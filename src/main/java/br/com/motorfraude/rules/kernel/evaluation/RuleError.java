package br.com.motorfraude.rules.kernel.evaluation;

/** Regra que falhou na avaliação (ex.: divisão por zero). Conta como não disparada; o motor incrementa o contador. */
public record RuleError(String ruleId, int version, String message) {}
