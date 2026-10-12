package org.porteen.worker;

import org.porteen.deployer.DeployResult;
import org.porteen.deployer.DeploySpec;
import org.porteen.deployer.DeployStatus;
import org.porteen.deployer.KubernetesDeployer;
import org.porteen.persistence.DeploymentRecord;
import org.porteen.persistence.DeploymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "porteen.worker.enabled", havingValue = "true", matchIfMissing = true)
public class DeploymentWorker {

    private static final Logger log = LoggerFactory.getLogger(DeploymentWorker.class);

    private final DeploymentRepository repo;
    private final KubernetesDeployer deployer;
    private final TransactionTemplate transaction;

    public DeploymentWorker(DeploymentRepository repo, KubernetesDeployer deployer, PlatformTransactionManager transactionManager) {
        this.repo = repo;
        this.deployer = deployer;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    void requeueInterrupted() {
        Integer n = transaction.execute(s -> repo.requeueInterrupted());

        if (n != null && n > 0) log.warn("Requeued {} deployment(s) interrupted by a restart", n);
    }

    @Scheduled(fixedDelayString = "${porteen.worker.poll-ms}")
    public void poll() {
        DeploymentRecord job = claim();
        if (job != null) run(job);
    }

    private DeploymentRecord claim() {
        return transaction.execute(s -> repo.findFirstByStatusOrderByCreatedAtAsc(DeployStatus.PENDING)
                .map(r -> { r.markDeploying(); return repo.save(r); })
                .orElse(null));
    }

    private void run(DeploymentRecord job) {
        log.info("Deploying {}/{} ({})", job.getTenant(), job.getAppName(), job.getImage());

        try {
            DeployResult r = deployer.deploy(new DeploySpec(job.getTenant(), job.getAppName(), job.getImage(), job.getPort(), job.getHealthPath()));
            job.markFinished(r.status(), r.namespace(), r.hostname(), r.message());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while deploying {}; it stays in Deploying and is re-queued on restart", job.getId());
            return;
        } catch (Exception e) {
            log.error("Deploy {} failed", job.getId());
            job.markFinished(DeployStatus.FAILED, null, null, truncate("deploy error: " + e.getMessage()));
        }
        repo.save(job);
        log.info("Deployment {} finished: {}", job.getId(), job.getStatus());
    }

    private static String truncate(String s) {
        return s == null ? null : s.length() <= 1000 ? s : s.substring(0, 1000);
    }

}
