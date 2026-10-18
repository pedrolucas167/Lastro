# Política de Segurança

## Estado do projeto

O Lastro é **experimental** e **não é production-ready**. A API exige autenticação (JWT do Keycloak) e autoriza por papel — o cliente M2M só abre conta e transfere; saldo, ordens, carga e halt exigem operador. O que **não** existe é vínculo entre conta e portador: qualquer operador movimenta qualquer conta. Não exponha a aplicação a redes não confiáveis.

## Credenciais de desenvolvimento

O repositório traz, de propósito, credenciais de demonstração: o realm `lastro` em `deploy/keycloak/` (usuário `operador`/`operador` e o segredo `lastro-service-secret` do cliente M2M), o `admin`/`admin` do Keycloak e o `lastro`/`lastro` do Postgres no `compose.yaml`.

Elas são públicas e servem só para rodar a demo local. O que **não** pode acontecer é subir o realm de desenvolvimento exposto — qualquer pessoa lê o segredo no repositório e emite um token M2M. Para barrar o caso mais fácil desse acidente, a aplicação **recusa subir** quando o segredo do cliente M2M de desenvolvimento está em uso com um emissor que não seja localhost (`lastro.security.reject-dev-credentials`); o Keycloak e o Postgres ficam fora desse alcance e dependem de você não publicá-los.

## Versões suportadas

Só a branch `main` recebe correções. Não há releases com suporte de longo prazo.

## Reportando uma vulnerabilidade

**Não abra uma issue pública.** Use o reporte privado do GitHub:

1. Acesse a aba **Security** do repositório.
2. Clique em **Report a vulnerability** (GitHub Security Advisories).

Se preferir, envie um e-mail para o mantenedor (ver o perfil em [github.com/pedrolucas167](https://github.com/pedrolucas167)) com:

- descrição da vulnerabilidade e impacto;
- passos para reproduzir (prova de conceito, se possível);
- versão/commit afetado.

## O que esperar

- Confirmação de recebimento em até **7 dias**.
- Uma avaliação inicial e um plano de correção, quando aplicável.
- Crédito no aviso, se você quiser.

Como o projeto é mantido por voluntários, não há SLA para correção.
