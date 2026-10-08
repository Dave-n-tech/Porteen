package org.porteen;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Config;

public class CreateNamespaceTest {
    public static void main(String[] args) throws Exception {
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);

        CoreV1Api api = new CoreV1Api();

        V1Namespace namespace = new V1Namespace()
                .metadata(new V1ObjectMeta().name("tenant-code-test"));

        V1Namespace result = api.createNamespace(namespace).execute();

        System.out.println("Created namespace: " + result.getMetadata().getName());
    }
}
