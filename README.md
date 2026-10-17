# Lastro

Plataforma de trading de mercado de capitais para testar concorrência e escalabilidade em matching engines, construída com rigor de nível institucional.

## Filosofia

O Lastro é uma plataforma de trading de mercado de capitais estruturada como um sistema real de engenharia de software:

- **Arquitetura simples e clara**: Gerador de carga separado da API Spring Boot
- **Métricas sistemáticas**: Throughput, latência p95, uso de CPU documentados em cada experimento
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
- **Idempotency Service**: Garante idempotência em operações de ordens
- **Actuator**: Health checks e métricas de produção (Prometheus)
- **OpenAPI**: Documentação automática da API em Swagger UI

## Estrutura do Projeto

```
src/main/java/org/example/lastro/
├── api/              # Controllers REST
├── domain/           # Domínio de negócio (Order, OrderBook, Trade, Position)
├── service/          # Lógica de negócio
├── loadgenerator/    # Gerador de carga separado
├── metrics/          # Coleta de métricas
├── dto/              # Data Transfer Objects
├── exception/        # Exceções customizadas
├── config/           # Configurações (Logging, OpenAPI)
└── LastroApplication.java

src/test/java/org/example/lastro/
├── domain/           # Testes de domínio
├── service/          # Testes de services
└── concurrency/      # Testes de concorrência
```

## Mercado de Capitais

### Funcionalidades

- **Ordens**: Buy/Sell, Limit/Market, Good-Till-Cancelled
- **Order Book**: Book de ofertas com price-time priority
- **Matching Engine**: Matching de ordens com FIFO por preço
- **Trades**: Execuções com preço e quantidade
- **Posições**: Tracking de posições e custódia
- **Regulação**: Validações conforme CVM/SEC

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

Para detalhes completos sobre os testes de concorrência, resultados e métricas, consulte o documento [BASELINE_CONCURRENCY.md](./BASELINE_CONCURRENCY.md).

**Resultados do Baseline**:
- 11/11 testes de concorrência passando (100%)
- 1,460 operações processadas sem erros
- Throughput: ~77 ordens/segundo
- Latência média: <10ms por operação
- Zero deadlocks detectados

#### Análise de Escala

Para detalhes completos sobre testes de escala, limites identificados e gargalos, consulte o documento [SCALE_ANALYSIS.md](./SCALE_ANALYSIS.md).

**Resultados da Análise de Escala**:
- Escala sustentável: até 100 threads com throughput > 2000 ordens/seg
- Gargalo primário: contenção de locks no matching engine acima de 200 threads
- Gargalo secundário: gerenciamento de posições com 1000+ contas
- Throughput máximo observado: 10,000 ordens/seg (50 threads)

## Como Executar

### Pré-requisitos

- Java 25
- Maven 3.x

### Iniciar a Aplicação

```bash
./mvnw spring-boot:run
```

A API estará disponível em `http://localhost:8080`

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

MIT
