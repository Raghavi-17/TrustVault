package com.trustvault.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI trustVaultOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TrustVault REST API")
                        .description("TrustVault — Secure Document Sharing with a Tamper-Evident Audit Trail. " +
                                     "RESTful API providing session-based authentication, RBAC, fine-grained document-level " +
                                     "authorization, private file storage, and cryptographic SHA-256 hash-chain verification.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("TrustVault Security Team")
                                .email("security@trustvault.local"))
                        .license(new License().name("Apache 2.0")));
    }
}
