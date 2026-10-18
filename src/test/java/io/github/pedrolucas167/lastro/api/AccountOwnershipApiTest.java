package io.github.pedrolucas167.lastro.api;

import io.github.pedrolucas167.lastro.domain.Account;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.Transaction;
import io.github.pedrolucas167.lastro.service.AccountService;
import io.github.pedrolucas167.lastro.service.TransactionService;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O vínculo entre conta e titular visto pela API: o titular so alcanca as contas que tem,
 * o back office alcanca qualquer uma, e o vinculo e definido uma vez.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountOwnershipApiTest {
    /** O back office abre as contas dos outros: o titular nao abre conta para ninguem. */
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));
    private static final String HOLDER_ONE = "Bearer cliente-1";
    private static final String HOLDER_TWO = "Bearer cliente-2";
    private static final String OPERATOR = "Bearer operador";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode("cliente-1")).willReturn(jwt("cliente-1", "lastro-holder"));
        given(jwtDecoder.decode("cliente-2")).willReturn(jwt("cliente-2", "lastro-holder"));
        given(jwtDecoder.decode("operador")).willReturn(jwt("operador-1", "lastro-operator"));
    }

    @Test
    @DisplayName("O titular lê a própria conta e não a dos outros")
    void holderReadsOnlyOwnAccount() throws Exception {
        String mine = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-1").getId();
        String other = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-2").getId();

        mockMvc.perform(get("/api/accounts/" + mine).header("Authorization", HOLDER_ONE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.holder").value("cliente-1"));

        mockMvc.perform(get("/api/accounts/" + other).header("Authorization", HOLDER_ONE))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("O titular transfere da própria conta e não da conta dos outros")
    void holderTransfersOnlyFromOwnAccount() throws Exception {
        String mine = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-1").getId();
        String other = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-2").getId();

        mockMvc.perform(post("/api/transactions/transfer")
                .header("Authorization", HOLDER_ONE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccountId\": \"%s\", \"toAccountId\": \"%s\", \"amount\": 10.00}"
                    .formatted(other, mine)))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/transactions/transfer")
                .header("Authorization", HOLDER_ONE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccountId\": \"%s\", \"toAccountId\": \"%s\", \"amount\": 10.00}"
                    .formatted(mine, other)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("O titular lê a transação que tocou a conta dele, e só ela")
    void holderReadsOnlyTransactionsTouchingOwnAccount() throws Exception {
        Account mine = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-1");
        Account other = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-2");
        Account third = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-3");
        Actor backOffice = new Actor("operador-1", Set.of(Actor.OPERATOR));

        Transaction received = transactionService.transfer(
            backOffice, other.getId(), mine.getId(), new BigDecimal("10.00"));
        Transaction unrelated = transactionService.transfer(
            backOffice, other.getId(), third.getId(), new BigDecimal("10.00"));

        mockMvc.perform(get("/api/transactions/" + received.getId()).header("Authorization", HOLDER_ONE))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/transactions/" + unrelated.getId()).header("Authorization", HOLDER_ONE))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/transactions/" + unrelated.getId()).header("Authorization", OPERATOR))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("O titular não abre conta em nome de outro")
    void holderCannotOpenAccountForAnotherHolder() throws Exception {
        mockMvc.perform(post("/api/accounts")
                .header("Authorization", HOLDER_ONE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"initialBalance\": 100.00, \"holder\": \"cliente-2\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A lista do titular traz apenas as contas que ele tem")
    void holderListsOnlyOwnAccounts() throws Exception {
        given(jwtDecoder.decode("cliente-lista")).willReturn(jwt("cliente-lista", "lastro-holder"));
        String mine = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-lista").getId();
        accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-outro");

        mockMvc.perform(get("/api/accounts/mine").header("Authorization", "Bearer cliente-lista"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(mine));
    }

    @Test
    @DisplayName("O operador define o titular uma vez e não troca depois")
    void operatorAssignsHolderOnce() throws Exception {
        String accountId = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00")).getId();

        mockMvc.perform(post("/api/accounts/" + accountId + "/holder")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holder\": \"cliente-2\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.holder").value("cliente-2"));

        mockMvc.perform(post("/api/accounts/" + accountId + "/holder")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holder\": \"cliente-2\"}"))
            .andExpect(status().isConflict());

        mockMvc.perform(get("/api/accounts/" + accountId).header("Authorization", HOLDER_TWO))
            .andExpect(status().isOk());
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
