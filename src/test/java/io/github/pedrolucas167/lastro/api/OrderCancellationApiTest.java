package io.github.pedrolucas167.lastro.api;

import com.jayway.jsonpath.JsonPath;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.OrderCancelled;
import io.github.pedrolucas167.lastro.journal.OrderReplaced;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cancelar e substituir ponta a ponta: o journal so recebe o fato quando o comando aconteceu.
 *
 * Cada teste usa um simbolo proprio porque o contexto do Spring e o book sao compartilhados entre
 * eles (e o halt de um teste nao pode contaminar o outro).
 */
@SpringBootTest(properties = "lastro.market.enabled=false")
@AutoConfigureMockMvc
class OrderCancellationApiTest {
    private static final String OPERATOR = "Bearer operador";
    private static final String UNKNOWN_SYMBOL = "BBDC4";
    private static final String RETRY_SYMBOL = "WEGE3";
    private static final String FILLED_SYMBOL = "ABEV3";
    private static final String AMEND_SYMBOL = "BBAS3";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventStore eventStore;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode("operador")).willReturn(jwtWithOperatorRole());
    }

    @Test
    @DisplayName("Cancelar ordem que nao existe responde 404 e nao deixa fato no journal")
    void cancellingAnUnknownOrderLeavesNoFact() throws Exception {
        long before = journaled(OrderCancelled.class);

        cancel("ordem-inexistente-" + UUID.randomUUID(), UNKNOWN_SYMBOL)
            .andExpect(status().isNotFound());

        assertEquals(before, journaled(OrderCancelled.class));
    }

    @Test
    @DisplayName("Cancelar duas vezes responde 200 nas duas e grava um fato so")
    void cancellingTwiceIsOkAndWritesOneFact() throws Exception {
        String orderId = submit(RETRY_SYMBOL, "BUY", "30.50", 100);

        cancel(orderId, RETRY_SYMBOL).andExpect(status().isOk());
        cancel(orderId, RETRY_SYMBOL).andExpect(status().isOk());

        assertEquals(1, cancellationsOf(orderId));
    }

    @Test
    @DisplayName("Cancelar ordem preenchida responde conflito e nao grava fato")
    void cancellingAFilledOrderIsAConflict() throws Exception {
        String buyId = submit(FILLED_SYMBOL, "BUY", "30.50", 100);
        submit(FILLED_SYMBOL, "SELL", "30.50", 100);

        cancel(buyId, FILLED_SYMBOL).andExpect(status().isConflict());

        assertEquals(0, cancellationsOf(buyId));
    }

    @Test
    @DisplayName("Amend recusado responde 404 e nao deixa OrderReplaced")
    void refusedAmendLeavesNoFact() throws Exception {
        String buyId = submit(AMEND_SYMBOL, "BUY", "30.50", 100);
        submit(AMEND_SYMBOL, "SELL", "30.50", 40);

        mockMvc.perform(post("/api/trading/orders/" + buyId + "/replace")
                .header("Authorization", OPERATOR)
                .param("symbol", AMEND_SYMBOL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\": 30.50, \"quantity\": 20}"))
            .andExpect(status().isNotFound());

        assertEquals(0, replacementsOf(buyId));
    }

    private ResultActions cancel(String orderId, String symbol) throws Exception {
        return mockMvc.perform(post("/api/trading/orders/" + orderId + "/cancel")
            .header("Authorization", OPERATOR)
            .param("symbol", symbol));
    }

    private String submit(String symbol, String side, String price, int quantity) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/trading/orders")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\": \"%s\", \"side\": \"%s\", \"type\": \"LIMIT\", \"price\": %s, "
                    .formatted(symbol, side, price)
                    + "\"quantity\": " + quantity + ", \"accountId\": \"acc-cancelamento\"}"))
            .andExpect(status().isOk())
            .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private long journaled(Class<?> type) {
        return eventStore.readAll().stream().filter(type::isInstance).count();
    }

    private long cancellationsOf(String orderId) {
        return eventStore.readAll().stream()
            .filter(OrderCancelled.class::isInstance)
            .map(OrderCancelled.class::cast)
            .filter(event -> orderId.equals(event.orderId()))
            .count();
    }

    private long replacementsOf(String orderId) {
        return eventStore.readAll().stream()
            .filter(OrderReplaced.class::isInstance)
            .map(OrderReplaced.class::cast)
            .filter(event -> orderId.equals(event.orderId()))
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
