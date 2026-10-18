package io.github.pedrolucas167.lastro.config;

import io.github.pedrolucas167.lastro.domain.Actor;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A matriz de autorizacao: o cliente M2M faz apenas o que o load generator precisa
 * (abrir conta e transferir), e todo o resto -- saldo, ordens, carga, halt -- e ato de
 * operador.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiAuthorizationTest {
    private static final String SERVICE = "Bearer servico";
    private static final String OPERATOR = "Bearer operador";
    private static final String HOLDER = "Bearer cliente";
    private static final String NO_ROLE = "Bearer sem-papel";
    // Quem cria as contas nos preparos e o back office: a matriz testa as rotas, nao o vinculo.
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode("servico")).willReturn(jwtWithRoles("lastro-service"));
        given(jwtDecoder.decode("operador")).willReturn(jwtWithRoles("lastro-operator"));
        given(jwtDecoder.decode("cliente")).willReturn(jwt("cliente-1", "lastro-holder"));
        given(jwtDecoder.decode("sem-papel")).willReturn(jwtWithRoles());
    }

    @Test
    @DisplayName("O M2M abre conta e transfere: é o que o load generator faz")
    void serviceAccountCanCreateAccountAndTransfer() throws Exception {
        mockMvc.perform(post("/api/accounts")
                .header("Authorization", SERVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"initialBalance\": 1000.00}"))
            .andExpect(status().isOk());

        String from = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00")).getId();
        String to = accountService.createAccount(BACK_OFFICE, new BigDecimal("0.00")).getId();

        mockMvc.perform(post("/api/transactions/transfer")
                .header("Authorization", SERVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccountId\": \"%s\", \"toAccountId\": \"%s\", \"amount\": 50.00}"
                    .formatted(from, to)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("O M2M não movimenta saldo nem opera ordens")
    void serviceAccountCannotTouchMoneyOrOrders() throws Exception {
        String accountId = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00")).getId();

        mockMvc.perform(post("/api/accounts/" + accountId + "/credit")
                .header("Authorization", SERVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 1000.00}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/trading/orders")
                .header("Authorization", SERVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\": \"PETR4\", \"side\": \"BUY\", \"type\": \"LIMIT\", "
                    + "\"price\": 30.50, \"quantity\": 100, \"accountId\": \"" + accountId + "\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/trading/trades").header("Authorization", SERVICE))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/accounts/" + accountId).header("Authorization", SERVICE))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("O M2M não dispara carga nem aciona halt")
    void serviceAccountCannotRunLoadTestOrHalt() throws Exception {
        mockMvc.perform(post("/api/loadtest/run").header("Authorization", SERVICE))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/market/PETR4/halt").header("Authorization", SERVICE))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("O operador opera ordens, saldo e mercado")
    void operatorCanOperate() throws Exception {
        String accountId = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00")).getId();

        mockMvc.perform(get("/api/trading/trades").header("Authorization", OPERATOR))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/accounts/" + accountId + "/credit")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 1000.00}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/accounts")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"initialBalance\": 100.00}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/market/halted").header("Authorization", OPERATOR))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("O titular abre a própria conta e lê as que tem")
    void holderOpensAndReadsOwnAccounts() throws Exception {
        mockMvc.perform(post("/api/accounts")
                .header("Authorization", HOLDER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"initialBalance\": 100.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.holder").value("cliente-1"));

        mockMvc.perform(get("/api/accounts/mine").header("Authorization", HOLDER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].holder").value("cliente-1"));
    }

    @Test
    @DisplayName("O titular não movimenta saldo, não define titularidade nem opera ordens")
    void holderCannotTouchMoneyOrOrders() throws Exception {
        String accountId = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-1").getId();

        mockMvc.perform(post("/api/accounts/" + accountId + "/credit")
                .header("Authorization", HOLDER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 1000.00}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/accounts/" + accountId + "/holder")
                .header("Authorization", HOLDER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holder\": \"cliente-1\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/trading/orders")
                .header("Authorization", HOLDER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\": \"PETR4\", \"side\": \"BUY\", \"type\": \"LIMIT\", "
                    + "\"price\": 30.50, \"quantity\": 100, \"accountId\": \"" + accountId + "\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/loadtest/run").header("Authorization", HOLDER))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Token válido sem papel do realm não passa em nada")
    void tokenWithoutRoleIsForbiddenEverywhere() throws Exception {
        mockMvc.perform(get("/api/trading/trades").header("Authorization", NO_ROLE))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/accounts")
                .header("Authorization", NO_ROLE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"initialBalance\": 100.00}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A documentação continua acessível a qualquer portador de token do realm")
    void docsAreOpenToAnyRealmToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs").header("Authorization", SERVICE))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs").header("Authorization", OPERATOR))
            .andExpect(status().isOk());
    }

    private Jwt jwtWithRoles(String... roles) {
        return jwt("service-account-lastro", roles);
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
