# Lastro

[![CI](https://github.com/pedrolucas167/Lastro/actions/workflows/ci.yml/badge.svg)](https://github.com/pedrolucas167/Lastro/actions/workflows/ci.yml)
[![Docker](https://github.com/pedrolucas167/Lastro/actions/workflows/docker.yml/badge.svg)](https://github.com/pedrolucas167/Lastro/actions/workflows/docker.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)

> **Status: experimental (v0.x).** O núcleo de matching é correto sob concorrência e durável via journal + replay. A API exige autenticação (JWT do Keycloak), autoriza por papel na rota e por titular na conta — o titular só alcança as contas que tem, enquanto operador e M2M são o balcão e movimentam qualquer conta — e cada evento do journal grava quem o causou. Isto **não é production-ready**: não há limites por titular nem conectividade de mercado (feed/FIX); a liquidação é D+0 e no próprio livro, sem câmara, netting ou D+n. Use por sua conta e risco.

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
- **Chave de idempotência**: transferências e ordens guardam a chave do cliente no journal — o reenvio devolve o que já foi aceito sem repetir o efeito, e a garantia sobrevive ao reinício
- **Event Journal**: Persistência append-only dos fatos de domínio — só o comando que aconteceu — com replay no startup
- **Actuator**: Health checks e métricas de produção (Prometheus)
- **OpenAPI**: Documentação automática da API em Swagger UI
- **Keycloak**: Provedor de identidade que emite os JWTs; a API só os valida (resource server)

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
├── config/           # Configurações (Logging, OpenAPI, Segurança)
└── LastroApplication.java

src/test/java/io/github/pedrolucas167/lastro/
├── domain/           # Testes de domínio
├── service/          # Testes de services
└── concurrency/      # Testes de concorrência

deploy/keycloak/      # Realm de desenvolvimento (papéis, clientes e usuário de demo)
```

## Mercado de Capitais

### Funcionalidades

- **Ordens**: Buy/Sell, Limit/Market, time-in-force GTC/IOC/FOK e cancel-replace (amend). Ordem a mercado é imediata — o que não executa é cancelado — e só ordem limitada GTC repousa no book
- **Order Book**: Book de ofertas com price-time priority
- **Matching Engine**: Matching de ordens com FIFO por preço
- **Pré-trade**: a compra exige caixa disponível (saldo menos o que já está bloqueado por ordens em aberto) e a venda exige a ação na posição — não há posição descoberta
- **Liquidação**: o trade move o caixa no ato (D+0) — o comprador paga e o vendedor recebe, sem evento de caixa próprio
- **Custódia**: depósito e transferência de ações entre contas, com o custo médio acompanhando a ação; é por onde a ação entra no sistema
- **Contabilidade**: livro-razão de partidas dobradas derivado do journal — todo movimento de dinheiro tem contrapartida, e o saldo do cliente é a projeção do código dele no plano de contas
- **Trades**: Execuções com preço e quantidade
- **Posições**: Tracking de posições e PnL por conta/símbolo
- **Regulação**: Validações de pré-negociação (janela de negociação, bandas de preço, limites de quantidade/preço/valor e formato do símbolo)
- **Circuit breaker**: Halt/resume por símbolo, durável no journal, que pausa o trading sem cancelar o book
- **Controle de acesso**: JWT do Keycloak e autorização por papel (M2M × operador) por rota

### Lastro (pré-trade, liquidação e custódia)

Nenhuma ordem chega ao engine sem lastro, e o lastro é o que a ordem **ainda pode executar**:

- **Compra** bloqueia `preço-limite × quantidade restante` do disponível da conta — ordem a mercado bloqueia o teto da banda de preço. Sem caixa que cubra, a ordem é recusada com `403` **antes** de virar fato: não existe ordem sem dinheiro no book.
- **Venda** bloqueia as ações que a ordem pode executar. Vender ação que a conta não tem é recusado com `403` — não há posição descoberta.
- **Executou, pagou**: o trade liquida na hora (D+0). O comprador paga `preço × quantidade` e o vendedor recebe no mesmo instante; não há evento de caixa separado porque o `TradeExecuted` carrega tudo o que a liquidação precisa.
- **Não executou, voltou**: cancelar (ou não repousar, no caso de ordem a mercado) devolve o lastro do que sobrou. O bloqueio **não** é evento do journal — é consequência das ordens em aberto, como a posição, e o replay o reconstrói somando as ordens ainda abertas.

A ação entra no sistema pela custódia (`POST /api/trading/positions/deposit`): o operador registra a carteira que o cliente traz de fora, no custo médio informado. Dali em diante ela se move entre contas por transferência e por trade — sem isso, uma conta nova não teria o que vender.

### Contabilidade (partidas dobradas)

O caixa deixa de ser um número por conta: é um livro-razão de partidas dobradas, e o saldo do cliente é a projeção do código dele no plano de contas. Todo movimento tem contrapartida.

| Código | Natureza | Papel |
|---|---|---|
| `ASSETS:SETTLEMENT` | ativo | o caixa da corretora na liquidante |
| `LIABILITIES:CLIENT:<accountId>` | passivo | o caixa do cliente — o que a corretora deve a ele |

| Fato | Débito | Crédito |
|---|---|---|
| abertura com saldo, crédito | `ASSETS:SETTLEMENT` | `LIABILITIES:CLIENT:<id>` |
| débito | `LIABILITIES:CLIENT:<id>` | `ASSETS:SETTLEMENT` |
| transferência | `LIABILITIES:CLIENT:<origem>` | `LIABILITIES:CLIENT:<destino>` |
| trade | `LIABILITIES:CLIENT:<comprador>` | `LIABILITIES:CLIENT:<vendedor>` |

O livro é **derivado do journal**, como o bloqueio deriva das ordens em aberto: o journal guarda o fato, o livro guarda a contabilidade dele, e o mapeamento mora em um lugar só. Não há evento novo nem migração — o journal de qualquer versão anterior ganha partidas dobradas no replay.

Duas provas que o sistema cobra o tempo todo:

- **Toda transação fecha**: débitos = créditos, e o construtor de `LedgerTransaction` recusa o contrário.
- **A soma dos saldos dos clientes é exatamente o caixa na liquidante**: ninguém inventa dinheiro, e transferência ou trade não criam nem destroem caixa — só mudam de quem é.

Na subida, o replay reconstrói contas e livro e confere conta a conta: uma divergência aborta o startup em vez de deixar o processo servir saldo errado. O extrato (`GET /api/accounts/{id}/statement`) sai do livro — entre clientes, a contraparte vem sem o id, para o extrato de um não revelar a conta do outro —, e o balanço do livro (`GET /api/ledger/trial-balance`) é ato de operador.

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
- Docker (necessário para o Keycloak, que emite os tokens; opcional para o Postgres — sem ele a aplicação usa um H2 embutido em memória)

### Iniciar a Aplicação

```bash
./mvnw spring-boot:run
```

A API estará disponível em `http://localhost:8080`

### Autenticação e autorização

A API é um **resource server OAuth2**: toda rota exige um JWT emitido pelo Keycloak, exceto `GET /actuator/health`, que é público para o HEALTHCHECK do container. A aplicação **não é** o provedor de identidade — só valida tokens (JWKS do Keycloak, emissor e **audiência** verificados).

O realm de desenvolvimento (`lastro`, em `deploy/keycloak/lastro-realm.json`) traz três papéis, e cada rota exige um deles:

| Rota | `lastro-service` (M2M) | `lastro-operator` | `lastro-holder` (titular) |
|------|:---:|:---:|:---:|
| `POST /api/accounts` | ✓ | ✓ | ✓ (só em nome próprio) |
| `POST /api/transactions/transfer` | ✓ | ✓ | ✓ (só a partir de conta própria) |
| `GET /api/accounts/{id}` | – | ✓ (qualquer) | ✓ (só a própria) |
| `GET /api/accounts/mine` (as contas do próprio token) | – | ✓ | ✓ |
| `GET /api/transactions/{id}` | – | ✓ (qualquer) | ✓ (se tocar conta própria) |
| `GET /api/clients/me` (o próprio cadastro) | – | ✓ | ✓ |
| `POST /api/clients`, `/suspend`, `/reactivate`, `GET /api/clients` (cadastro e status) | – | ✓ | – |
| `POST /api/accounts/{id}/holder` (definir titular) | – | ✓ | – |
| `POST /api/accounts/{id}/credit`, `/debit` (movimenta saldo; o débito só alcança o disponível) | – | ✓ | – |
| `POST /api/trading/orders`, `/replace`, `/cancel` | – | ✓ | – |
| `POST /api/trading/positions/deposit`, `/positions/transfer` (custódia) | – | ✓ | – |
| `GET /api/trading/**` (book, trades, posições, PnL) | – | ✓ | – |
| `POST /api/market/{symbol}/halt`, `/resume`, `GET /api/market/halted` | – | ✓ | – |
| `/api/loadtest/**` (disparar e consultar carga) | – | ✓ | – |
| `/actuator/metrics`, `/prometheus`, `/info` | – | ✓ | – |
| `/v3/api-docs`, `/swagger-ui` | ✓ | ✓ | ✓ |
| `GET /actuator/health` | público | público | público |

**Audiência.** Todo token do realm carrega `aud: lastro-api` (um mapper de audiência em cada cliente), e a API exige essa audiência: um token emitido pelo mesmo Keycloak para outro cliente ou outro sistema é recusado com `401`, mesmo com o emissor certo. O papel também é do **cliente**, não do usuário: cada cliente autoriza só o seu papel (`fullScopeAllowed: false` com mapeamento explícito), então quem entra pelo cliente do titular não leva o papel de operador.

O papel M2M faz apenas o que o load generator precisa — abrir conta e transferir. Todo o resto, inclusive movimentar saldo e acionar halt, é ato de operador: um cliente de serviço comprometido não movimenta dinheiro nem opera o mercado.

**Titular da conta.** O titular é o `sub` do Keycloak — a identidade que responde pela conta, gravada no journal junto com a abertura. O operador pode abrir uma conta em nome de alguém (`{"initialBalance": 100.00, "holder": "<sub>"}`) ou dar titular a uma conta que ainda não tem (`POST /api/accounts/{id}/holder`), mas o vínculo **é definido uma vez**: não existe troca de titularidade por reatribuição (a resposta é `409`). Quem tem o papel `lastro-holder` só enxerga e só tira dinheiro das contas que tem; conta sem titular é alcançável apenas pelo operador e pelo M2M. Movimentar saldo de terceiro (crédito/débito), operar o mercado e definir titularidade continuam sendo atos de operador — é o balcão, e é o que a trilha de auditoria cobre.

**Cliente e suspensão.** O cliente é o cadastro em volta do `sub` do titular — nome, status e, depois, limites —, e não muda a conta: o vínculo continua sendo o `sub`, então o journal de 0.1 a 0.6 continua legível e nenhuma conta migra. Suspender não apaga nada nem confisca: o cliente suspenso não abre ordem nem tira dinheiro por transferência, mas continua recebendo crédito e enxergando o que é dele, e o motivo aparece em `GET /api/clients/me`. Titular sem cadastro de cliente opera como antes — o passado não é reescrito.

```bash
docker compose up -d keycloak   # Keycloak em http://localhost:8090 (admin/admin)
```

Token de operador (senha `operador`) e uma chamada autenticada:

```bash
TOKEN=$(curl -s -X POST "http://localhost:8090/realms/lastro/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=lastro-operator" \
  -d "username=operador" -d "password=operador" | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/trading/trades
```

Para o token de serviço (client credentials), troque o grant:

```bash
curl -s -X POST "http://localhost:8090/realms/lastro/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" -d "client_id=lastro-service" \
  -d "client_secret=lastro-service-secret"
```

O titular da conta (`cliente`, senha `cliente`) usa o mesmo grant do operador, no cliente `lastro-holder`:

```bash
TOKEN_HOLDER=$(curl -s -X POST "http://localhost:8090/realms/lastro/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=lastro-holder" \
  -d "username=cliente" -d "password=cliente" | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN_HOLDER" http://localhost:8080/api/accounts/mine
```

Sem token a API responde `401`; com um token válido sem o papel exigido, `403` (e um token do mesmo emissor sem a audiência também é `401` — ele não é destinado a esta API). As credenciais acima são de desenvolvimento — em produção, use um Keycloak próprio e outro segredo de cliente. E como o segredo do cliente M2M está no repositório, a aplicação **recusa subir** se ele for usado com um emissor que não seja localhost (`lastro.security.reject-dev-credentials=false` troca o erro por um aviso).

Para apontar para outro Keycloak, defina `spring.security.oauth2.resourceserver.jwt.issuer-uri`, `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` e `spring.security.oauth2.resourceserver.jwt.audiences` (ou as variáveis de ambiente equivalentes). O `issuer-uri` é só validado, o JWKS é buscado sob demanda — a aplicação sobe mesmo com o Keycloak fora do ar. A audiência é obrigatória: no seu Keycloak, emita a audiência escolhida no `aud` (um mapper de audiência no cliente) e liste-a aqui; sem ela a API recusa todo token, de propósito.

No Swagger UI, use **Authorize** e cole o token.

### Persistência

Cada mudança de estado — movimento de dinheiro (conta aberta, titular definido, crédito, débito, transferência), atividade do engine (ordens enviadas, canceladas, substituídas e trades executados) e controle de mercado (halt/resume por símbolo) — é registrada como um **evento imutável** em um **journal append-only**. No startup, o estado em memória é reconstruído a partir do journal: as contas e transações são reaplicadas, o engine **reexecuta os comandos na mesma ordem** (restaurando o book com prioridade preço-tempo, as posições e os trades) e os símbolos pausados voltam pausados — os dados sobrevivem a reinícios e o próprio journal serve como trilha de auditoria. O journal só grava o comando que **aconteceu**: cancelamento ou amend que o engine recusou não vira evento, e repetir o cancelamento não gera um segundo fato.

Cada evento grava **quem o causou**: o `sub` do token e os papéis que a identidade carregava naquele momento. A autoridade fica no fato, não na configuração de hoje — revogar um papel amanhã não reescreve quem podia o quê ontem. Eventos gravados antes disso (journal anterior a este campo) replayam com o ator vazio; o replay não depende dele.

- **Desenvolvimento/testes**: H2 embutido em memória, sem configuração.
- **Produção/demo**: Postgres via Compose:

```bash
docker compose up --build
```

O Compose sobe o Postgres, aponta a aplicação para ele (`SPRING_DATASOURCE_*`) e aplica as migrações com Flyway na subida.

Para usar um Postgres próprio, defina `spring.datasource.url`, `spring.datasource.username` e `spring.datasource.password`.

### Executar Load Test

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/loadtest/run?numAccounts=10&numTransfers=1000&concurrency=10&metricsIntervalMs=100"
```

Parâmetros:
- `numAccounts`: Número de contas a criar (padrão: 10)
- `numTransfers`: Número de transferências a executar (padrão: 1000)
- `concurrency`: Nível de concorrência (padrão: 10)
- `metricsIntervalMs`: Intervalo de coleta de métricas em ms (padrão: 100)

O disparo é ato de operador (o `$TOKEN` acima). O gerador, internamente, chama a API autenticado como cliente M2M.

### Ver Métricas

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/loadtest/metrics
```

## API Endpoints

Todas as rotas abaixo exigem `Authorization: Bearer <token>` com o papel adequado (ver a matriz em [Autenticação e autorização](#autenticação-e-autorização)); a única exceção é `GET /actuator/health`.

### Trading

- `POST /api/trading/orders` - Criar ordem (body: `{"symbol": "PETR4", "side": "BUY", "type": "LIMIT", "price": 30.50, "quantity": 100, "accountId": "acc-1", "timeInForce": "GTC"}`; `timeInForce` opcional: `GTC` (padrão), `IOC`, `FOK`)
  - A ordem exige lastro: a compra precisa de caixa disponível na conta e a venda precisa da ação na posição. Sem lastro a resposta é `403` e **nada** entra no journal — ordem recusada não vira fato.
  - Cabeçalho opcional `Idempotency-Key`: reenviar a mesma ordem com a mesma chave devolve a ordem que já foi aceita, sem criar outra nem gerar outro trade. A chave fica no journal e volta no replay, então o reenvio continua seguro depois de um reinício; a mesma chave para outra ordem responde `409`.
- `POST /api/trading/orders/{orderId}/replace?symbol=PETR4` - Substituir preço/quantidade de uma ordem em repouso (body: `{"price": 10.50, "quantity": 200}`; campos opcionais). Amend recusado pelo engine (ordem fora de repouso ou quantidade abaixo da já executada) responde `404` **sem** deixar `OrderReplaced` no journal.
- `POST /api/trading/orders/{orderId}/cancel?symbol=PETR4` - Cancelar ordem em repouso: `200` quando cancela, e também `200` no reenvio de uma ordem já cancelada — o journal já tem o cancelamento, então não entra evento novo. Ordem desconhecida é `404` e ordem preenchida é `409`; nos dois casos, nada é gravado.
- `GET /api/trading/orderbook/{symbol}` - Obter book de ofertas
- `GET /api/trading/trades` - Obter todos os trades
- `GET /api/trading/trades?symbol=XXX` - Obter trades por símbolo
- `GET /api/trading/positions/{accountId}/{symbol}` - Obter posição
- `GET /api/trading/positions/{accountId}/{symbol}/pnl?currentPrice=XX` - Obter PnL
- `POST /api/trading/positions/deposit` - Depositar ação numa conta, vinda de fora do sistema (body: `{"accountId": "acc-1", "symbol": "PETR4", "quantity": 100, "price": 30.50}`)
- `POST /api/trading/positions/transfer` - Transferir ação entre contas (body: `{"fromAccountId": "acc-1", "toAccountId": "acc-2", "symbol": "PETR4", "quantity": 100}`; não move ação prometida a uma venda em aberto)

### Clientes

- `POST /api/clients` - Registrar o cliente de um titular (body: `{"subject": "<sub do Keycloak>", "name": "Maria Silva"}`; ato de operador). Um cliente por `sub`, e o cadastro vira fato no journal — registrar o mesmo titular de novo responde `409`.
- `GET /api/clients/me` - O próprio cadastro, com o status e o motivo da suspensão. Quem ainda não tem cadastro recebe `404` — e continua operando como antes.
- `GET /api/clients` - Listar os clientes (ato de operador)
- `GET /api/clients/{id}` - Buscar cliente (ato de operador)
- `POST /api/clients/{id}/suspend` - Suspender o cliente (body opcional: `{"reason": "documentacao vencida"}`). Suspenso não abre ordem nem tira dinheiro por transferência; receber crédito continua valendo. Suspender duas vezes responde `200` e grava um fato só.
- `POST /api/clients/{id}/reactivate` - Reativar o cliente

### Contas

- `POST /api/accounts` - Criar conta (body: `{"initialBalance": 1000.00}`; o operador pode abrir em nome de alguém com `"holder": "<sub>"`)
- `GET /api/accounts/mine` - Listar as contas do próprio token
- `GET /api/accounts/{id}` - Buscar conta (a resposta traz `balance` e `available`, o saldo menos o bloqueado por ordens de compra em aberto)
- `POST /api/accounts/{id}/holder` - Definir o titular de uma conta sem titular (body: `{"holder": "<sub>"}`, ato de operador)
- `POST /api/accounts/{id}/credit` - Creditar valor (body: `{"amount": 100.00}`)
- `POST /api/accounts/{id}/debit` - Debitar valor (body: `{"amount": 100.00}`; só o disponível, sem tocar no que está prometido a uma ordem)
- `GET /api/accounts/{id}/statement` - Extrato do caixa: cada partida que tocou a conta (motivo, lado, valor, contraparte e o saldo depois dela), do fato mais antigo para o mais novo. O titular vê a própria conta; o operador vê qualquer uma.

### Livro-razão

- `GET /api/ledger/trial-balance` - Balanço do livro, ato de operador: `balanced`, os dois totais (débito e crédito), o que a corretora deve aos clientes e o caixa na liquidante, conta a conta.

### Transações

- `POST /api/transactions/transfer` - Transferir entre contas (body: `{"fromAccountId": "x", "toAccountId": "y", "amount": 100.00}`)
  - Cabeçalho opcional `Idempotency-Key`, com a mesma semântica da ordem: reenviar a mesma transferência com a mesma chave devolve a que já foi feita, sem movimentar o dinheiro de novo. A chave fica no journal e volta no replay; a mesma chave para outra transferência responde `409`. Transferência que falhou por saldo insuficiente não vira fato, então não prende a chave — o reenvio tenta de novo.
- `GET /api/transactions/{id}` - Buscar transação

### Mercado

- `POST /api/market/{symbol}/halt` - Pausar o trading de um símbolo (body opcional: `{"reason": "circuito de protecao"}`)
- `POST /api/market/{symbol}/resume` - Retomar o trading do símbolo
- `GET /api/market/halted` - Listar símbolos pausados e seus motivos

O halt bloqueia ordens novas e amend do símbolo, mas **não cancela** o que está no book — cancelar continua permitido para desmontar posições. O estado é durável: sobrevive a restart pelo journal.

### Load Tests

- `POST /api/loadtest/run` - Executar load test
- `GET /api/loadtest/metrics` - Obter métricas
- `POST /api/loadtest/metrics/clear` - Limpar métricas

### Health & Metrics

- `GET /actuator/health` - Health check (público; detalhes só para quem está autenticado)
- `GET /actuator/metrics` - Métricas JVM
- `GET /actuator/prometheus` - Métricas Prometheus
- `GET /swagger-ui.html` - Documentação Swagger UI (requer token: use **Authorize**)
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
