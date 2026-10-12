package org.porteen.worker;

import org.porteen.deployer.KubernetesDeployer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "porteen.worker.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerConfig {

    @Bean
    KubernetesDeployer kubernetesDeployer(@Value("${porteen.base-domain}") String baseDomain)
            throws IOException {
        return new KubernetesDeployer(baseDomain);
    }
}