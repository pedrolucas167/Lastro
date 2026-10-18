package io.github.pedrolucas167.lastro.domain;

import java.math.BigDecimal;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Account {
    private final String id;
    private BigDecimal balance;
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

    public boolean tryDebit(BigDecimal amount) {
        lock.lock();
        try {
            if (this.balance.compareTo(amount) < 0) {
                return false;
            }
            this.balance = this.balance.subtract(amount);
            return true;
        } finally {
            lock.unlock();
        }
    }
}
