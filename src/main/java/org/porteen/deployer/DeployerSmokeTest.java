package org.porteen.deployer;

public class DeployerSmokeTest {
    public static void main(String[] args) throws Exception {
        KubernetesDeployer deployer = new KubernetesDeployer("127.0.0.1.nip.io");

        DeployResult first = deployer.deploy(new DeploySpec(
                "demo3", "offline-file-server", "offline-file-server:dev", 8080, "/health"));
        System.out.println("First deploy: http://" + first.hostname() + "/health");

        DeployResult second = deployer.deploy(new DeploySpec(
                "demo3", "offline-file-server", "offline-file-server:v2", 8080, "/health"));
        System.out.println("Second deploy: http://" + second.hostname() + "/health");
    }
}
