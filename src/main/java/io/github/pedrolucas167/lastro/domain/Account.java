package io.github.pedrolucas167.lastro.domain;

import java.math.BigDecimal;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Account {
    private final String id;
    private BigDecimal balance;
    /** Bloqueado para ordens de compra em aberto: sai do disponivel, nao do saldo. */
    private BigDecimal held = BigDecimal.ZERO;
    /** Escrito uma vez (request ou replay) e lido por outras threads sem passar pelo lock do saldo. */
    private volatile String holder;
    private final Lock lock = new ReentrantLock();

    public Account(String id, BigDecimal initialBalance) {
        this.id = id;
        this.balance = initialBalance;
    }

    public String getId() {
        return id;
    }

    public String getHolder() {
        return holder;
    }

    /** O titular e definido uma vez: a conta nao troca de dono por reatribuicao. */
    public void assignHolder(String holder) {
        if (this.holder != null) {
            throw new IllegalStateException("Account " + id + " already has a holder");
        }
        this.holder = holder;
    }

    public boolean heldBy(String subject) {
        return holder != null && holder.equals(subject);
    }

    public BigDecimal getBalance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    /** O que a conta ainda pode gastar ou transferir: o saldo menos o bloqueado para ordens. */
    public BigDecimal getAvailable() {
        lock.lock();
        try {
            return balance.subtract(held);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Restaura o bloqueado no replay: o valor vem da soma das ordens de compra em aberto, que o
     * journal reconstroi. E um valor reconstruido, nao um fato novo -- por isso nao ha checagem
     * aqui: ordem antiga, de quando dava para entrar sem lastro, continua abrindo.
     */
    public void rebuildHeld(BigDecimal amount) {
        lock.lock();
        try {
            held = amount.signum() < 0 ? BigDecimal.ZERO : amount;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Bloqueia valor para uma ordem de compra. Devolve false quando o disponivel nao cobre --
     * e por isso a checagem e o bloqueio sao um so passo atomico: duas ordens que chegam juntas
     * nao podem prometer o mesmo dinheiro.
     */
    public boolean hold(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        lock.lock();
        try {
            if (balance.subtract(held).compareTo(amount) < 0) {
                return false;
            }
            held = held.add(amount);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Devolve ao disponivel o valor bloqueado que a ordem nao vai mais gastar. */
    public void release(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        lock.lock();
        try {
            BigDecimal remaining = held.subtract(amount);
            // Liberar mais do que esta bloqueado seria contar dinheiro duas vezes; o disponivel
            // nunca passa do saldo, entao o bloqueio para no zero.
            held = remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
        } finally {
            lock.unlock();
        }
    }

    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        lock.lock();
        try {
            this.balance = this.balance.add(amount);
        } finally {
            lock.unlock();
        }
    }

    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        lock.lock();
        try {
            if (this.balance.compareTo(amount) < 0) {
                throw new IllegalArgumentException("Insufficient funds");
            }
            this.balance = this.balance.subtract(amount);
        } finally {
            lock.unlock();
        }
    }

    /** Tira do disponivel (saldo menos bloqueado): dinheiro prometido a uma ordem nao sai da conta. */
    public boolean tryDebit(BigDecimal amount) {
        lock.lock();
        try {
            if (balance.subtract(held).compareTo(amount) < 0) {
                return false;
            }
            this.balance = this.balance.subtract(amount);
            return true;
        } finally {
            lock.unlock();
        }
    }
}
