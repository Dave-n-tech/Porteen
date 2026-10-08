package org.porteen.deployer;

public record DeploySpec(
        String tenant,      // who owns this app, e.g. "demo2"
        String appName,     // e.g. "offline-file-server"
        String image,       // e.g. "offline-file-server:dev"
        int port,           // port the container listens on
        String healthPath   // e.g. "/health" or "/actuator/health"
){}
