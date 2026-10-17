# Desempenho - Lastro Trading Engine

## Visão Geral

Este documento publica as medições de desempenho do Lastro, feitas com **JMH** (Java Microbenchmark Harness).

Ele substitui os números que existiam no README e em `SCALE_ANALYSIS.md`. Aqueles não eram reproduzíveis — o motivo, com evidências, está em [BASELINE_CONCURRENCY.md](./BASELINE_CONCURRENCY.md#por-que-os-números-de-desempenho-foram-removidos). Estes são: cada linha abaixo é reproduzível com os comandos da seção [Como Reproduzir](#como-reproduzir).

## Metodologia

- **JMH 1.37**: `@Fork(2)` (duas JVMs novas por configuração), 3 iterações de warmup e 5 de medição, 1s cada
- Cada benchmark reinicia o estado do engine a cada iteração (`@Setup(Level.Iteration)`), para não medir crescimento de memória
- **Logging desligado** (`src/jmh/resources/logback-test.xml`). O `MatchingEngine` e o `OrderService` logam em INFO a cada ordem e a cada trade; com logging ativo o benchmark mediria o appender, não o engine. Esse custo é real e **não** está incluído aqui
- O que o JMH reporta como erro é a dispersão entre iterações e forks, não a variação da máquina ao longo do dia

## Ambiente

- Intel Core i5-10210U — **4 núcleos físicos / 8 lógicos**
- 31,8 GB de RAM
- Windows 11 Pro
- Java 25.0.4.1 (HotSpot 64-Bit)
- Máquina de desenvolvimento, não um servidor dedicado: outros processos competem por CPU

## Resultados

### Motor de matching

Mede apenas o `MatchingEngine.submitOrder`: sem journal, sem logging, sem HTTP. Cada invocação envia duas ordens limit que cruzam no mesmo preço, portanto o número já está em **ordens/segundo** (o par é contado como duas operações).

| Benchmark | Threads | Símbolos | Score | Erro | Unidade |
|---|---|---|---|---|---|
| `MatchingEngineBenchmark.matchedPair` | 1 | 1 | **817.639** | ± 81.358 | ordens/s |
| `MatchingEngineBenchmark.matchedPair` | 8 | 1 (contenção) | **504.957** | ± 68.921 | ordens/s |
| `MatchingEngineBenchmark.matchedPair` | 8 | 8 | **642.861** | ± 127.836 | ordens/s |

### Caminho da aplicação

Single-thread, para medir custo por operação e não throughput agregado.

| Benchmark | Score | Erro | Unidade | O que inclui |
|---|---|---|---|---|
| `OrderPathBenchmark.journalAppend` | **1.987.580** | ± 471.064 | eventos/s | Criar o evento e anexá-lo ao journal **em memória** |
| `OrderPathBenchmark.createOrder` | **422.773** | ± 73.449 | ordens/s | UUID + append no journal + registro na tabela de ordens |

## Leitura dos Resultados

### O motor faz ~800 mil ordens/segundo em uma thread

Isso equivale a ~1,2µs por par casado, incluindo a construção dos dois objetos `Order`. O README declarava **~77 ordens/segundo** — uma diferença de mais de quatro ordens de grandeza. A explicação não é o motor ter ficado mais rápido: o número antigo media logging, escrita no journal, criação de pool de threads e GC, e ainda contava metade das ordens. É exatamente o tipo de número que não deveria ser publicado.

### Adicionar threads não aumenta o throughput

Oito threads somam **menos** que uma thread sozinha — 505 mil contra 818 mil ordens/s quando disputam o mesmo símbolo, e 643 mil mesmo quando cada thread usa um símbolo distinto.

Duas coisas estão por trás disso, e o benchmark **não** isola qual pesa mais:

1. A máquina tem **4 núcleos físicos**. Oito threads já saturam o hardware; parte do resultado é o escalonador, não o engine.
2. Toda submissão anexa os trades gerados a uma **única `ConcurrentLinkedQueue` compartilhada** (`MatchingEngine.trades`), independente do símbolo. É um ponto de serialização global, e é a suspeita principal para o ganho não aparecer nem com símbolos distintos.

A conclusão defensável hoje é: **paralelismo por símbolo não é suficiente para escalar** enquanto houver estado compartilhado no caminho quente. Confirmar a causa exige profiling (JFR ou async-profiler), não outro benchmark.

### O caminho da API custa ~2,4µs por ordem

`createOrder` faz ~423 mil ordens/s (~2,4µs), contra ~2,0 milhões de eventos/s do append puro no journal em memória (~0,5µs). A diferença é geração de UUID e registro na tabela de ordens — não é o journal.

## O que estas medições NÃO incluem

⚠️ **I/O durável.** O journal medido é o `InMemoryEventStore`. Nenhum número aqui inclui escrita em disco ou em banco. Durabilidade real depende do Postgres e do disco, e precisa ser medida contra a stack implantada — é o papel do load generator (`/api/loadtest`), não do JMH, que roda em um único processo.

⚠️ **Logging.** Desligado, conforme a metodologia. Em produção o caminho quente emite cerca de 3 linhas INFO por par casado.

⚠️ **HTTP.** A camada web não é medida; os números são do engine e dos serviços, em processo.

⚠️ **Uma máquina, um dia.** Sem repetição em máquinas diferentes nem em horários diferentes. Os erros reportados cobrem a dispersão entre iterações, não a variação do ambiente.

## Como Reproduzir

Pré-requisito: Java 25. O perfil `benchmark` não roda em builds normais — ele adiciona `src/jmh/java` como fonte de teste, as dependências do JMH e o goal de execução.

```bash
# Motor, 1 thread
./mvnw -Pbenchmark test-compile exec:exec -Dexec.args="-classpath %classpath org.openjdk.jmh.Main -t 1 -p symbols=1 io.github.pedrolucas167.lastro.bench.MatchingEngineBenchmark"

# Motor, 8 threads, 1 e 8 símbolos
./mvnw -Pbenchmark test-compile exec:exec -Dexec.args="-classpath %classpath org.openjdk.jmh.Main -t 8 io.github.pedrolucas167.lastro.bench.MatchingEngineBenchmark"

# Caminho da aplicação
./mvnw -Pbenchmark test-compile exec:exec -Dexec.args="-classpath %classpath org.openjdk.jmh.Main io.github.pedrolucas167.lastro.bench.OrderPathBenchmark"
```

Parâmetros do JMH podem ser acrescentados livremente ao final de `exec.args` (por exemplo `-f 4` para mais forks, ou `-prof gc` para medir alocação).

## Próximos Passos

1. **Profiling** do caminho quente para confirmar se a `ConcurrentLinkedQueue` de trades é o ponto de serialização
2. **Custo do logging** medido como item separado, para saber quanto ele pesa em produção
3. **Journal durável**: medição com Postgres via load generator, incluindo latência de escrita e fsync
4. **Latência** além de throughput: p50/p95/p99 por operação, e não só ordens/segundo
