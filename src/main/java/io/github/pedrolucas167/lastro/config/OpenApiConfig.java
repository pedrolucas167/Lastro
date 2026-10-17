package io.github.pedrolucas167.lastro.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String REPOSITORY_URL = "https://github.com/pedrolucas167/Lastro";
    private static final String APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0.txt";

    @Value("${lastro.version}")
    private String version;

    // Sem entrada "servers": o Swagger UI resolve a URL pela requisição, o que
    // mantém a especificação correta em qualquer ambiente (dev, container, proxy).
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
            + "livro de ofertas, posições e PnL.");
        info.setContact(contact);
        info.setLicense(license);

        return new OpenAPI().info(info);
    }
}
