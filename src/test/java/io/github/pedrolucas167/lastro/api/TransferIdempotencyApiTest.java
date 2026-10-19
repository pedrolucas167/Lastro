package io.github.pedrolucas167.lastro.api;

import com.jayway.jsonpath.JsonPath;
import io.github.pedrolucas167.lastro.domain.Account;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.TransferCompleted;
import io.github.pedrolucas167.lastro.service.AccountService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O cabecalho `Idempotency-Key` na transferencia, ponta a ponta. */
@SpringBootTest(properties = "lastro.market.enabled=false")
@AutoConfigureMockMvc
class TransferIdempotencyApiTest {
    private static final String OPERATOR = "Bearer operador";
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private EventStore eventStore;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode("operador")).willReturn(jwtWithOperatorRole());
    }

    @Test
    @DisplayName("Reenviar a transferencia com a mesma chave devolve a que ja foi feita")
    void resentTransferWithTheSameKeyReturnsTheAcceptedOne() throws Exception {
        Account from = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"));
        Account to = accountService.createAccount(BACK_OFFICE, new BigDecimal("0.00"));
        String key = "transferencia-" + UUID.randomUUID();

        String first = transfer(key, body(from, to));

        mockMvc.perform(post("/api/transactions/transfer")
                .header("Authorization", OPERATOR)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(from, to)))
            .andExpect(status().isOk())
            .andExpect(result -> assertEquals(first,
                JsonPath.read(result.getResponse().getContentAsString(), "$.id")));

        assertEquals(0, new BigDecimal("50.00").compareTo(accountService.getAccount(from.getId()).getBalance()));
        assertEquals(1, journaledTransfers(key));
    }

    @Test
    @DisplayName("A mesma chave para outra transferencia responde conflito")
    void keyAlreadyUsedByAnotherTransferIsAConflict() throws Exception {
        Account from = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"));
        Account to = accountService.createAccount(BACK_OFFICE, new BigDecimal("0.00"));
        Account other = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"));
        String key = "transferencia-" + UUID.randomUUID();

        transfer(key, body(from, to));

        mockMvc.perform(post("/api/transactions/transfer")
                .header("Authorization", OPERATOR)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(from, other)))
            .andExpect(status().isConflict());

        assertEquals(1, journaledTransfers(key));
    }

    @Test
    @DisplayName("A chave no corpo e recusada: quem manda por engano nao fica sem garantia")
    void idempotencyKeyInTheBodyIsRefused() throws Exception {
        Account from = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"));
        Account to = accountService.createAccount(BACK_OFFICE, new BigDecimal("0.00"));

        mockMvc.perform(post("/api/transactions/transfer")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccountId\": \"%s\", \"toAccountId\": \"%s\", \"amount\": 50.00, "
                    .formatted(from.getId(), to.getId()) + "\"idempotencyKey\": \"chave-antiga\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.idempotencyKey").value(containsString("Idempotency-Key header")));

        assertEquals(0, new BigDecimal("100.00").compareTo(accountService.getAccount(from.getId()).getBalance()));
    }

    private String body(Account from, Account to) {
        return "{\"fromAccountId\": \"%s\", \"toAccountId\": \"%s\", \"amount\": 50.00}"
            .formatted(from.getId(), to.getId());
    }

    private String transfer(String idempotencyKey, String body) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/transactions/transfer")
                .header("Authorization", OPERATOR)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private long journaledTransfers(String idempotencyKey) {
        return eventStore.readAll().stream()
            .filter(TransferCompleted.class::isInstance)
            .map(TransferCompleted.class::cast)
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
