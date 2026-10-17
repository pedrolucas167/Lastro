# Lastro

**Matching engine de mercado de capitais em Java 25 + Spring Boot — simples, correto sob concorrência e durável.**

Ordens, book de ofertas com price-time priority, matching engine, posições, contas e transferências. Tudo coberto por testes, medido com números reais e persistido em um journal append-only que sobrevive a reinícios.

## Comece em 30 segundos

```bash
./mvnw spring-boot:run
```

API em `http://localhost:8080` · Swagger UI em `http://localhost:8080/swagger-ui.html`

```bash
# Gera carga: 10 contas, 1.000 transferências, 10 threads concorrentes
curl -X POST "http://localhost:8080/api/loadtest/run?numAccounts=10&numTransfers=1000&concurrency=10"

# Métricas coletadas (throughput, latência, CPU)
curl "http://localhost:8080/api/loadtest/metrics"
```

Não precisa configurar banco: por padrão roda com um H2 em memória. Para Postgres durável, `docker compose up --build`.

## O que tem dentro

| Área | O que faz |
|---|---|
| **Matching engine** | Ordens LIMIT e MARKET; matching FIFO por preço (price-time priority) |
| **Order book** | `ConcurrentSkipListMap` thread-safe: melhor bid/ask, spread e volume |
| **Trades e posições** | Execuções com preço/quantidade; posição, preço médio e PnL por conta |
| **Contas** | Saldo, crédito, débito e transferências com **idempotência** |
| **Persistência** | Journal append-only de eventos + replay no startup (durabilidade e auditoria) |
| **Observabilidade** | Actuator, métricas Prometheus, logs estruturados e Swagger/OpenAPI |

## Correção sob concorrência

Feito para provar que dá para ter um motor simples **e** correto sob carga:

- Locks por `Position`, book concorrente e matching atômico por par de ordens
- **11/11** testes de concorrência verdes · **1.460** operações sem erro · **0** deadlocks
- Baseline: ~**77 ordens/s**, latência média < 10 ms

Detalhes: [BASELINE_CONCURRENCY.md](./BASELINE_CONCURRENCY.md)

## Escala

O limite foi encontrado medindo, não por achismo:

- Escala sustentável até **100 threads** com **> 2.000 ordens/s**
- Pico de **10.000 ordens/s** (50 threads)
- Gargalo: contenção de locks no matching engine acima de 200 threads

Detalhes: [SCALE_ANALYSIS.md](./SCALE_ANALYSIS.md)

## Durabilidade

Cada movimento de dinheiro (conta aberta, crédito, débito, transferência) vira um **evento imutável** em um **journal append-only**. No startup, o estado é reconstruído a partir do journal — os dados sobrevivem a reinícios e o próprio journal é a **trilha de auditoria**.

- **Dev/testes**: H2 embutido em memória, sem configuração.
- **Produção**: Postgres via `docker compose up --build` (migrações com Flyway).

## Arquitetura

```
┌────────────────┐   HTTP   ┌───────────────────────────────────┐
│ Load Generator │─────────▶│          Spring Boot API          │
│   (separado)   │          │ api · service · domain · journal  │
└────────────────┘          └───────────────────────────────────┘
```

```
src/main/java/org/example/lastro/
├── api/            # Controllers REST
├── domain/         # Order, OrderBook, MatchingEngine, Trade, Position, Account
├── service/        # Regras de negócio e orquestração
├── journal/        # Eventos, EventStore e replay (persistência + auditoria)
├── loadgenerator/  # Gerador de carga separado da API
├── metrics/        # Coleta de métricas de sistema
├── dto/ · exception/ · config/
└── LastroApplication.java
```

## API

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/trading/orders` | Envia ordem |
| `POST` | `/api/trading/orders/{id}/cancel` | Cancela ordem |
| `GET` | `/api/trading/orderbook/{symbol}` | Book: melhor bid/ask, spread e volume |
| `GET` | `/api/trading/trades?symbol=` | Trades (todos ou por símbolo) |
| `GET` | `/api/trading/positions/{accountId}/{symbol}` | Posição e preço médio |
| `GET` | `/api/trading/positions/{accountId}/{symbol}/pnl?currentPrice=` | PnL |
| `POST` | `/api/accounts` | Cria conta |
| `GET` | `/api/accounts/{id}` | Consulta conta |
| `POST` | `/api/accounts/{id}/credit` · `/debit` | Crédito / débito |
| `POST` | `/api/transactions/transfer` | Transferência (`idempotencyKey` opcional) |
| `GET` | `/api/transactions/{id}` | Consulta transação |
| `POST` | `/api/loadtest/run` | Executa load test |
| `GET` | `/api/loadtest/metrics` | Métricas do load test |
| `GET` | `/actuator/health` · `/actuator/prometheus` | Health e métricas |

Documentação interativa em `/swagger-ui.html`. Exemplo de ordem:

```json
{ "symbol": "PETR4", "side": "BUY", "type": "LIMIT", "price": 30.50, "quantity": 100, "accountId": "<accountId>" }
```

## Testes

```bash
./mvnw test
```

Cobre domínio, serviços, concorrência, replay da persistência e um round-trip real em **Postgres via Testcontainers**. O CI roda em todo PR e no `main`; o workflow de Docker valida a imagem.

## Roadmap

Feito:

- [x] Matching engine com price-time priority e concorrência correta
- [x] Baseline de concorrência e análise de escala
- [x] Journal append-only + replay (contas e transações)

Próximo:

- [ ] Journal do engine (ordens, trades e posições)
- [ ] Autenticação e autorização (API keys)
- [ ] Trilha de auditoria imutável, idempotência de ordens e ledger de dupla entrada
- [ ] Risco pré-trade: limites de posição/exposição e kill switch
- [ ] Market data via WebSocket

## Licença

MIT
