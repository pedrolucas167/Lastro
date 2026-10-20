# Changelog

Todas as mudanças relevantes deste projeto são documentadas aqui.

O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o projeto adota [Versionamento Semântico](https://semver.org/lang/pt-BR/).

> ⚠️ **Experimental.** O núcleo de matching é correto sob concorrência e durável via journal + replay. A API exige autenticação (JWT do Keycloak), autoriza por papel na rota e por titular na conta, e cada evento do journal grava o ator (o `sub` e os papéis daquele momento), mas não há entidade de cliente (KYC), limites por titular nem conectividade de mercado (feed/FIX), e a liquidação é D+0 no próprio livro, sem câmara ou netting. Não use em produção.

## [Unreleased]

## [0.5.0] - 2026-10-19

Quinta versão pública: lastro, liquidação e custódia. Nenhuma ordem chega ao engine sem lastro — a compra bloqueia o caixa que ainda pode gastar (saldo menos o que já está prometido a ordens em aberto) e a venda bloqueia a ação —, e o trade liquida no ato: o comprador paga e o vendedor recebe no mesmo instante, com o `TradeExecuted` carregando tudo o que a liquidação precisa. A ação passa a entrar no sistema pela custódia (depósito e transferência), sem a qual uma conta nova não teria o que vender; ordem a mercado deixou de repousar (o que não executa é cancelado, fechando o `500` da execução seguinte, e a compra a mercado é limitada pelo teto da banda); e o journal deixa de aceitar ordem sem dinheiro ou sem ação por trás.

### Adicionado

- **Lastro de compra.** A ordem bloqueia `preço-limite × quantidade restante` do disponível da conta e é recusada com `403` quando não cobre — antes a ordem entrava no book sem dinheiro por trás, e o comprador executava sem saldo (a conta ficava negativa ou o débito falhava depois do trade). O disponível é o saldo menos o que está prometido a ordens em aberto; `AccountResponse` passa a expor `available`, e crédito/débito/transferência só alcançam o disponível.
- **Lastro de venda.** A ordem bloqueia as ações que ela ainda pode executar e é recusada com `403` quando a posição não cobre — antes a venda a descoberto criava posição negativa sem limite nenhum.
- **Liquidação no trade (D+0).** O comprador paga `preço × quantidade` e o vendedor recebe no instante do trade. Não há evento de caixa próprio: o `TradeExecuted` carrega tudo o que a liquidação precisa, então o replay chega ao mesmo estado sem coreografia extra.
- **Custódia**: `POST /api/trading/positions/deposit` (a ação entra no sistema, vinda de fora, no custo médio informado) e `POST /api/trading/positions/transfer` (a ação passa de uma conta para outra, com o custo médio acompanhando). É por onde a ação existe — sem isso, uma conta nova não teria o que vender. O fato é `CustodyTransferred`, replicado no replay, e a transferência não move ação prometida a uma venda em aberto.

### Corrigido

- **Replay de journal antigo com conta que nunca existiu.** A API aceitava qualquer `accountId` até a 0.4.0, então o journal pode ter ordem ou trade de uma conta que nunca foi aberta — e o `AccountNotFoundException` do replay derrubava o startup. O replay pula esses eventos com `WARN`: nada de conta ou caixa inventado, e a aplicação sobe.
- Ordem a mercado podia repousar no book e derrubar a execução seguinte com `500`: a ordem ficava no book sem preço, e o motor tentava comparar preço com `null`. Agora só ordem limitada GTC descansa — o que não executa de uma ordem a mercado é cancelado.
- As validações de saldo e posição do serviço regulatório (`validateSufficientBalance`/`validateSufficientPosition`) eram código morto: nunca eram chamadas. Foram removidas e substituídas pelo pré-trade de verdade.

### Alterado

- **Compra a mercado é limitada pelo teto da banda de preço**: o teto é o valor bloqueado (a ordem não tem preço próprio) e símbolo sem banda de referência recusa compra a mercado com `403`, porque não há teto que garanta o caixa.
- O amend bloqueia apenas o que ele passa a pedir a mais e devolve o que a ordem deixa de pedir; amend recusado pelo engine devolve o bloqueio inteiro.
- O bloqueio de lastro **não** é evento do journal: é consequência das ordens em aberto, como a posição, e o replay o reconstrói somando as ordens de compra e de venda que continuam abertas — o que exige reconstruir as contas antes do engine (daí a ordem explícita no replay).
- Journal gravado antes desta versão continua abrindo: o replay reconstrói o bloqueio a partir das ordens em aberto, mesmo as que foram aceitas sem lastro. A conta que ficou com bloqueio maior que o saldo simplesmente não aceita ordem nova até ser creditada.

## [0.4.0] - 2026-10-19

Quarta versão pública: durabilidade dos comandos do cliente e verdade da trilha. Ordens e transferências aceitam `Idempotency-Key` e a garantia vive no journal — o reenvio devolve o que já foi aceito, inclusive depois de um reinício —, cada evento grava quem o causou (o `sub` e os papéis daquele momento), e o journal deixou de registrar cancelamento ou amend que o engine recusou. Fecha também a porta de autenticação: o token carrega `aud: lastro-api`, a API o exige, e cada cliente do realm autoriza só o seu papel.

### Adicionado

- A transferência também trata reenvio pela chave do cliente: `POST /api/transactions/transfer` aceita `Idempotency-Key`, a chave entra no evento `TransferCompleted` e o índice é remontado no replay. Antes a garantia vivia num mapa em memória com janela de 24h, que morria no reinício — ou seja, o reenvio de um cliente logo depois de um restart movimentava o dinheiro de novo. A mesma chave para outra transferência responde `409`; transferência que falhou por saldo insuficiente não vira fato e por isso não prende a chave (o reenvio tenta de novo).
- `POST /api/trading/orders` aceita o cabeçalho `Idempotency-Key`: reenviar a mesma ordem com a mesma chave devolve a ordem já aceita, sem ordem nova e sem trade duplicado — antes, o reenvio de um cliente que perdeu a resposta criava uma segunda ordem de verdade. A chave entra no evento `OrderSubmitted` e o índice é remontado no replay, então o reenvio segue seguro depois de um reinício; a mesma chave para outra ordem responde `409`, e reenviar sem chave continua criando uma ordem nova a cada chamada.
- Cada evento do journal passa a gravar **quem o causou** — o `sub` do token e os papéis que a identidade carregava naquele momento — resolvido na borda e passado explícito ao serviço. Antes o journal dizia o que aconteceu, não quem fez; era o gap de auditoria declarado no README.

### Corrigido

- Cancelamento e amend que o engine recusou deixavam fato no journal: `OrderCancelled` era gravado **antes** de o engine dizer se o cancelamento aconteceu, então cancelar uma ordem inexistente respondia `404` e deixava na trilha um cancelamento atribuído a quem chamou — com o reenvio de um cliente (cancelar após um timeout) fabricando um fato fantasma por tentativa. Agora os dois eventos entram no journal **depois** de o engine confirmar o comando, e um `200` no cancelamento passa a significar que o fato está gravado (se a gravação falhar, a resposta é erro, nunca um "cancelado" mentiroso). O replay não era afetado — reexecutar o comando recusado não mudava estado — mas a trilha afirmava o que não houve.

### Alterado

- Cancelar de novo deixou de ser erro: uma ordem já cancelada responde `200` sem evento novo (o journal já tem o cancelamento, e cancelar é idempotente por natureza), ordem desconhecida continua `404` e ordem preenchida responde `409`, porque não existe cancelamento possível. Antes os três casos respondiam `404`, punindo justamente o cliente que reenviava.
- `POST /api/transactions/transfer` não aceita mais `"idempotencyKey"` no corpo: a chave passou para o cabeçalho `Idempotency-Key`, igual à ordem. O campo antigo é recusado com `400` (em vez de ignorado em silêncio), para ninguém achar que está protegido sem estar. O `IdempotencyService` em memória foi removido — a chave agora é fato do journal, como na ordem, e não há mais janela de 24h nem trabalho de limpeza.
- `Actor` deixou de ser `(subject, privileged)` e passou a ser `(subject, roles)`, com "privilegiado" derivado dos papéis. O journal precisa da autoridade do momento: um booleano não distingue operador de serviço M2M, e papel revogado depois apagaria a informação. O vocabulário de papéis (`lastro-service`, `lastro-operator`, `lastro-holder`) saiu de `config.Roles` para `domain.Actor`, que é quem os carrega; `config.Roles` ficou só com a leitura do token. A matriz de rotas não mudou.
- Journal gravado antes deste campo (payload sem `actor`) continua replayando, com o ator vazio — o replay não depende dele.

### Segurança

- O realm não emitia `aud` nenhum, e a API validava só o emissor — ou seja, **qualquer** token que aquele Keycloak emitisse valia na API, inclusive um destinado a outro cliente ou a outro sistema do mesmo realm. Agora todo token carrega `aud: lastro-api` (mapper de audiência em cada cliente, no `deploy/keycloak/lastro-realm.json`) e a API valida a audiência além do emissor (`spring.security.oauth2.resourceserver.jwt.audiences`). Sem audiência configurada a API recusa todo token, em vez de aceitar qualquer um: falha alta na primeira requisição é melhor do que servir com a porta aberta.
- Os clientes do realm tinham `fullScopeAllowed` ligado, então o token carregava os papéis do **usuário**, não os do cliente: um operador que entrasse pelo cliente `lastro-holder` levava `lastro-operator` no token. Agora o escopo é explícito por cliente (`scopeMappings`, `fullScopeAllowed: false`) e o token carrega só o papel daquele cliente — quem entra pelo cliente errado não leva poder nenhum.

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
