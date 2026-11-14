package br.com.motorfraude.rules.kernel.evaluation;

/** Regra que disparou, com o motivo já preenchido pelo reasonTemplate. */
public record RuleHit(String ruleId, int version, int weight, String reason) {}
