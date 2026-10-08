package org.porteen;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;

import java.util.List;
import java.util.Map;


public class CreateDeploymentTest {
    public static void main(String[] args) throws Exception {
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);

        AppsV1Api api = new AppsV1Api();

        // container spec
        V1Container container = new V1Container()
                .name("offline-file-server")
                .image("offline-file-server:dev")
                .imagePullPolicy("IfNotPresent")
                .ports(List.of(new V1ContainerPort().containerPort(8080)))
                .readinessProbe(new V1Probe()
                        .httpGet(new V1HTTPGetAction().path("/health").port(new IntOrString(8080)))
                        .initialDelaySeconds(3)
                        .periodSeconds(5))
                .livenessProbe(new V1Probe()
                        .httpGet(new V1HTTPGetAction().path("/health").port(new IntOrString(8080)))
                        .initialDelaySeconds(10)
                        .periodSeconds(10))
                .resources(new V1ResourceRequirements()
                        .requests(Map.of(
                                "cpu", new Quantity("100m"),
                                "memory", new Quantity("128Mi")))
                        .limits(Map.of(
                                "cpu", new Quantity("500m"),
                                "memory", new Quantity("256Mi"))));

        // pod template
        V1PodTemplateSpec template = new V1PodTemplateSpec()
                .metadata(new V1ObjectMeta().labels(Map.of("app", "offline-file-server")))
                .spec(new V1PodSpec().containers(List.of(container)));

        // deployment itself
        V1Deployment deployment = new V1Deployment()
                .metadata(new V1ObjectMeta().name("offline-file-server"))
                .spec(new V1DeploymentSpec()
                        .replicas(1)
                        .selector(new V1LabelSelector().matchLabels(Map.of("app", "offline-file-server")))
                        .template(template));

        V1Deployment result = api.createNamespacedDeployment("tenant-code-test", deployment).execute();

        System.out.println("Created deployment: " + result.getMetadata().getName());
    }
}
