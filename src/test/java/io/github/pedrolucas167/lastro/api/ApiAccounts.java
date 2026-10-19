package io.github.pedrolucas167.lastro.api;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contas reais para os testes de API: sem saldo nao ha compra, sem acao nao ha venda.
 *
 * A acao entra por custodia, como entraria no sistema de verdade.
 */
final class ApiAccounts {
    private static final String OPERATOR = "Bearer operador";

    private ApiAccounts() {
    }

    static String funded(MockMvc mockMvc, String balance) throws Exception {
        var created = mockMvc.perform(post("/api/accounts")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"initialBalance\": " + balance + "}"))
            .andExpect(status().isOk())
            .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    static String withShares(MockMvc mockMvc, String symbol, int quantity, String price)
            throws Exception {
        String accountId = funded(mockMvc, "0.00");
        deposit(mockMvc, accountId, symbol, quantity, price);
        return accountId;
    }

    static void deposit(MockMvc mockMvc, String accountId, String symbol, int quantity, String price)
            throws Exception {
        mockMvc.perform(post("/api/trading/positions/deposit")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": \"%s\", \"symbol\": \"%s\", \"quantity\": %d, \"price\": %s}"
                    .formatted(accountId, symbol, quantity, price)))
            .andExpect(status().isOk());
    }
}
