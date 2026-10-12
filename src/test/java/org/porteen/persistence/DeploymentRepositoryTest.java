package org.porteen.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.porteen.deployer.DeployStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DeploymentRepositoryTest {

    @Autowired DeploymentRepository repo;
    @Value("${spring.datasource.url}") String dbUrl;

    @BeforeEach
    void guardAndClean() {
        assertTrue(dbUrl.endsWith("porteen_test"), "Tests must never run against " + dbUrl);
        repo.deleteAll();
    }
    @Test
    void savesAndReadsBack() {
        DeploymentRecord saved = repo.save(
                new DeploymentRecord("persist-test", "demo-app", "node-app:v2", 8080, "/health"));

        assertNotNull(saved.getId());
        assertEquals(DeployStatus.PENDING, saved.getStatus());

        saved.markFinished(DeployStatus.LIVE, "tenant-persist-test",
                "demo-app.persist-test.127.0.0.1.nip.io", "rollout complete");
        repo.save(saved);

        DeploymentRecord loaded = repo.findById(saved.getId()).orElseThrow();
        assertEquals(DeployStatus.LIVE, loaded.getStatus());
        assertEquals("rollout complete", loaded.getMessage());
    }
}