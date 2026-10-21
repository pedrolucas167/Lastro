package io.github.pedrolucas167.lastro.api;

import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O extrato e o balanco vistos pela API: o titular le o proprio extrato (e so o proprio), e o
 * balanco inteiro e ato de operador. Os numeros sao conferidos como decimal, sem passar por double.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LedgerApiTest {
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));
    private static final String HOLDER_ONE = "Bearer cliente-extrato";
    private static final String HOLDER_TWO = "Bearer cliente-vizinho";
    private static final String OPERATOR = "Bearer operador";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode("cliente-extrato")).willReturn(jwt("cliente-extrato", "lastro-holder"));
        given(jwtDecoder.decode("cliente-vizinho")).willReturn(jwt("cliente-vizinho", "lastro-holder"));
        given(jwtDecoder.decode("operador")).willReturn(jwt("operador-1", "lastro-operator"));
    }

    @Test
    @DisplayName("O extrato mostra as partidas da conta, com contraparte e saldo depois de cada uma")
    void statementShowsThePostings() throws Exception {
        String accountId = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-extrato").getId();
        accountService.credit(BACK_OFFICE, accountId, new BigDecimal("50.00"));
        accountService.debit(BACK_OFFICE, accountId, new BigDecimal("30.00"));

        String body = mockMvc.perform(get("/api/accounts/{id}/statement", accountId)
                .header("Authorization", HOLDER_ONE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.lines", hasSize(3)))
            .andExpect(jsonPath("$.lines[0].reason").value("OPENING"))
            .andExpect(jsonPath("$.lines[1].reason").value("DEPOSIT"))
            .andExpect(jsonPath("$.lines[1].counterparty").value("ASSETS:SETTLEMENT"))
            .andExpect(jsonPath("$.lines[2].reason").value("WITHDRAWAL"))
            .andExpect(jsonPath("$.lines[2].side").value("DEBIT"))
            .andReturn().getResponse().getContentAsString();

        JsonNode statement = objectMapper.readTree(body);
        assertEquals(0, new BigDecimal("120.00")
            .compareTo(decimal(statement.get("balance"))));
        assertEquals(0, new BigDecimal("100.00")
            .compareTo(decimal(statement.get("lines").get(0).get("balanceAfter"))));
        assertEquals(0, new BigDecimal("30.00")
            .compareTo(decimal(statement.get("lines").get(2).get("amount"))));
        assertEquals(0, new BigDecimal("120.00")
            .compareTo(decimal(statement.get("lines").get(2).get("balanceAfter"))));
    }

    @Test
    @DisplayName("O extrato de outro titular e barrado e o de conta inexistente nao existe")
    void statementIsOnlyForTheHolder() throws Exception {
        String mine = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-extrato").getId();

        mockMvc.perform(get("/api/accounts/{id}/statement", mine).header("Authorization", HOLDER_TWO))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/accounts/{id}/statement", "conta-que-nao-existe")
                .header("Authorization", HOLDER_ONE))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("O balanco do livro fecha e e ato de operador")
    void trialBalanceBalancesAndIsOperatorOnly() throws Exception {
        String accountId = accountService.createAccount(BACK_OFFICE, new BigDecimal("10.00"), "cliente-extrato").getId();
        accountService.credit(BACK_OFFICE, accountId, new BigDecimal("5.00"));

        mockMvc.perform(get("/api/ledger/trial-balance").header("Authorization", HOLDER_ONE))
            .andExpect(status().isForbidden());

        String body = mockMvc.perform(get("/api/ledger/trial-balance").header("Authorization", OPERATOR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balanced").value(true))
            .andReturn().getResponse().getContentAsString();

        JsonNode trialBalance = objectMapper.readTree(body);
        assertEquals(0, decimal(trialBalance.get("totalDebits"))
            .compareTo(decimal(trialBalance.get("totalCredits"))));
        assertEquals(0, decimal(trialBalance.get("clientLiabilities"))
            .compareTo(decimal(trialBalance.get("settlement"))));

        JsonNode settlement = null;
        for (JsonNode account : trialBalance.get("accounts")) {
            if ("ASSETS:SETTLEMENT".equals(account.get("code").asString())) {
                settlement = account;
            }
        }
        // O caixa na liquidante responde exatamente pelo que ela deve aos clientes.
        assertEquals(0, decimal(settlement.get("balance"))
            .compareTo(decimal(trialBalance.get("clientLiabilities"))));
    }

    private static BigDecimal decimal(JsonNode node) {
        return new BigDecimal(node.asString());
    }

    private Jwt jwt(String subject, String... roles) {
        return Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(subject)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("realm_access", Map.of("roles", List.of(roles)))
            .build();
    }
}
