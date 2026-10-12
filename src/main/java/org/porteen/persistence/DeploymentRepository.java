package org.porteen.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.porteen.deployer.DeployStatus;
import org.springframework.data.jpa.repository.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeploymentRepository extends JpaRepository<DeploymentRecord, UUID> {

    List<DeploymentRecord> findByTenantAndAppNameOrderByCreatedAtDesc(String tenant, String appName);

    Optional<DeploymentRecord> findFirstByTenantAndAppNameAndStatusOrderByCreatedAtDesc(
            String tenant, String appName, DeployStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    Optional<DeploymentRecord> findFirstByStatusOrderByCreatedAtAsc(DeployStatus status);

    @Modifying
    @Query("update DeploymentRecord d set d.status = org.porteen.deployer.DeployStatus.PENDING "
            + "where d.status = org.porteen.deployer.DeployStatus.DEPLOYING")
    int requeueInterrupted();
}
