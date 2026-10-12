package org.porteen.deployer;

import com.google.protobuf.Api;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KubernetesDeployer {

    private final CoreV1Api coreApi;
    private final AppsV1Api appsApi;
    private final NetworkingV1Api networkingApi;
    private final String baseDomain;

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private static final Set<String> FATAL_WAITING_REASONS = Set.of(
            "ErrImagePull", "ImagePullBackOff", "InvalidImageName",
            "CrashLoopBackOff", "CreateContainerConfigError");

    public KubernetesDeployer(String baseDomain) throws IOException {
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);
        this.coreApi = new CoreV1Api();
        this.appsApi = new AppsV1Api();
        this.networkingApi = new NetworkingV1Api();
        this.baseDomain = baseDomain; // e.g. "127.0.0.1.nip.io"
    }

    @FunctionalInterface
    private interface ApiCall {
        void run() throws ApiException;
    }

    private record RolloutOutcome(boolean ok, String message) {}

    private RolloutOutcome awaitRollout(String namespace, String name, Duration timeout)
            throws ApiException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            V1Deployment d = appsApi.readNamespacedDeployment(name, namespace).execute();

            if(isRolloutComplete(d)) {
                return new RolloutOutcome(true, "Rollout complete");
            }

            String podProblem = findPodProblem(namespace, name);
            if (podProblem != null) {
                return new RolloutOutcome(false, podProblem);
            }

            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        return new RolloutOutcome(false, "timed out after " + timeout.toSeconds() + "s waiting for rollout");
    }

    private boolean isRolloutComplete(V1Deployment d) {
        V1DeploymentStatus s = d.getStatus();
        if (s == null) return false;

        long generation = d.getMetadata().getGeneration();
        long observed = s.getObservedGeneration() == null ? 0 : s.getObservedGeneration();
        int desired = d.getSpec().getReplicas();
        int updated = orZero(s.getUpdatedReplicas());
        int total = orZero(s.getReplicas());
        int available = orZero(s.getAvailableReplicas());

        return observed >= generation   // the controller has seen our latest change
                && updated == desired   // all pods run the new spec
                && total == updated     // no old pods left
                && available == updated; // all are passing readiness
    }

    private String findPodProblem(String namespace, String appName) throws ApiException {
        V1PodList pods = coreApi.listNamespacedPod(namespace)
                .labelSelector("app=" + appName)
                .execute();

        for (V1Pod pod : pods.getItems()) {
            if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) continue;
            for (V1ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                V1ContainerStateWaiting waiting =
                        cs.getState() == null ? null : cs.getState().getWaiting();
                if (waiting != null && FATAL_WAITING_REASONS.contains(waiting.getReason())) {
                    return pod.getMetadata().getName() + ": " + waiting.getReason()
                            + (waiting.getMessage() == null ? "" : " - " + waiting.getMessage());
                }
            }
        }
        return null;
    }

    private static int orZero(Integer v) {
        return v == null ? 0 : v;
    }

    public DeployResult deploy(DeploySpec spec) throws ApiException, InterruptedException{
        return deploy(spec, Duration.ofSeconds(120));
    }

    public DeployResult deploy(DeploySpec spec, Duration timeout) throws ApiException, InterruptedException {
        String namespace = "tenant-" + spec.tenant();
        String hostname = generateHostname(spec.tenant(), spec.appName());
        Map<String, String> labels = Map.of("app", spec.appName());

        ensureNamespace(namespace);
        createDeployment(namespace, spec, labels);
        createService(namespace, spec, labels);
        createIngress(namespace, spec, hostname);

        RolloutOutcome outcome = awaitRollout(namespace, spec.appName(), timeout);
        return new DeployResult(namespace, hostname,
                outcome.ok() ? DeployStatus.LIVE : DeployStatus.FAILED, outcome.message());
    }

    private void ensureNamespace(String name) throws ApiException {
        V1Namespace ns = new V1Namespace().metadata(new V1ObjectMeta().name(name));
        try {
            coreApi.createNamespace(ns).execute();
        } catch (ApiException e) {
            if (e.getCode() != 409) throw e; // 409 = already exists, which is fine here
        }
    }

    private void createDeployment(String namespace, DeploySpec spec, Map<String, String> labels) throws ApiException {
        V1Probe readiness = probe(spec, 5, 5);
        V1Probe liveness = probe(spec, 15, 10);

        V1Container container = new V1Container()
                .name(spec.appName())
                .image(spec.image())
                .imagePullPolicy("IfNotPresent")
                .ports(List.of(new V1ContainerPort().containerPort(spec.port())))
                .readinessProbe(readiness)
                .livenessProbe(liveness)
                .resources(new V1ResourceRequirements()
                        .requests(Map.of("cpu", new Quantity("100m"), "memory", new Quantity("128Mi")))
                        .limits(Map.of("cpu", new Quantity("500m"), "memory", new Quantity("256Mi"))));

        V1Deployment deployment = new V1Deployment()
                .metadata(new V1ObjectMeta().name(spec.appName()).labels(labels))
                .spec(new V1DeploymentSpec()
                        .replicas(1)
                        .selector(new V1LabelSelector().matchLabels(labels))
                        .template(new V1PodTemplateSpec()
                                .metadata(new V1ObjectMeta().labels(labels))
                                .spec(new V1PodSpec().containers(List.of(container)))));

        String name = spec.appName();
        upsert(
                () -> appsApi.createNamespacedDeployment(namespace, deployment).execute(),
                () -> appsApi.replaceNamespacedDeployment(name, namespace, deployment).execute()
        );
    }

    private V1Probe probe(DeploySpec spec, int initialDelay, int period) {
        return new V1Probe()
                .httpGet(new V1HTTPGetAction()
                        .path(spec.healthPath())
                        .port(new IntOrString(spec.port())))
                .initialDelaySeconds(initialDelay)
                .periodSeconds(period);
    }

    private void createService(String namespace, DeploySpec spec, Map<String, String> labels)
            throws ApiException {
        V1Service service = new V1Service()
                .metadata(new V1ObjectMeta().name(spec.appName()))
                .spec(new V1ServiceSpec()
                        .selector(labels)
                        .type("ClusterIP")
                        .ports(List.of(new V1ServicePort()
                                .port(80)
                                .targetPort(new IntOrString(spec.port())))));

        String name = spec.appName();
        upsert(
                () -> coreApi.createNamespacedService(namespace, service).execute(),
                () -> {
                    V1Service existing = coreApi.readNamespacedService(name, namespace).execute();
                    service.getSpec()
                            .clusterIP(existing.getSpec().getClusterIP())
                            .clusterIPs(existing.getSpec().getClusterIPs());
                    service.getMetadata().resourceVersion(existing.getMetadata().getResourceVersion());
                    coreApi.replaceNamespacedService(name, namespace, service).execute();
                }
        );
    }

    private void createIngress(String namespace, DeploySpec spec, String hostname)
            throws ApiException {
        V1HTTPIngressPath path = new V1HTTPIngressPath()
                .path("/")
                .pathType("Prefix")
                .backend(new V1IngressBackend()
                        .service(new V1IngressServiceBackend()
                                .name(spec.appName())
                                .port(new V1ServiceBackendPort().number(80))));

        V1Ingress ingress = new V1Ingress()
                .metadata(new V1ObjectMeta().name(spec.appName()))
                .spec(new V1IngressSpec()
                        .ingressClassName("nginx")
                        .rules(List.of(new V1IngressRule()
                                .host(hostname)
                                .http(new V1HTTPIngressRuleValue().paths(List.of(path))))));

        String name = spec.appName();
        upsert(
                () -> networkingApi.createNamespacedIngress(namespace, ingress).execute(),
                () -> networkingApi.replaceNamespacedIngress(name, namespace, ingress).execute()
        );
    }

    private String generateHostname(String tenant, String appName) {
        return appName + "." + tenant + "." + baseDomain;
    }

    private void upsert(ApiCall create, ApiCall replace) throws ApiException {
        try {
            create.run();
        } catch (ApiException e) {
            if (e.getCode() != 409) throw e;
            replace.run();
        }
    }
}
