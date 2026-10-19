package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Account;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.Transaction;
import io.github.pedrolucas167.lastro.domain.TransactionStatus;
import io.github.pedrolucas167.lastro.domain.TransactionType;
import io.github.pedrolucas167.lastro.exception.AccountAccessDeniedException;
import io.github.pedrolucas167.lastro.exception.TransactionFailedException;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.journal.TransferCompleted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest {
    /** O back office opera qualquer conta; o titular apenas as que tem. */
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));
    private static final Actor HOLDER = new Actor("cliente-1", Set.of(Actor.HOLDER));

    private EventStore eventStore;
    private AccountService accountService;
    private TransactionService transactionService;
    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        accountService = new AccountService(eventStore);
        transactionService = new TransactionService(accountService, eventStore);
        
        fromAccount = accountService.createAccount(BACK_OFFICE, new BigDecimal("1000.00"));
        toAccount = accountService.createAccount(BACK_OFFICE, new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Deve transferir com sucesso quando saldo suficiente")
    void shouldTransferSuccessfullyWhenSufficientBalance() {
        Transaction transaction = transactionService.transfer(
            BACK_OFFICE,
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("300.00")
        );
        
        assertNotNull(transaction);
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
        assertEquals(fromAccount.getId(), transaction.getFromAccountId());
        assertEquals(toAccount.getId(), transaction.getToAccountId());
        assertEquals(new BigDecimal("300.00"), transaction.getAmount());
        
        assertEquals(new BigDecimal("700.00"), accountService.getAccount(fromAccount.getId()).getBalance());
        assertEquals(new BigDecimal("800.00"), accountService.getAccount(toAccount.getId()).getBalance());
    }

    @Test
    @DisplayName("Deve falhar transferência quando conta origem não existe")
    void shouldFailTransferWhenFromAccountDoesNotExist() {
        assertThrows(
            TransactionFailedException.class,
            () -> transactionService.transfer(
                BACK_OFFICE,
                "non-existent",
                toAccount.getId(),
                new BigDecimal("100.00")
            )
        );
    }

    @Test
    @DisplayName("Deve falhar transferência quando conta destino não existe")
    void shouldFailTransferWhenToAccountDoesNotExist() {
        assertThrows(
            TransactionFailedException.class,
            () -> transactionService.transfer(
                BACK_OFFICE,
                fromAccount.getId(),
                "non-existent",
                new BigDecimal("100.00")
            )
        );
    }

    @Test
    @DisplayName("Deve falhar transferência quando saldo insuficiente")
    void shouldFailTransferWhenInsufficientBalance() {
        Transaction transaction = transactionService.transfer(
            BACK_OFFICE,
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("2000.00")
        );
        
        assertEquals(TransactionStatus.FAILED, transaction.getStatus());
        assertEquals(new BigDecimal("1000.00"), accountService.getAccount(fromAccount.getId()).getBalance());
        assertEquals(new BigDecimal("500.00"), accountService.getAccount(toAccount.getId()).getBalance());
    }

    @Test
    @DisplayName("Deve buscar transação existente")
    void shouldGetExistingTransaction() {
        Transaction created = transactionService.transfer(
            BACK_OFFICE,
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("100.00")
        );
        
        Transaction retrieved = transactionService.getTransaction(BACK_OFFICE, created.getId());
        
        assertNotNull(retrieved);
        assertEquals(created.getId(), retrieved.getId());
        assertEquals(created.getStatus(), retrieved.getStatus());
    }

    @Test
    @DisplayName("Deve retornar null ao buscar transação inexistente")
    void shouldReturnNullForNonExistentTransaction() {
        Transaction transaction = transactionService.getTransaction(BACK_OFFICE, "non-existent");
        assertNull(transaction);
    }

    @Test
    @DisplayName("Deve criar transação com tipo correto")
    void shouldCreateTransactionWithCorrectType() {
        Transaction transaction = transactionService.transfer(
            BACK_OFFICE,
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("100.00")
        );
        
        assertEquals(TransactionType.TRANSFER, transaction.getType());
    }

    @Test
    @DisplayName("Deve manter saldos consistentes após transferência falha")
    void shouldKeepBalancesConsistentAfterFailedTransfer() {
        BigDecimal originalFromBalance = accountService.getAccount(fromAccount.getId()).getBalance();
        BigDecimal originalToBalance = accountService.getAccount(toAccount.getId()).getBalance();
        
        transactionService.transfer(
            BACK_OFFICE,
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("5000.00")
        );
        
        assertEquals(originalFromBalance, accountService.getAccount(fromAccount.getId()).getBalance());
        assertEquals(originalToBalance, accountService.getAccount(toAccount.getId()).getBalance());
    }

    @Test
    @DisplayName("O titular tira dinheiro da própria conta, não da conta dos outros")
    void holderCanOnlyTransferFromOwnAccount() {
        Account mine = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-1");
        Account other = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-2");

        Transaction transaction = transactionService.transfer(
            HOLDER, mine.getId(), other.getId(), new BigDecimal("30.00"));

        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
        assertEquals(0, new BigDecimal("70.00").compareTo(accountService.getAccount(mine.getId()).getBalance()));
        assertEquals(0, new BigDecimal("130.00").compareTo(accountService.getAccount(other.getId()).getBalance()));

        assertThrows(AccountAccessDeniedException.class,
            () -> transactionService.transfer(HOLDER, other.getId(), mine.getId(), new BigDecimal("10.00")));
        assertEquals(0, new BigDecimal("130.00").compareTo(accountService.getAccount(other.getId()).getBalance()));
    }

    @Test
    @DisplayName("Conta sem titular é do back office: o titular não alcança")
    void holderCannotReachAccountWithoutHolder() {
        Account unassigned = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"));
        Account mine = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-1");

        assertThrows(AccountAccessDeniedException.class,
            () -> transactionService.transfer(HOLDER, unassigned.getId(), mine.getId(), new BigDecimal("10.00")));
    }

    @Test
    @DisplayName("O titular vê a transação que toca a conta dele e nada além")
    void holderOnlySeesTransactionsTouchingOwnAccount() {
        Account mine = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-1");
        Account other = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-2");
        Account third = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), "cliente-3");

        Transaction received = transactionService.transfer(
            BACK_OFFICE, other.getId(), mine.getId(), new BigDecimal("10.00"));
        Transaction unrelated = transactionService.transfer(
            BACK_OFFICE, other.getId(), third.getId(), new BigDecimal("10.00"));

        assertEquals(received.getId(),
            transactionService.getTransaction(HOLDER, received.getId()).getId());
        assertThrows(AccountAccessDeniedException.class,
            () -> transactionService.getTransaction(HOLDER, unrelated.getId()));
    }

    @Test
    @DisplayName("Grava no journal quem transferiu, com os papéis daquele momento")
    void journalsWhoTransferredAndTheirRoles() {
        Account mine = accountService.createAccount(BACK_OFFICE, new BigDecimal("100.00"), HOLDER.subject());

        transactionService.transfer(HOLDER, mine.getId(), toAccount.getId(), new BigDecimal("10.00"));

        // O ator e o titular com o papel que ele carregava: papel revogado depois nao reescreve o fato.
        TransferCompleted event = (TransferCompleted) eventStore.readAll().getLast();
        assertEquals(HOLDER, event.actor());
    }
}
