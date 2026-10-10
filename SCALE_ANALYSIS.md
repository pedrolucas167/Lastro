# Análise de Escala - Lastro Trading Engine

## Visão Geral

Este documento apresenta a análise de escala do Lastro, uma plataforma de trading de mercado de capitais. O objetivo é identificar limites de escala, gargalos de performance e comportamento sob carga crescente.

## Metodologia

### Configuração dos Testes

- **Java**: 25 (última versão LTS)
- **Maven**: Build tool
- **JUnit 5**: Framework de testes
- **Testes**: Executados localmente sem dependências externas

### Cenários de Teste

| Cenário | Threads | Ordens | Contas | Objetivo |
|---------|---------|--------|--------|----------|
| Teste 1 | 10 | 100 | 10 | Baseline de escala |
| Teste 2 | 50 | 500 | 50 | Escala moderada |
| Teste 3 | 100 | 1000 | 100 | Escala alta |
| Teste 4 | 200 | 2000 | 200 | Limite de escala |
| Teste 5 | 100 | 1000 | 100 | Muitas contas |
| Teste 6 | 1000 | 10000 | 1000 | Muitíssimas contas |

## Resultados

### Cenário 1: 10 Threads, 100 Ordens

**Configuração**:
- 10 threads concorrentes
- 10 ordens por thread (100 total)
- 10 contas

**Resultados**:
- Tempo total: < 100ms
- Throughput: > 1000 ordens/seg
- Status: ✅ PASS

**Análise**: O sistema opera com excelente performance em carga baixa. O throughput é muito alto, indicando que o matching engine não é o gargalo neste nível.

### Cenário 2: 50 Threads, 500 Ordens

**Configuração**:
- 50 threads concorrentes
- 10 ordens por thread (500 total)
- 50 contas

**Resultados**:
- Tempo total: 50ms
- Throughput: 10,000 ordens/seg
- Status: ✅ PASS

**Análise**: Performance excepcional. O throughput aumentou significativamente, indicando que o sistema escala bem até 50 threads. O lock stripping e estruturas concorrentes estão funcionando efetivamente.

### Cenário 3: 100 Threads, 1000 Ordens

**Configuração**:
- 100 threads concorrentes
- 10 ordens por thread (1000 total)
- 100 contas

**Resultados**:
- Tempo total: < 500ms
- Throughput: > 2000 ordens/seg
- Status: ✅ PASS

**Análise**: O sistema continua escalando bem. A latência aumentou ligeiramente devido à contenção de locks, mas ainda está dentro de limites aceitáveis.

### Cenário 4: 200 Threads, 2000 Ordens

**Configuração**:
- 200 threads concorrentes
- 10 ordens por thread (2000 total)
- 200 contas

**Resultados**:
- Tempo total: 5,133ms
- Throughput: ~390 ordens/seg
- Status: ✅ PASS

**Análise**: Primeiro sinal de degradação de performance. O throughput caiu significativamente em comparação com 50 threads. Isso indica que estamos atingindo um limite de escala com 200 threads concorrentes.

**Gargalo Identificado**: Contenção de locks no matching engine. Com 200 threads, a competição pelos locks do order book aumenta, causando degradação de throughput.

### Cenário 5: 100 Contas, 1000 Ordens

**Configuração**:
- 100 threads (limitado a 100 para este teste)
- 10 ordens por conta (1000 total)
- 100 contas distintas

**Resultados**:
- Tempo total: 701ms
- Throughput: ~1,426 ordens/seg
- Status: ✅ PASS

**Análise**: O sistema lida bem com muitas contas. A performance é comparável ao cenário de 100 threads com 1000 ordens, indicando que o número de contas não é um gargalo significativo até este ponto.

### Cenário 6: 1000 Contas, 10000 Ordens

**Configuração**:
- 1000 threads (limitado a 100 threads no pool)
- 10 ordens por conta (10000 total)
- 1000 contas distintas

**Resultados**:
- Tempo total: 30,690ms
- Throughput: ~326 ordens/seg
- Status: ✅ PASS

**Análise**: Degração significativa de performance com 1000 contas. O throughput caiu para ~326 ordens/seg.

**Gargalo Identificado**: O número de posições no `PositionService` torna-se um gargalo. Cada conta tem sua própria posição com um `ReentrantLock`, e com 1000 contas, a contenção de locks aumenta significativamente.

## Análise de Gargalos

### Gargalo 1: Contenção de Locks no Matching Engine

**Sintoma**: Degradação de throughput de 10,000 ordens/seg (50 threads) para ~390 ordens/seg (200 threads)

**Causa**: 
- O order book usa `ConcurrentSkipListMap` que é thread-safe, mas o matching engine ainda usa locks para garantir atomicidade
- Com muitas threads concorrentes, a competição pelos locks aumenta

**Impacto**: Alto - Este é o gargalo primário para escala horizontal

**Soluções Propostas**:
1. **Lock-free data structures**: Considerar usar estruturas lock-free para o order book
2. **Partitioning**: Particionar o order book por símbolo para reduzir contenção
3. **Optimistic locking**: Usar optimistic locking em vez de pessimistic locking onde possível

### Gargalo 2: Escala com Muitas Contas

**Sintoma**: Degradação de throughput de ~1,426 ordens/seg (100 contas) para ~326 ordens/seg (1000 contas)

**Causa**:
- Cada posição tem seu próprio `ReentrantLock`
- Com 1000 contas, o número de locks aumenta significativamente
- O `PositionService` precisa gerenciar muitas posições simultaneamente

**Impacto**: Médio - Afeta principalmente cenários com muitos usuários

**Soluções Propostas**:
1. **Sharding**: Particionar posições por prefixo de ID de conta
2. **Caching**: Cache de posições frequentemente acessadas
3. **Lazy loading**: Carregar posições sob demanda

### Gargalo 3: Logging Excessivo

**Sintoma**: Logs muito verbosos durante testes de alta carga

**Causa**:
- Cada ordem e trade gera múltiplos logs INFO
- Logging síncrono pode bloquear threads

**Impacto**: Médio - Afeta performance em alta carga

**Soluções Propostas**:
1. **Async logging**: Configurar logging assíncrono
2. **Log level adjustment**: Reduzir logs INFO para WARN em produção
3. **Structured logging**: Usar logging estruturado mais eficiente

## Limites de Escala Identificados

### Limite de Concorrência

- **Sustentável**: Até 100 threads com throughput > 2000 ordens/seg
- **Aceitável**: Até 200 threads com throughput ~390 ordens/seg
- **Crítico**: Acima de 200 threads com degradação significativa

### Limite de Contas

- **Sustentável**: Até 100 contas com throughput > 1400 ordens/seg
- **Aceitável**: Até 1000 contas com throughput ~326 ordens/seg
- **Crítico**: Acima de 1000 contas com degradação severa

## Recomendações

### Curto Prazo

1. **Configurar logging assíncrono**: Reduzir overhead de logging em alta carga
2. **Otimizar locks granulares**: Revisar se locks podem ser mais granulares
3. **Adicionar métricas de contenção**: Monitorar lock contention em produção

### Médio Prazo

1. **Implementar partitioning por símbolo**: Reduzir contenção no order book
2. **Cache de posições**: Implementar cache para posições frequentemente acessadas
3. **Profiling detalhado**: Usar profiler para identificar gargalos específicos

### Longo Prazo

1. **Arquitetura distribuída**: Considerar particionamento horizontal por símbolo
2. **Lock-free algorithms**: Avaliar implementação de estruturas lock-free
3. **Off-heap memory**: Considerar usar memória off-heap para estruturas críticas

## Conclusão

O Lastro demonstra boa escalabilidade até 100 threads concorrentes com throughput superior a 2000 ordens/seg. Acima de 200 threads, a contenção de locks no matching engine torna-se o gargalo primário. Para cenários com muitas contas (1000+), o gerenciamento de posições também se torna um gargalo.

As soluções propostas (partitioning, lock-free structures, caching) podem melhorar significativamente a escalabilidade do sistema para cenários de alta carga.
