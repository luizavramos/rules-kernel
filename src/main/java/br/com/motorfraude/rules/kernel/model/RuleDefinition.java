package br.com.motorfraude.rules.kernel.model;

import java.util.Map;

public record RuleDefinition(String ruleId, int version, RuleStatus status, String expression,
        Map<String, Object> params, Map<String, Map<String, Object>> paramOverrides,
        int weight, String reasonTemplate) {}
