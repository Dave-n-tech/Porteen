package org.porteen.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import org.porteen.deployer.DeployStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "deployments")
public class DeploymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String tenant;

    @Column(nullable = false)
    private String appName;

    @Column(nullable = false)
    private String image;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private String healthPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeployStatus status = DeployStatus.PENDING;

    private String namespace;
    private String hostname;

    @Column(length = 1000)
    private String message;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected DeploymentRecord() {}

    public DeploymentRecord(String tenant, String appName, String image, int port, String healthPath) {
        this.tenant = tenant;
        this.appName = appName;
        this.image = image;
        this.port = port;
        this.healthPath = healthPath;
    }

    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public void markDeploying() {
        this.status = DeployStatus.DEPLOYING;
    }

    public void markFinished(DeployStatus status, String namespace, String hostname, String message) {
        this.status = status;
        this.namespace = namespace;
        this.hostname = hostname;
        this.message = message;
    }

}
