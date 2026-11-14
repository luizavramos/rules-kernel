package br.com.motorfraude.rules.kernel.evaluation;

public record RuleError(String ruleId, int version, String message) {}
