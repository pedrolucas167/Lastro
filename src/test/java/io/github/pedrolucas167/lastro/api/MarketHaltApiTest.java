package io.github.pedrolucas167.lastro.api;

import com.jayway.jsonpath.JsonPath;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.journal.DomainEvent;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.MarketEvent;
import io.github.pedrolucas167.lastro.journal.SymbolHalted;
import io.github.pedrolucas167.lastro.journal.SymbolResumed;
import io.github.pedrolucas167.lastro.service.MarketHaltService;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O halt por símbolo ponta a ponta: aciona, bloqueia ordem nova, não bloqueia cancelamento. */
@SpringBootTest(properties = "lastro.market.enabled=false")
@AutoConfigureMockMvc
class MarketHaltApiTest {
    private static final String OPERATOR = "Bearer operador";
    /** O halt e ato de operador: e ele que aparece no journal como ator do fato. */
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MarketHaltService marketHaltService;

    @Autowired
    private EventStore eventStore;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode("operador")).willReturn(jwtWithOperatorRole());
    }

    @AfterEach
    void releaseAllSymbols() {
        marketHaltService.haltedSymbols().keySet().forEach(symbol -> marketHaltService.resume(BACK_OFFICE, symbol));
    }

    @Test
    @DisplayName("O halt bloqueia ordem nova do símbolo pausado e registra o motivo")
    void haltBlocksNewOrders() throws Exception {
        mockMvc.perform(post("/api/market/PETR4/halt")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"circuito de protecao\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("PETR4"))
            .andExpect(jsonPath("$.halted").value(true))
            .andExpect(jsonPath("$.reason").value("circuito de protecao"));

        mockMvc.perform(post("/api/trading/orders")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody("PETR4")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value(containsString("Trading halted for PETR4")));

        assertTrue(journaled(SymbolHalted.class, "PETR4"));
        assertEquals(BACK_OFFICE, actorOf(SymbolHalted.class, "PETR4"));
    }

    @Test
    @DisplayName("Sem halt, a mesma ordem é aceita: o bloqueio é do símbolo, não da API")
    void orderIsAcceptedForSymbolWithoutHalt() throws Exception {
        mockMvc.perform(post("/api/market/PETR4/halt")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"circuito\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/trading/orders")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody("VALE3")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("O halt não cancela o book: ordem em repouso continua cancelável")
    void restingOrdersCanStillBeCancelledWhileHalted() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/trading/orders")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody("PETR4")))
            .andExpect(status().isOk())
            .andReturn();

        String orderId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/market/PETR4/halt")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"circuito\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/trading/orders/" + orderId + "/cancel")
                .header("Authorization", OPERATOR)
                .param("symbol", "PETR4"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("O símbolo pausado aparece na lista e o resume libera")
    void haltedSymbolsAreListedAndResumeReleases() throws Exception {
        mockMvc.perform(post("/api/market/VALE3/halt")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"noticia relevante\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/market/halted").header("Authorization", OPERATOR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.VALE3").value("noticia relevante"));

        mockMvc.perform(post("/api/market/VALE3/resume").header("Authorization", OPERATOR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.halted").value(false));

        mockMvc.perform(get("/api/market/halted").header("Authorization", OPERATOR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.VALE3").doesNotExist());

        assertTrue(journaled(SymbolResumed.class, "VALE3"));
    }

    @Test
    @DisplayName("Halt sem corpo registra um motivo padrao")
    void haltWithoutBodyRecordsADefaultReason() throws Exception {
        mockMvc.perform(post("/api/market/ITUB4/halt").header("Authorization", OPERATOR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.halted").value(true))
            .andExpect(jsonPath("$.reason").value(not(blankOrNullString())));
    }

    // 30.50 esta dentro da banda de preco do PETR4 (referencia 30.50): sem isso a rejeicao
    // viria da banda, e nao do halt.
    private String orderBody(String symbol) {
        return "{\"symbol\": \"%s\", \"side\": \"BUY\", \"type\": \"LIMIT\", \"price\": 30.50, "
            .formatted(symbol)
            + "\"quantity\": 100, \"accountId\": \"acc-1\"}";
    }

    private boolean journaled(Class<? extends MarketEvent> type, String symbol) {
        return eventStore.readAll().stream()
            .filter(type::isInstance)
            .map(MarketEvent.class::cast)
            .anyMatch(event -> symbol.equals(symbolOf(event)));
    }

    /** O ator gravado no journal para o fato: quem fez, com os papeis que carregava. */
    private Actor actorOf(Class<? extends MarketEvent> type, String symbol) {
        return eventStore.readAll().stream()
            .filter(type::isInstance)
            .map(MarketEvent.class::cast)
            .filter(event -> symbol.equals(symbolOf(event)))
            .map(DomainEvent::actor)
            .findFirst()
            .orElseThrow();
    }

    private String symbolOf(MarketEvent event) {
        return switch (event) {
            case SymbolHalted e -> e.symbol();
            case SymbolResumed e -> e.symbol();
        };
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
