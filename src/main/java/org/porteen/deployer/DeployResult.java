package org.porteen.deployer;

public record DeployResult (
        String namespace,
        String hostname,
        DeployStatus status,
        String message
){}
