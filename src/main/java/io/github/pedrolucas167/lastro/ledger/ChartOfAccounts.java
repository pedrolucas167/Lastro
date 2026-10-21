package io.github.pedrolucas167.lastro.ledger;

/**
 * O plano de contas do Lastro.
 *
 * O dinheiro do cliente e um passivo da corretora: ela deve esse caixa a ele. Do outro lado fica o
 * caixa na liquidante, o ativo que responde por esses passivos -- e a contraparte de tudo que entra
 * e sai do sistema. Nao ha conta de resultado ainda: taxa, quando existir, entra aqui como receita.
 */
public final class ChartOfAccounts {

    public static final String SETTLEMENT = "ASSETS:SETTLEMENT";

    /** O passivo de clientes, sem o id: e o maximo que um extrato pode dizer da contraparte. */
    public static final String CLIENT_LIABILITY = "LIABILITIES:CLIENT";

    private static final String CLIENT_PREFIX = CLIENT_LIABILITY + ":";
    private static final String ASSET_PREFIX = "ASSETS:";

    /** O caixa de um cliente: um codigo por conta, para o saldo dele sair do proprio livro. */
    public static String client(String accountId) {
        return CLIENT_PREFIX + accountId;
    }

    public static boolean isClient(String accountCode) {
        return accountCode.startsWith(CLIENT_PREFIX);
    }

    public static String clientId(String accountCode) {
        if (!isClient(accountCode)) {
            throw new IllegalArgumentException("Not a client account code: " + accountCode);
        }
        return accountCode.substring(CLIENT_PREFIX.length());
    }

    public static AccountNature natureOf(String accountCode) {
        return accountCode.startsWith(ASSET_PREFIX) ? AccountNature.ASSET : AccountNature.LIABILITY;
    }

    private ChartOfAccounts() {
    }
}
