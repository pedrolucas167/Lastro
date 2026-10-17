# Baseline de Concorrência - Lastro Trading Engine

## Visão Geral

Este documento registra a validação de **corretude** do Lastro sob carga concorrente: que todas as operações completam, que o order book e as posições permanecem consistentes e que não há deadlocks.

Ele **não** é um relatório de desempenho. As tabelas de throughput e latência que existiam aqui foram removidas porque não eram reproduzíveis — o motivo está em [Por que os números de desempenho foram removidos](#por-que-os-números-de-desempenho-foram-removidos).

## Contexto do Problema

Sistemas de negociação exigem garantias rigorosas de concorrência:

- **Atomicidade**: cada operação de matching é indivisível
- **Consistência**: o order book mantém integridade sob carga
- **Isolamento**: operações concorrentes não interferem entre si
- **Ausência de deadlock**: nenhuma combinação de operações trava o sistema

O Lastro implementa matching com price-time priority: ordens são executadas por preço e, dentro do mesmo preço, por ordem de chegada.

## Estratégias de Concorrência

Todas verificadas no código-fonte atual:

### 1. Lock granular por entidade

`Order` e `Position` têm cada um seu próprio `ReentrantLock`, em vez de um lock global:

```java
// Order.java, Position.java
private final Lock lock = new ReentrantLock();
```

### 2. Lock por símbolo no matching engine

O `MatchingEngine` mantém um lock por símbolo, de modo que símbolos diferentes não competem entre si:

```java
// MatchingEngine.java
private final ConcurrentHashMap<String, Lock> symbolLocks;
```

### 3. ConcurrentSkipListMap para o order book

```java
// OrderBook.java
private final ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> bids; // ordem decrescente
private final ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> asks; // ordem crescente
```

A `LinkedList` de cada nível de preço é o que garante FIFO dentro do preço, ou seja, o componente de *time* da price-time priority.

### 4. ConcurrentLinkedQueue para trades

```java
// MatchingEngine.java
private final ConcurrentLinkedQueue<Trade> trades;
```

### 5. Idempotência em transferências

`IdempotencyService` guarda o resultado por chave (janela de 24h), consumido por `TransactionService.transfer`. É por isso que transferências repetidas com a mesma `idempotencyKey` não geram efeito duplicado — **ordens não têm esse mecanismo**.

## Cenários de Teste

Onze testes, em duas classes: `TradingConcurrencyTest` (5) e `ConcurrencyTest` (6).

### Trading

| Teste | Configuração | Valida |
|-------|--------------|--------|
| `testConcurrentBuyOrdersAtSamePrice` | PETR4, 100 ordens BUY, 10 threads, preço único | Todas as 100 ordens processadas, nenhuma perdida |
| `testConcurrentMatchingOfBuyAndSellOrders` | VALE3, 50 pares, 10 threads BUY + 10 SELL | Quantidade total negociada exatamente 500 |
| `testConcurrentOrderBookConsistency` | ITUB4, 200 ordens mistas, 20 threads, 10 níveis de preço | Book consistente, spread nunca negativo |
| `testNoDeadlockInConcurrentTrading` | BBDC4, 1.000 ciclos BUY/SELL, 10 threads | 1.000 ciclos completam, sem deadlock |
| `testPositionConsistencyUnderConcurrentTrades` | WEGE3, 100 trades, 10 threads | Posições atualizadas, quantidade negociada > 0 |

Total de ordens enviadas pelos cenários de trading: **2.600** (contagem direta no código).

### Transações (contas)

| Teste | Valida |
|-------|--------|
| `shouldMaintainConsistencyWithConcurrentCredits` | 100 créditos concorrentes de 10,00 resultam exatamente em 1.000,00 |
| `shouldMaintainConsistencyWithConcurrentDebits` | 100 débitos concorrentes com saldo suficiente: todos aplicados, saldo exato |
| `shouldRejectConcurrentDebitsWhenInsufficientBalance` | Débitos concorrentes acima do saldo são rejeitados, sem saldo negativo |
| `shouldMaintainConsistencyWithConcurrentTransfers` | Transferências concorrentes preservam a soma dos saldos |
| `shouldNotDeadlockInCyclicTransfers` | Transferências cíclicas (A→B, B→A) completam, provando ordem de aquisição de locks consistente |
| `shouldPreserveBalanceInvariantsUnderHighConcurrency` | Invariantes de saldo sob alta concorrência |

## Garantias Validadas

O que os testes acima **provam**:

1. **Atomicidade**: nenhuma ordem é perdida ou duplicada sob concorrência
2. **Consistência**: o order book mantém integridade; quantidades negociadas batem exatamente com o esperado
3. **Isolamento**: saldos e posições ficam corretos após execução concorrente
4. **Ausência de deadlock**: nenhum cenário trava, inclusive transferências cíclicas
5. **Idempotência de transferências**: chave de idempotência evita aplicação duplicada

Price-time priority é validada pelos testes de domínio (`MatchingEngineReplaceTest`, `MatchingEngineTimeInForceTest`) e pelo replay do journal (`TradingJournalReplayTest`), não pelos testes de concorrência.

## O que estes testes NÃO provam

Eles não medem desempenho. Os testes verificam que **todas as operações completam**; o tempo que isso leva varia em ordens de grandeza com o estado da JVM (JIT, GC) e com o escalonador do sistema operacional, para o mesmo código. Detalhes e evidências em [Por que os números de desempenho foram removidos](#por-que-os-números-de-desempenho-foram-removidos).

## Por que os números de desempenho foram removidos

A versão anterior deste documento publicava uma tabela de desempenho (throughput ~77 ordens/segundo, latência média <10ms, 1.123 ops/s, 1.460 operações). Ela foi removida por quatro motivos, todos verificáveis:

1. **Contradição interna.** A mesma tabela trazia "77 ordens/segundo" e "1.123 ops/s". O segundo valor é `1.460 / 1,3s`; o primeiro não tem origem identificável. Além disso, "1.460 operações" não corresponde ao que os testes enviam (2.600 ordens só nos cenários de trading).

2. **Erro de contagem de 2× nos testes.** O `ScaleTest` calculava o throughput com `numThreads * ordersPerThread`, mas cada iteração enviava **duas** ordens (BUY e SELL) — a própria asserção do teste usava `*2`. Todo throughput publicado era metade do real. Corrigido.

3. **Não reproduzível.** O cenário de 50 threads documentava 50ms / 10.000 ordens/seg. Medido em 4 execuções na mesma máquina: 5.063ms–5.810ms / 86–98 ordens/seg. Fator de 106×.

4. **Mede o ambiente, não o engine.** Medições do mesmo cenário, mesmo código:

   | Cenário | Dentro da suíte | Isolado (JVM nova) |
   |---------|-----------------|--------------------|
   | 50 threads / 1.000 ordens | 5.063–5.810ms | 567–779ms |
   | 10 threads / 200 ordens | 36–107ms | 292–612ms |

   O mesmo teste fica 8× mais rápido isolado — e o de 10 threads fica *mais lento* isolado, na direção oposta. O que muda não é a carga (idêntica), é o estado da JVM. Somando a isso: os resultados não são monotônicos (50 threads mais lento que 100), a variância chega a 3× entre execuções, e as medições foram feitas em um processador de **4 núcleos físicos / 8 lógicos** enquanto os cenários usam 50 a 1.000 threads — acima de 8 threads mede-se o escalonador, não o engine.

Números de desempenho exigem um instrumento próprio, com warmup, forks e repetições — não `System.currentTimeMillis()` em volta de um teste de corretude.

## Limitações Conhecidas

⚠️ **Estado em memória durante o teste**: não inclui latência de rede nem I/O de disco real
⚠️ **Carga local**: threads na mesma JVM, não clientes distribuídos
⚠️ **Recuperação de falha abrupta**: o replay do journal no startup é testado (`PersistenceReplayTest`, `TradingJournalReplayTest`), mas não há teste de morte do processo no meio de uma escrita

## Próximos Passos

1. **Medição de desempenho com JMH**: warmup explícito, forks e repetições, publicando mediana e dispersão em vez de uma execução única
2. **Latência de rede**: simular com proxy, medindo a camada HTTP separadamente do engine
3. **Testes de longa duração**: execuções de horas para detectar vazamentos de memória
4. **Recuperação de falhas**: teste de morte do processo durante escrita no journal

## Conclusão

Os onze testes de concorrência passam e validam atomicidade, consistência, isolamento e ausência de deadlock no matching engine e nas contas. Essa é a garantia que o Lastro oferece hoje.

Desempenho é uma afirmação diferente, que este documento não faz: as medições anteriores não eram reproduzíveis e foram removidas em vez de substituídas por números igualmente frágeis.
