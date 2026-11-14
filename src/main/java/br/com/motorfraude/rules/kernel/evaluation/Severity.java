package br.com.motorfraude.rules.kernel.evaluation;

/** Severidade pelas faixas do scoring: pontos >= alta é HIGH; >= média é MEDIUM; senão NONE. */
public enum Severity {
    NONE,
    MEDIUM,
    HIGH
}
