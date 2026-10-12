package org.porteen.api;

import org.porteen.deployer.DeployStatus;
import org.porteen.persistence.DeploymentRecord;

import java.time.Instant;
import java.util.UUID;

public record DeploymentResponse(
        UUID id,
        String tenant,
        String appName,
        String image,
        DeployStatus status,
        String namespace,
        String hostname,
        String url,
        String message,
        Instant createdAt,
        Instant updatedAt) {

    public static DeploymentResponse from(DeploymentRecord r) {
        String url = r.getHostname() == null ? null : "http://" + r.getHostname();
        return new DeploymentResponse(r.getId(), r.getTenant(), r.getAppName(), r.getImage(),
                r.getStatus(), r.getNamespace(), r.getHostname(), url,
                r.getMessage(), r.getCreatedAt(), r.getUpdatedAt());
    }
}