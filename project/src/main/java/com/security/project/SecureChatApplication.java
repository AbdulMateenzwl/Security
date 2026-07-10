package com.security.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SecureChat — application entry point.
 *
 * <p>SecureChat is an end-to-end encrypted messaging and collaboration backend. The server acts as
 * a blind relay: it stores and forwards Signal-protocol ciphertext only and never has access to
 * message plaintext.</p>
 *
 * <ul>
 *   <li>{@link EnableJpaAuditing} — populates {@code @CreatedDate}/{@code @LastModifiedDate} audit
 *       fields on entities (e.g. {@code createdAt}, {@code updatedAt}).</li>
 *   <li>{@link EnableScheduling} — enables the cron-driven cleanup of expired (disappearing)
 *       messages.</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan            // binds @ConfigurationProperties records under this package
@EnableJpaAuditing
@EnableScheduling
public class SecureChatApplication {

	public static void main(String[] args) {
		SpringApplication.run(SecureChatApplication.class, args);
	}

}
