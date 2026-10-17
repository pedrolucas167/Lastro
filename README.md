# Lastro

[![CI](https://github.com/pedrolucas167/Lastro/actions/workflows/ci.yml/badge.svg)](https://github.com/pedrolucas167/Lastro/actions/workflows/ci.yml)
[![Docker](https://github.com/pedrolucas167/Lastro/actions/workflows/docker.yml/badge.svg)](https://github.com/pedrolucas167/Lastro/actions/workflows/docker.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)

> **Status: experimental (v0.x).** O núcleo de matching é correto sob concorrência e durável via journal + replay, mas isto **não é production-ready**: a API ainda não tem autenticação, e não há conectividade de mercado (feed/FIX) nem clearing/settlement. Use por sua conta e risco.

Plataforma de trading de mercado de capitais para testar concorrência e escalabilidade em matching engines, construída com rigor de nível institucional.

## Filosofia

O Lastro é uma plataforma de trading de mercado de capitais estruturada como um sistema real de engenharia de software:

- **Arquitetura simples e clara**: Gerador de carga separado da API Spring Boot
- **Métricas instrumentadas**: throughput, latência p50/p95/p99 e uso de CPU coletados pelo próprio sistema (`/actuator/prometheus`, `/api/loadtest/metrics`)
- **Sem over-engineering**: Nada de microserviços ou Kafka no Day 1
- **Provar antes de escalar**: Primeiro provar concorrência correta no matching engine, depois descobrir o limite de escala
- **Rigor de engenharia**: Testes abrangentes, logging estruturado, validações robustas, idempotência, CI/CD
- **Foco em Mercado de Capitais**: Ordens, book de ofertas, price-time priority, matching engine, regulação

## Arquitetura

```
┌─────────────────┐         ┌─────────────────┐
│  Load Generator │────────▶│   Spring Boot   │
│   (Separado)    │  HTTP   │      API        │
└─────────────────┘         └─────────────────┘
                                    │
                                    ▼
                           ┌─────────────────┐
                           │ Metrics Collector│
                           │  (CPU, Memória)  │
                           └─────────────────┘
```

### Componentes

- **API Spring Boot**: Gerencia ordens, book de ofertas e matching engine
- **Load Generator**: Cliente HTTP que gera carga controlada de ordens
- **Metrics Collector**: Coleta métricas de sistema durante os testes
- **Order Book**: Book de ofertas com price-time priority
- **Matching Engine**: Engine de matching de ordens buy/sell
- **Idempotency Service**: Garante idempotência em transferências entre contas (janela de 24h)
- **Event Journal**: Persistência append-only de eventos de domínio, com replay no startup
- **Actuator**: Health checks e métricas de produção (Prometheus)
- **OpenAPI**: Documentação automática da API em Swagger UI

## Estrutura do Projeto

```
src/main/java/io/github/pedrolucas167/lastro/
├── api/              # Controllers REST
├── domain/           # Domínio de negócio (Order, OrderBook, Trade, Position)
├── service/          # Lógica de negócio
├── loadgenerator/    # Gerador de carga separado
├── metrics/          # Coleta de métricas
├── journal/          # Journal append-only de eventos (persistência + auditoria)
├── dto/              # Data Transfer Objects
├── exception/        # Exceções customizadas
├── config/           # Configurações (Logging, OpenAPI)
└── LastroApplication.java

src/test/java/io/github/pedrolucas167/lastro/
├── domain/           # Testes de domínio
├── service/          # Testes de services
└── concurrency/      # Testes de concorrência
```

## Mercado de Capitais

### Funcionalidades

- **Ordens**: Buy/Sell, Limit/Market, time-in-force GTC/IOC/FOK e cancel-replace (amend)
- **Order Book**: Book de ofertas com price-time priority
- **Matching Engine**: Matching de ordens com FIFO por preço
- **Trades**: Execuções com preço e quantidade
- **Posições**: Tracking de posições e PnL por conta/símbolo
- **Regulação**: Validações de pré-negociação (janela de negociação, bandas de preço, limites de quantidade/preço/valor e formato do símbolo)

### Concorrência

#### Estratégia

- **ReentrantLock** em cada `Position` para garantir atomicidade
- **ConcurrentSkipListMap** para order book thread-safe
- **Lock stripping** para matching engine de alta performance

#### Garantias

- Ordens são processadas em ordem de chegada por preço
- Matching é atômico por par de ordens
- Sem inconsistências nas posições sob concorrência

#### Baseline de Concorrência

Os 11 testes de concorrência (5 de trading, 6 de contas) validam que todas as operações completam sob carga concorrente, com contagem exata e sem deadlock. Garantias e cenários em [BASELINE_CONCURRENCY.md](./BASELINE_CONCURRENCY.md).

#### Desempenho

Medido com JMH, com warmup, forks e múltiplas iterações:

- **Motor de matching**: ~818.000 ordens/segundo em uma thread (em memória, sem journal e sem logging)
- **Caminho da API** (`createOrder`, com journal em memória): ~423.000 ordens/segundo
- Adicionar threads **não** aumenta o throughput: 8 threads somam ~643.000 ordens/segundo, mesmo em símbolos distintos

Metodologia, ambiente, comandos de reprodução e limitações — incluindo o fato de nenhum número incluir I/O durável — em [PERFORMANCE.md](./PERFORMANCE.md).

#### Testes de Escala

Seis cenários, de 10 a 1.000 threads e até 20.000 ordens, validam que o sistema completa sob carga concorrente. Eles medem corretude, não desempenho — detalhes em [SCALE_ANALYSIS.md](./SCALE_ANALYSIS.md).

## Como Executar

### Pré-requisitos

- Java 25
- Maven 3.x
- Docker (opcional: para Postgres via `docker compose`; sem ele a aplicação usa um H2 embutido em memória)

### Iniciar a Aplicação

```bash
./mvnw spring-boot:run
```

A API estará disponível em `http://localhost:8080`

### Persistência

Cada mudança de estado — movimento de dinheiro (conta aberta, crédito, débito, transferência) e atividade do engine (ordens enviadas, canceladas, substituídas e trades executados) — é registrada como um **evento imutável** em um **journal append-only**. No startup, o estado em memória é reconstruído a partir do journal: as contas e transações são reaplicadas, e o engine **reexecuta os comandos na mesma ordem**, restaurando o book (com prioridade preço-tempo), as posições e os trades — os dados sobrevivem a reinícios e o próprio journal serve como trilha de auditoria.

- **Desenvolvimento/testes**: H2 embutido em memória, sem configuração.
- **Produção/demo**: Postgres via Compose:

```bash
docker compose up --build
```

O Compose sobe o Postgres, aponta a aplicação para ele (`SPRING_DATASOURCE_*`) e aplica as migrações com Flyway na subida.

Para usar um Postgres próprio, defina `spring.datasource.url`, `spring.datasource.username` e `spring.datasource.password`.

### Executar Load Test

```bash
curl -X POST "http://localhost:8080/api/loadtest/run?numAccounts=10&numTransfers=1000&concurrency=10&metricsIntervalMs=100"
```

Parâmetros:
- `numAccounts`: Número de contas a criar (padrão: 10)
- `numTransfers`: Número de transferências a executar (padrão: 1000)
- `concurrency`: Nível de concorrência (padrão: 10)
- `metricsIntervalMs`: Intervalo de coleta de métricas em ms (padrão: 100)

### Ver Métricas

```bash
curl http://localhost:8080/api/loadtest/metrics
```

## API Endpoints

### Trading

- `POST /api/trading/orders` - Criar ordem (body: `{"symbol": "PETR4", "side": "BUY", "type": "LIMIT", "price": 30.50, "quantity": 100, "accountId": "acc-1", "timeInForce": "GTC"}`; `timeInForce` opcional: `GTC` (padrão), `IOC`, `FOK`)
- `POST /api/trading/orders/{orderId}/replace?symbol=PETR4` - Substituir preço/quantidade de uma ordem em repouso (body: `{"price": 10.50, "quantity": 200}`; campos opcionais)
- `POST /api/trading/orders/{orderId}/cancel` - Cancelar ordem
- `GET /api/trading/orderbook/{symbol}` - Obter book de ofertas
- `GET /api/trading/trades` - Obter todos os trades
- `GET /api/trading/trades?symbol=XXX` - Obter trades por símbolo
- `GET /api/trading/positions/{accountId}/{symbol}` - Obter posição
- `GET /api/trading/positions/{accountId}/{symbol}/pnl?currentPrice=XX` - Obter PnL

### Contas

- `POST /api/accounts` - Criar conta (body: `{"initialBalance": 1000.00}`)
- `GET /api/accounts/{id}` - Buscar conta
- `POST /api/accounts/{id}/credit` - Creditar valor (body: `{"amount": 100.00}`)
- `POST /api/accounts/{id}/debit` - Debitar valor (body: `{"amount": 100.00}`)

### Transações

- `POST /api/transactions/transfer` - Transferir entre contas (body: `{"fromAccountId": "x", "toAccountId": "y", "amount": 100.00, "idempotencyKey": "optional"}`)
- `GET /api/transactions/{id}` - Buscar transação

### Load Tests

- `POST /api/loadtest/run` - Executar load test
- `GET /api/loadtest/metrics` - Obter métricas
- `POST /api/loadtest/metrics/clear` - Limpar métricas

### Health & Metrics

- `GET /actuator/health` - Health check
- `GET /actuator/metrics` - Métricas JVM
- `GET /actuator/prometheus` - Métricas Prometheus
- `GET /swagger-ui.html` - Documentação Swagger UI
- `GET /v3/api-docs` - Especificação OpenAPI

## Roadmap

### Fase 1: Corretude (Atual)
- [x] Implementar domínio com locks
- [x] Criar load generator separado
- [x] Implementar coleta de métricas
- [x] Executar baseline de concorrência
- [x] Validar corretude sob concorrência

### Fase 2: Escala
- [x] Identificar limite de escala
- [x] Testar diferentes níveis de concorrência
- [x] Testar com diferentes números de contas
- [x] Documentar pontos de gargalo

### Fase 3: Otimização (se necessário)
- [ ] Avaliar necessidade de cache
- [ ] Avaliar necessidade de pool de conexões
- [ ] Considerar arquitetura distribuída (apenas se necessário)

## Licença

[Apache License 2.0](./LICENSE) © 2026 Pedro Lucas Marques
