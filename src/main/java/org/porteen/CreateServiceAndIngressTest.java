package org.porteen;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;

import java.util.List;
import java.util.Map;

public class CreateServiceAndIngressTest {
    public static void main(String[] args) throws Exception {
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);

        String namespace = "tenant-code-test";
        String appName = "offline-file-server";

        // Service
        CoreV1Api coreApi = new CoreV1Api();

        V1Service service = new V1Service()
                .metadata(new V1ObjectMeta().name(appName))
                .spec(new V1ServiceSpec()
                        .selector(Map.of("app", appName))
                        .type("ClusterIP")
                        .ports(List.of(new V1ServicePort()
                                .port(80)
                                .targetPort(new IntOrString(8080)))));

        V1Service createdService = coreApi.createNamespacedService(namespace, service).execute();
        System.out.println("Created service: " + createdService.getMetadata().getName());

        // Ingress
        NetworkingV1Api networkingApi = new NetworkingV1Api();

        V1HTTPIngressPath path = new V1HTTPIngressPath()
                .path("/")
                .pathType("Prefix")
                .backend(new V1IngressBackend()
                        .service(new V1IngressServiceBackend()
                                .name(appName)
                                .port(new V1ServiceBackendPort().number(80))));

        V1Ingress ingress = new V1Ingress()
                .metadata(new V1ObjectMeta()
                        .name(appName)
                        .annotations(Map.of("nginx.ingress.kubernetes.io/rewrite-target", "/")))
                .spec(new V1IngressSpec()
                        .ingressClassName("nginx")
                        .rules(List.of(new V1IngressRule()
                                .host("code-test." + appName + ".127.0.0.1.nip.io")
                                .http(new V1HTTPIngressRuleValue().paths(List.of(path))))));

        V1Ingress createdIngress = networkingApi.createNamespacedIngress(namespace, ingress).execute();
        System.out.println("Created ingress: " + createdIngress.getMetadata().getName());
    }
}