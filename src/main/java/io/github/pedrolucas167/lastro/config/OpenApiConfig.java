package io.github.pedrolucas167.lastro.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String REPOSITORY_URL = "https://github.com/pedrolucas167/Lastro";
    private static final String APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0.txt";
    private static final String BEARER_JWT = "bearer-jwt";

    @Value("${lastro.version}")
    private String version;

    // Sem servidor fixo no codigo: o springdoc deriva a URL da requisicao, o que
    // mantem a especificacao correta em qualquer ambiente (dev, container, proxy).
    @Bean
    public OpenAPI lastroOpenAPI() {
        Contact contact = new Contact();
        contact.setName("Lastro");
        contact.setUrl(REPOSITORY_URL);

        License license = new License();
        license.setName("Apache License 2.0");
        license.setUrl(APACHE_LICENSE_URL);

        Info info = new Info();
        info.setTitle("Lastro API");
        info.setVersion(version);
        info.setDescription("Plataforma de negociação: contas, transferências, matching de ordens, "
            + "livro de ofertas, posições e PnL. Todas as rotas exigem um JWT do Keycloak; "
            + "apenas GET /actuator/health é público.");
        info.setContact(contact);
        info.setLicense(license);

        // Declarado globalmente: a API inteira exige o token, exceto o health check.
        SecurityScheme bearer = new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("JWT emitido pelo Keycloak (realm lastro)");

        return new OpenAPI()
            .info(info)
            .components(new Components().addSecuritySchemes(BEARER_JWT, bearer))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
    }
}
