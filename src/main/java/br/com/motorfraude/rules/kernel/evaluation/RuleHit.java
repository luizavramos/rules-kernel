package br.com.motorfraude.rules.kernel.evaluation;

public record RuleHit(String ruleId, int version, int weight, String reason) {}
