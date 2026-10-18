package io.github.pedrolucas167.lastro.domain;

/**
 * Quem esta chamando a API: a identidade do token (`sub`) e se ela e privilegiada.
 *
 * Privilegiado e o back office -- o operador e o servico M2M -- que opera qualquer conta.
 * Quem nao e privilegiado so opera as contas que tem.
 */
public record Actor(String subject, boolean privileged) {
}
