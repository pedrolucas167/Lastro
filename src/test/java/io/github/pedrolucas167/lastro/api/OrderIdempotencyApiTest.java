package io.github.pedrolucas167.lastro.api;

import com.jayway.jsonpath.JsonPath;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.OrderSubmitted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O cabecalho `Idempotency-Key` ponta a ponta: o reenvio do cliente recebe a mesma ordem. */
@SpringBootTest(properties = "lastro.market.enabled=false")
@AutoConfigureMockMvc
class OrderIdempotencyApiTest {
    private static final String OPERATOR = "Bearer operador";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventStore eventStore;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private String accountId;

    @BeforeEach
    void setUp() throws Exception {
        given(jwtDecoder.decode("operador")).willReturn(jwtWithOperatorRole());
        accountId = ApiAccounts.funded(mockMvc, "100000.00");
    }

    @Test
    @DisplayName("Reenviar a ordem com a mesma chave devolve a ordem ja aceita")
    void resentOrderWithTheSameKeyReturnsTheAcceptedOrder() throws Exception {
        String key = "compra-" + UUID.randomUUID();

        String first = createOrder(key, orderBody());

        mockMvc.perform(post("/api/trading/orders")
                .header("Authorization", OPERATOR)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody()))
            .andExpect(status().isOk())
            .andExpect(result -> assertEquals(first,
                JsonPath.read(result.getResponse().getContentAsString(), "$.id")));

        assertEquals(1, journaledOrders(key));
    }

    @Test
    @DisplayName("Chave ja usada por outra ordem responde conflito, sem ordem nova")
    void keyAlreadyUsedByAnotherOrderIsAConflict() throws Exception {
        String key = "compra-" + UUID.randomUUID();
        createOrder(key, orderBody());

        mockMvc.perform(post("/api/trading/orders")
                .header("Authorization", OPERATOR)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody().replace("PETR4", "VALE3")))
            .andExpect(status().isConflict());

        assertEquals(1, journaledOrders(key));
    }

    // 30.50 esta dentro da banda de preco do PETR4 (referencia 30.50).
    private String orderBody() {
        return "{\"symbol\": \"PETR4\", \"side\": \"BUY\", \"type\": \"LIMIT\", \"price\": 30.50, "
            + "\"quantity\": 100, \"accountId\": \"" + accountId + "\"}";
    }

    private String createOrder(String idempotencyKey, String body) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/trading/orders")
                .header("Authorization", OPERATOR)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private long journaledOrders(String idempotencyKey) {
        return eventStore.readAll().stream()
            .filter(OrderSubmitted.class::isInstance)
            .map(OrderSubmitted.class::cast)
            .filter(event -> idempotencyKey.equals(event.idempotencyKey()))
            .count();
    }

    private Jwt jwtWithOperatorRole() {
        return Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("operador")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("realm_access", Map.of("roles", List.of("lastro-operator")))
            .build();
    }
}
