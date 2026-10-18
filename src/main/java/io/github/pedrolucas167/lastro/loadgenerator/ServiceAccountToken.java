package io.github.pedrolucas167.lastro.loadgenerator;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * O load generator chama a API por HTTP, como qualquer outro cliente, e por isso precisa
 * se autenticar. Usa a concessao client credentials com a service account do Keycloak.
 */
@Component
public class ServiceAccountToken {

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final ObjectMapper objectMapper;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    public ServiceAccountToken(ObjectMapper objectMapper,
                               @Value("${lastro.loadtest.token-uri}") String tokenUri,
                               @Value("${lastro.loadtest.client-id}") String clientId,
                               @Value("${lastro.loadtest.client-secret}") String clientSecret) {
        this.objectMapper = objectMapper;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String fetch() {
        String form = "grant_type=client_credentials"
            + "&client_id=" + encode(clientId)
            + "&client_secret=" + encode(clientSecret);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUri))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                    "Falha ao obter token M2M: HTTP " + response.statusCode() + " de " + tokenUri);
            }

            TokenResponse token = objectMapper.readValue(response.body(), TokenResponse.class);
            if (token.accessToken() == null) {
                throw new IllegalStateException("Resposta sem access_token de " + tokenUri);
            }
            return token.accessToken();
        } catch (IOException e) {
            throw new IllegalStateException(
                "Falha ao obter token M2M de " + tokenUri + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido ao obter token M2M de " + tokenUri, e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }
}
