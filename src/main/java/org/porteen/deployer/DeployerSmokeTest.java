package org.porteen.deployer;

public class DeployerSmokeTest {
    public static void main(String[] args) throws Exception {
        String image = args[0];
        String healthPath = args.length > 1 ? args[1] : "/health";

        KubernetesDeployer deployer = new KubernetesDeployer("127.0.0.1.nip.io");
        DeployResult result = deployer.deploy(new DeploySpec(
                "demo3", "offline-file-server", image, 8080, healthPath));

        System.out.println("Status: " + result.status());
        System.out.println("Message: " + result.message());
        System.out.println("URL:    http://" + result.hostname() + "/health");
    }
}
