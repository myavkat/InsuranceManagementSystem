package com.insurancemanagementsystem.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Auto-configuration that registers JPA repositories from the common module.
 * <p>
 * Runs <em>after</em> {@link JpaRepositoriesAutoConfiguration} so the default
 * repository scanning (driven by {@code @SpringBootApplication}'s base package)
 * completes first. This configuration only adds the common repository package
 * without interfering with the default scan.
 * <p>
 * Entity scanning for the common entity package is declared via
 * {@code @EntityScan} on each service's application class — that annotation is
 * safe in {@code @WebMvcTest} slices because it is passive and does not force
 * JPA infrastructure creation.
 */
@AutoConfiguration(after = DataJpaRepositoriesAutoConfiguration.class)
@EnableJpaRepositories(basePackages = "com.insurancemanagementsystem.common.repository")
public class CommonPersistenceAutoConfiguration {
}
