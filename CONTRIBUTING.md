# Contribuindo com o Lastro

Obrigado pelo interesse! Este documento descreve como propor mudanças.

## Pré-requisitos

- **Java 25** — o build usa o Maven Wrapper (`mvnw`), então não é preciso instalar Maven.
- **Docker** (opcional) — só para o Postgres via `docker compose` e para os testes de integração com Testcontainers.

## Rodando localmente

```bash
./mvnw spring-boot:run
```

Sem Docker a aplicação sobe com um **H2 embutido em memória** (zero configuração). Com Docker:

```bash
docker compose up --build
```

## Testes

```bash
./mvnw test                 # suíte rápida (exclui @Tag("slow"))
./mvnw -Pall-tests test     # suíte completa (inclui os testes de escala)
./mvnw verify               # o que o CI roda em PRs
```

Os testes de escala são marcados com `@Tag("slow")` e ficam fora do feedback de PR — eles rodam no `main` e no agendamento noturno. **Antes de abrir um PR, garanta que `./mvnw verify` passa**; se mexeu no engine ou em concorrência, rode também `./mvnw -Pall-tests test`.

## Fluxo

1. Abra uma **issue** descrevendo o problema ou a ideia (ou comente em uma existente) antes de PRs grandes.
2. Crie uma branch a partir de `main`, com prefixo indicando o tipo: `feat/…`, `fix/…`, `docs/…`, `chore/…`, `refactor/…`, `ci/…`.
3. Faça commits pequenos e focados, com a mensagem no imperativo e em português (ex.: "Adiciona time-in-force as ordens").
4. Abra o PR preenchendo o template. Mudanças grandes devem vir em **PRs empilhados** — um por preocupação.

## Estilo

- **Código autoexplicativo**: prefira nomes claros a comentários. Comentários só para explicar o *porquê*, não o *o quê*.
- Siga o padrão do código ao redor (pacotes, nomes, formatação).
- Toda mudança de comportamento precisa de teste.
- Sem over-engineering: nada de Kafka ou microserviços no Day 1 (ver a seção **Filosofia** no README).

## O que evitar

- Não versione artefatos de execução (`logs/`, `target/`).
- Não misture refatoração ampla com mudança de comportamento no mesmo PR.
- Não abra issue pública para vulnerabilidades — veja o [SECURITY.md](./SECURITY.md).

## Reportando problemas

- **Bugs e sugestões**: use os templates de issue.
- **Vulnerabilidades**: reporte em privado, conforme o [SECURITY.md](./SECURITY.md).

Ao contribuir, você concorda em seguir o [Código de Conduta](./CODE_OF_CONDUCT.md).
