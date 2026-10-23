package io.github.pedrolucas167.lastro.api;

import com.jayway.jsonpath.JsonPath;
import io.github.pedrolucas167.lastro.domain.Account;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O cadastro de cliente ponta a ponta, e o que a suspensao muda: ordem nao nasce, dinheiro nao sai
 * e o que entra continua entrando. Cada teste usa um titular proprio porque o journal e o cadastro
 * sao compartilhados pelo contexto.
 */
@SpringBootTest(properties = "lastro.market.enabled=false")
@AutoConfigureMockMvc
class ClientApiTest {
    private static final String OPERATOR = "Bearer operador";
    private static final String HOLDER = "Bearer titular";
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private String subject;

    @BeforeEach
    void setUp() {
        subject = "titular-" + UUID.randomUUID();
        // Um stub so: o titular de cada teste e novo, e a mock nao pode ficar com stub que nao foi
        // chamado (o Mockito estrito recusa).
        given(jwtDecoder.decode(anyString())).willAnswer(invocation -> {
            String token = invocation.getArgument(0);
            return "operador".equals(token)
                ? jwt("operador", Actor.OPERATOR)
                : jwt(subject, Actor.HOLDER);
        });
    }

    @Test
    @DisplayName("O operador cadastra e o titular le o proprio cadastro em /me")
    void operatorRegistersAndHolderReadsOwnRegistration() throws Exception {
        String clientId = register(subject, "Maria");

        mockMvc.perform(get("/api/clients/me").header("Authorization", HOLDER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(clientId))
            .andExpect(jsonPath("$.subject").value(subject))
            .andExpect(jsonPath("$.name").value("Maria"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Titular sem cadastro responde 404 em /me, e o titular nao cadastra")
    void holderWithoutClientIsNotFoundAndCannotRegister() throws Exception {
        mockMvc.perform(get("/api/clients/me").header("Authorization", HOLDER))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/clients")
                .header("Authorization", HOLDER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(subject, "Maria")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cadastrar o mesmo titular duas vezes responde conflito")
    void registeringTheSameSubjectTwiceIsAConflict() throws Exception {
        register(subject, "Maria");

        mockMvc.perform(post("/api/clients")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(subject, "Maria Silva")))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Cliente desconhecido responde 404")
    void unknownClientIsNotFound() throws Exception {
        mockMvc.perform(get("/api/clients/" + UUID.randomUUID())
                .header("Authorization", OPERATOR))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cliente suspenso nao abre ordem, nao transfere, mas continua recebendo credito")
    void suspensionBlocksOrdersAndOutgoingMoneyOnly() throws Exception {
        String clientId = register(subject, "Maria");
        Account account = accountService.createAccount(BACK_OFFICE, new BigDecimal("1000.00"), subject);
        Account destination = accountService.createAccount(BACK_OFFICE, new BigDecimal("0.00"));

        submitBuy(account.getId()).andExpect(status().isOk());

        suspend(clientId, "documentacao vencida");

        submitBuy(account.getId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value(containsString("documentacao vencida")));
        transfer(account.getId(), destination.getId())
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/accounts/" + account.getId() + "/credit")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 50.00}"))
            .andExpect(status().isOk());

        assertEquals(0, new BigDecimal("1050.00")
            .compareTo(accountService.getAccount(account.getId()).getBalance()));
        assertEquals(0, new BigDecimal("0.00")
            .compareTo(accountService.getAccount(destination.getId()).getBalance()));
    }

    @Test
    @DisplayName("Reativar devolve a operacao e o fato fica no cadastro")
    void reactivationRestoresOperations() throws Exception {
        String clientId = register(subject, "Maria");
        Account account = accountService.createAccount(BACK_OFFICE, new BigDecimal("1000.00"), subject);

        suspend(clientId, "revisao");
        submitBuy(account.getId()).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/clients/" + clientId + "/reactivate")
                .header("Authorization", OPERATOR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.suspensionReason").doesNotExist());

        submitBuy(account.getId()).andExpect(status().isOk());

        mockMvc.perform(get("/api/clients/me").header("Authorization", HOLDER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private String register(String subjectToRegister, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/clients")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(subjectToRegister, name)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private void suspend(String clientId, String reason) throws Exception {
        mockMvc.perform(post("/api/clients/" + clientId + "/suspend")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"" + reason + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.suspensionReason").value(reason));
    }

    private ResultActions submitBuy(String accountId) throws Exception {
        return mockMvc.perform(post("/api/trading/orders")
            .header("Authorization", OPERATOR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"symbol\": \"CLI3\", \"side\": \"BUY\", \"type\": \"LIMIT\", \"price\": 30.00, "
                + "\"quantity\": 10, \"accountId\": \"" + accountId + "\"}"));
    }

    private ResultActions transfer(String fromAccountId, String toAccountId) throws Exception {
        return mockMvc.perform(post("/api/transactions/transfer")
            .header("Authorization", OPERATOR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fromAccountId\": \"%s\", \"toAccountId\": \"%s\", \"amount\": 10.00}"
                .formatted(fromAccountId, toAccountId)));
    }

    private static String body(String subject, String name) {
        return "{\"subject\": \"%s\", \"name\": \"%s\"}".formatted(subject, name);
    }

    private static Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(subject)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("realm_access", Map.of("roles", List.of(role)))
            .build();
    }
}
