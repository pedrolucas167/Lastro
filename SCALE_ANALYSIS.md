# Análise de Escala - Lastro Trading Engine

## Visão Geral

Este documento descreve os testes de escala do Lastro (`ScaleTest`) e o que eles validam.

Ele **não** publica throughput nem limites de escala. A versão anterior publicava uma curva de degradação e apontava gargalos específicos; isso foi removido porque as medições não eram reproduzíveis e uma das causas apontadas era impossível. Veja [O que foi removido e por quê](#o-que-foi-removido-e-por-quê).

## Cenários

Seis cenários. Cada iteração envia um par BUY+SELL, portanto o número de ordens é o dobro do número de pares:

| Cenário | Threads | Pares | Ordens | Contas | Valida |
|---------|---------|-------|--------|--------|--------|
| 1 | 10 | 100 | 200 | 10 | Todas as ordens completam |
| 2 | 50 | 500 | 1.000 | 50 | Todas as ordens completam |
| 3 | 100 | 1.000 | 2.000 | 100 | Todas as ordens completam |
| 4 | 200 | 2.000 | 4.000 | 200 | Todas as ordens completam |
| 5 | 100 | 1.000 | 2.000 | 100 | Todas as ordens completam com muitas contas distintas |
| 6 | 100 (pool) | 10.000 | 20.000 | 1.000 | Todas as ordens completam com 1.000 contas |

Os cenários 5 e 6 usam um pool de no máximo 100 threads (`Math.min(numAccounts, 100)`), independente do número de contas.

## O que os testes provam

- Todas as ordens enviadas são processadas: cada cenário assere a contagem exata de operações concluídas
- O sistema completa antes do timeout do latch (60s, ou 120s nos cenários com muitas contas), o que descarta travamento e deadlock
- Contas distintas não corrompem o estado umas das outras

Essas são propriedades de **corretude sob carga**, e elas se sustentam.

## O que os testes NÃO provam

Não provam nada sobre desempenho. O `ScaleTest` mede a janela de processamento, sem warmup, sem repetição e com os seis cenários interferindo entre si na mesma JVM. O número que sai dali descreve o estado da JVM, não a capacidade do engine.

## O que foi removido e por quê

### Os números

A versão anterior publicava tempos e throughputs por cenário (por exemplo: 50 threads → 50ms → 10.000 ordens/seg; 200 threads → 5.133ms → ~390 ordens/seg). Medições na mesma máquina, com o código atual:

| Cenário | Publicado | Medido (execuções na mesma máquina) |
|---------|-----------|-------------------------------------|
| 10 threads / 200 ordens | < 100ms | 36ms–107ms |
| 50 threads / 1.000 ordens | **50ms** | **3.922ms–10.250ms** |
| 100 threads / 2.000 ordens | < 500ms | 60ms–95ms |
| 200 threads / 4.000 ordens | 5.133ms | 5.758ms–8.724ms |
| 100 contas / 2.000 ordens | 701ms | 761ms–1.132ms |
| 1.000 contas / 20.000 ordens | 30.690ms | 23.011ms–45.592ms |

Três problemas:

1. **Contagem de ordens errada em 2×.** O throughput era calculado com `numThreads * ordersPerThread`, mas cada iteração envia BUY **e** SELL — a asserção do próprio teste usava `*2`. Todos os valores publicados eram metade do real. Corrigido.

2. **Não reproduzível.** O cenário de 50 threads chega a ser **200× mais lento** que o publicado, e varia 2,6× entre execuções na mesma máquina.

3. **Não monotônico e dependente do contexto.** 50 threads é consistentemente mais lento que 100 threads no mesmo caminho de código; e o mesmo cenário roda 8× mais rápido quando executado isolado em uma JVM nova (tabela em [BASELINE_CONCURRENCY.md](./BASELINE_CONCURRENCY.md#por-que-os-números-de-desempenho-foram-removidos)). O padrão acompanha o contexto de execução, não a carga. A causa não foi isolada; [PERFORMANCE.md](./PERFORMANCE.md) mede contenção por símbolo com JMH e mostra que adicionar threads **não** aumenta o throughput.

### A explicação também estava errada

A versão anterior atribuía a lentidão do cenário de 1.000 contas à "contenção de locks" nas `Position`, porque "cada conta tem sua própria posição com um `ReentrantLock`". Isso é impossível no teste como escrito: `runConcurrentOrdersWithManyAccounts` submete **uma tarefa por conta**, então cada posição é tocada por uma única thread. Sem compartilhamento, não há contenção.

O que de fato difere nesses cenários é que **todas as ordens compartilham um único símbolo** (PETR4 nos cenários 1–4, VALE3 nos cenários 5–6), ou seja, disputam um único lock de símbolo. E o hardware: as medições foram feitas em um processador de **4 núcleos físicos / 8 lógicos**, com pools de 50 a 1.000 threads. Acima de 8 threads mede-se o escalonador do sistema operacional.

Isso não significa que não exista limite de escala — significa que ele **não foi demonstrado**. "Gargalo acima de 200 threads" era uma propriedade da máquina de medição, não do engine.

## Ambiente das medições

- Intel Core i5-10210U, 4 núcleos físicos / 8 lógicos
- 31,8 GB de RAM
- Windows 11 Pro
- Java 25.0.4.1 (HotSpot 64-Bit)

## Próximos Passos

A medição com JMH foi feita: [PERFORMANCE.md](./PERFORMANCE.md) traz o throughput do motor com 1 e 8 threads, com 1 e 8 símbolos. O resultado responde à pergunta que este documento tentava responder, e a resposta não é favorável — adicionar threads **não** aumenta o throughput. Investigar a causa exige profiling, não mais um benchmark.

## Conclusão

O `ScaleTest` valida que o sistema completa sob carga concorrente de 10 a 1.000 threads, com contagem exata de operações e sem deadlock. Essa garantia é real e está coberta.

As afirmações de desempenho deste documento foram removidas por não se reproduzirem. As que existem agora vêm de medição com JMH e estão em [PERFORMANCE.md](./PERFORMANCE.md).
