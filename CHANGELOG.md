# Changelog

Todas as mudanças relevantes deste projeto são documentadas aqui.

O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o projeto adota [Versionamento Semântico](https://semver.org/lang/pt-BR/).

> ⚠️ **Experimental.** O núcleo de matching é correto sob concorrência e durável via journal + replay. A API exige autenticação (JWT do Keycloak), autoriza por papel na rota e por titular na conta, mas não há entidade de cliente (KYC), limites por titular, ator no journal nem conectividade de mercado (feed/FIX) e clearing. Não use em produção.

## [Unreleased]

## [0.3.0] - 2026-10-18

Terceira versão pública: vínculo entre conta e titular — o titular é o `sub` do Keycloak, o papel `lastro-holder` só enxerga e só movimenta as contas que tem, e a titularidade é um fato do journal. Inclui também a recusa de subida com credencial de desenvolvimento contra emissor não-local.

### Adicionado

- A aplicação recusa subir quando o segredo do cliente M2M de desenvolvimento (público no repositório) é usado com um emissor que não seja localhost — o realm de demonstração viraria uma porta aberta. `lastro.security.reject-dev-credentials=false` troca o erro por um aviso.
- Vínculo entre conta e titular: cada conta pode ter um titular (o `sub` do Keycloak) e o papel `lastro-holder` só enxerga e só tira dinheiro das contas que tem. O vínculo é um fato do journal (`AccountHolderAssigned`), então sobrevive ao replay, e é definido uma vez — reatribuir titularidade responde `409`.
- `GET /api/accounts/mine` (as contas do próprio token) e `POST /api/accounts/{id}/holder` (definir titular, ato de operador). O operador também pode abrir uma conta em nome de alguém passando `holder` no corpo de `POST /api/accounts`.
- Realm de desenvolvimento ganha o papel `lastro-holder`, o cliente `lastro-holder` e o usuário `cliente`/`cliente`.

### Alterado

- A versão do projeto no `pom.xml` acompanha a versão publicada (estava em `0.1.0` desde a primeira versão, enquanto as tags já iam em `v0.2.0`). Ela é a versão que a aplicação expõe — `GET /v3/api-docs` e `/actuator/info` — então agora ela e a tag coincidem.
- A resposta de conta (`AccountResponse`) passa a trazer o `holder`, e `GET /api/accounts/{id}` e `GET /api/transactions/{id}` passam a ser acessíveis a quem tem a conta — o operador continua vendo qualquer uma; quem não é o titular recebe `403`.
- Autenticado sem o papel exigido continua `403`; a tentativa de operar conta de terceiro também, mas o corpo da resposta diz que a conta é de outro titular.

## [0.2.0] - 2026-10-18

Segunda versão pública: autenticação, autorização por papel e o halt/circuit breaker durável. **Quebra de compatibilidade** para quem chamava a API da v0.1.0 sem token (ver Alterado).

### Adicionado

- Benchmark JMH para throughput do motor de matching e do caminho de aplicação, sob o perfil `-Pbenchmark`, com resultados e metodologia em `PERFORMANCE.md`.
- Autenticação na API: passa a ser um resource server OAuth2 que valida JWTs do Keycloak (JWKS sob demanda, emissor verificado) e exige token em todas as rotas, exceto `GET /actuator/health`. Os papéis de `realm_access.roles` viram authorities com prefixo `ROLE_`, prontas para autorizar por papel.
- Realm de desenvolvimento `lastro` (`deploy/keycloak/lastro-realm.json`) com os papéis `lastro-service` (M2M) e `lastro-operator`, e um cliente Keycloak para cada, subido pelo `compose.yaml`.
- Autorização por rota: cada endpoint passa a exigir o papel que ele pede. O cliente M2M (`lastro-service`) só abre conta e transfere — o que o load generator faz; movimentar saldo, operar ordens, disparar carga e acionar halt exigem `lastro-operator`.
- Halt/circuit breaker por símbolo (`POST /api/market/{symbol}/halt` e `/resume`, `GET /api/market/halted`): bloqueia ordens novas e amend do símbolo pausado, mantém as ordens em repouso canceláveis, e é **durável** — o halt é um evento do journal (`SymbolHalted`/`SymbolResumed`) reaplicado no replay, então um restart não religa o trading sozinho.

### Corrigido

- `ScaleTest` calculava o throughput com metade das ordens enviadas (cada iteração envia BUY e SELL) e incluía criação de pool de threads e shutdown no tempo medido.
- A especificação OpenAPI respondia `500`: o `springdoc-openapi` 2.3.0, compilado contra o Spring Framework 6, chamava um construtor de `ControllerAdviceBean` removido no Framework 7. Atualizado para `3.1.1`.
- A especificação OpenAPI declarava licença MIT, versão `1.0` e uma descrição de "teste de desempenho"; agora traz Apache-2.0, a versão do build e a descrição atual do projeto.
- O CHANGELOG da v0.1.0 anunciava "halt/circuit breaker" como entregue, mas o recurso nunca esteve no `main` — o pull request que o trazia foi mergeado em um branch que não chegou à linha principal. A linha foi anotada e o halt entrou de fato agora.

### Alterado

- **Quebra de compatibilidade:** a API passou a exigir um JWT do Keycloak em todas as rotas (exceto `GET /actuator/health`) e a autorizar por papel. Cliente que chamava a v0.1.0 sem token recebe `401`; o load test deixa de funcionar só com a URL e passa a exigir o cliente M2M configurado (`LASTRO_LOADTEST_TOKEN_URI`, `LASTRO_LOADTEST_CLIENT_ID`, `LASTRO_LOADTEST_CLIENT_SECRET`).
- Os números de desempenho do README, de `BASELINE_CONCURRENCY.md` e de `SCALE_ANALYSIS.md` foram removidos por não serem reproduzíveis (o cenário de 50 threads divergia 106×) e substituídos por medições JMH.
- `SCALE_ANALYSIS.md` passa a descrever o que os testes de escala validam — corretude sob carga — em vez de uma curva de escala e um diagnóstico de gargalos que o código não sustentava.
- O load generator deixa de chamar a API sem credenciais: autentica como cliente M2M (client credentials) com a service account do Keycloak.
- `GET /actuator/health` mostra detalhes apenas para quem está autenticado, e a especificação OpenAPI declara o esquema bearer exigido pelas rotas.

## [0.1.0] - 2026-10-17

Primeira versão pública: o núcleo de matching, com durabilidade e controles de mercado.

### Adicionado

- Matching engine com order book e prioridade preço-tempo.
- Ordens: BUY/SELL, LIMIT/MARKET e time-in-force GTC/IOC/FOK.
- Cancel-replace (amend) de ordens em repouso.
- Trades, posições e PnL por conta/símbolo.
- Contas, crédito/débito e transferências com idempotência.
- Journal append-only de contas e transações, com replay no startup.
- Journal append-only do engine (ordens enviadas, canceladas, substituídas e trades executados) com replay no startup, restaurando book, posições e trades.
- Controles de mercado: bandas de preço (limit up/down). *(O halt/circuit breaker também foi anunciado nesta versão, por engano: ele só entrou depois, ver [Unreleased].)*
- Validações regulatórias: janela de negociação, limites de quantidade/preço/valor e formato do símbolo.
- Persistência em Postgres via `docker compose`, ou H2 embutido em memória por padrão.
- Métricas (Actuator/Prometheus), load generator e documentação OpenAPI.
- CI com suíte rápida em PRs e completa no `main`/agendada; build da imagem Docker.
- Licença Apache-2.0 e metadados do projeto para open-source.
