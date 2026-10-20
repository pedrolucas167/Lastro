package io.github.pedrolucas167.lastro.ledger;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O livro-razao: as partidas dobradas que o journal implica, conta a conta.
 *
 * O journal guarda o fato e o livro guarda a contabilidade dele. Como o mapeamento e deterministico
 * e mora so aqui, reconstruir o journal reconstroi o livro: o replay chega ao mesmo extrato e ao
 * mesmo balanco, sem depender de nenhum estado que nao esteja na trilha.
 *
 * Duas provas saem daqui. Toda transacao fecha por construcao ({@link LedgerTransaction} recusa o
 * contrario), e a soma dos saldos dos clientes e exatamente o caixa na liquidante -- o dinheiro que a
 * corretora deve aos clientes tem lastro no ativo que ela tem.
 */
@Service
public class LedgerService {

    private final Map<String, LedgerAccount> accounts = new ConcurrentHashMap<>();
    private final Map<String, List<LedgerLine>> statements = new ConcurrentHashMap<>();

    /**
     * Lanca uma transacao fechada. O metodo inteiro e atomico: as partidas de um fato aparecem
     * juntas no livro e no extrato, mesmo com trades e transferencias chegando de varias threads.
     */
    public synchronized void post(LedgerTransaction transaction) {
        for (Posting posting : transaction.postings()) {
            LedgerAccount account = account(posting.accountCode());
            account.post(posting.side(), posting.amount());
            if (ChartOfAccounts.isClient(posting.accountCode())) {
                recordLine(transaction, posting, account);
            }
        }
    }

    private void recordLine(LedgerTransaction transaction, Posting posting, LedgerAccount account) {
        String accountId = ChartOfAccounts.clientId(posting.accountCode());
        LedgerLine line = new LedgerLine(transaction.reference(), transaction.reason(),
            transaction.occurredAt(), posting.side(), posting.amount(),
            counterpartyOf(transaction, posting), account.getBalance());
        statements.computeIfAbsent(accountId, id -> new ArrayList<>()).add(line);
    }

    /**
     * A contraparte do movimento. Entre clientes ela vem sem o id: quem ve o extrato de uma conta
     * nao descobre a conta da outra ponta.
     */
    private static String counterpartyOf(LedgerTransaction transaction, Posting posting) {
        return transaction.postings().stream()
            .filter(other -> !other.accountCode().equals(posting.accountCode()))
            .map(Posting::accountCode)
            .map(code -> ChartOfAccounts.isClient(code) ? ChartOfAccounts.CLIENT_LIABILITY : code)
            .findFirst()
            .orElse(null);
    }

    /** O saldo de qualquer conta do plano; zero para codigo que ainda nao teve movimento. */
    public BigDecimal balanceOf(String accountCode) {
        LedgerAccount account = accounts.get(accountCode);
        return account == null ? BigDecimal.ZERO : account.getBalance();
    }

    /** O caixa do cliente: creditos menos debitos no codigo dele (zero se nao houver movimento). */
    public BigDecimal clientBalance(String accountId) {
        return balanceOf(ChartOfAccounts.client(accountId));
    }

    /** O que a corretora deve aos clientes, somando todas as contas de cliente. */
    public BigDecimal clientLiabilitiesTotal() {
        return accounts.values().stream()
            .filter(account -> ChartOfAccounts.isClient(account.getCode()))
            .map(LedgerAccount::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal settlementBalance() {
        return balanceOf(ChartOfAccounts.SETTLEMENT);
    }

    /** O plano de contas em ordem de codigo, com debitos, creditos e saldo de cada conta. */
    public List<LedgerAccount> trialBalance() {
        return accounts.values().stream()
            .sorted(Comparator.comparing(LedgerAccount::getCode))
            .toList();
    }

    /** A prova de que todo debito teve credito: os dois lados do livro somam o mesmo. */
    public boolean isBalanced() {
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (LedgerAccount account : accounts.values()) {
            debits = debits.add(account.getDebits());
            credits = credits.add(account.getCredits());
        }
        return debits.compareTo(credits) == 0;
    }

    /** O extrato do cliente, na ordem em que os fatos entraram no journal. */
    public synchronized List<LedgerLine> statementOf(String accountId) {
        return List.copyOf(statements.getOrDefault(accountId, List.of()));
    }

    /** Zera o livro para reconstrui-lo do journal. */
    public synchronized void clear() {
        accounts.clear();
        statements.clear();
    }

    private LedgerAccount account(String accountCode) {
        return accounts.computeIfAbsent(accountCode, LedgerAccount::new);
    }
}
