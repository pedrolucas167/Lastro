# Changelog

Todas as mudanças relevantes deste projeto são documentadas aqui.

O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o projeto adota [Versionamento Semântico](https://semver.org/lang/pt-BR/).

> ⚠️ **Experimental.** O núcleo de matching é correto sob concorrência e durável via journal + replay, mas a API **não tem autenticação** e não há conectividade de mercado (feed/FIX) nem clearing. Não use em produção.

## [Unreleased]

### Adicionado

- Benchmark JMH para throughput do motor de matching e do caminho de aplicação, sob o perfil `-Pbenchmark`, com resultados e metodologia em `PERFORMANCE.md`.

### Corrigido

- `ScaleTest` calculava o throughput com metade das ordens enviadas (cada iteração envia BUY e SELL) e incluía criação de pool de threads e shutdown no tempo medido.
- A especificação OpenAPI respondia `500`: o `springdoc-openapi` 2.3.0, compilado contra o Spring Framework 6, chamava um construtor de `ControllerAdviceBean` removido no Framework 7. Atualizado para `3.1.1`.
- A especificação OpenAPI declarava licença MIT, versão `1.0` e uma descrição de "teste de desempenho"; agora traz Apache-2.0, a versão do build e a descrição atual do projeto.

### Alterado

- Os números de desempenho do README, de `BASELINE_CONCURRENCY.md` e de `SCALE_ANALYSIS.md` foram removidos por não serem reproduzíveis (o cenário de 50 threads divergia 106×) e substituídos por medições JMH.
- `SCALE_ANALYSIS.md` passa a descrever o que os testes de escala validam — corretude sob carga — em vez de uma curva de escala e um diagnóstico de gargalos que o código não sustentava.

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
- Controles de mercado: bandas de preço (limit up/down) e halt/circuit breaker.
- Validações regulatórias: janela de negociação, limites de quantidade/preço/valor e formato do símbolo.
- Persistência em Postgres via `docker compose`, ou H2 embutido em memória por padrão.
- Métricas (Actuator/Prometheus), load generator e documentação OpenAPI.
- CI com suíte rápida em PRs e completa no `main`/agendada; build da imagem Docker.
- Licença Apache-2.0 e metadados do projeto para open-source.
