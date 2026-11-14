package br.com.motorfraude.rules.kernel.model;

import java.util.Map;

/**
 * Uma versão de regra. {@code paramOverrides} tem como chave o segmento ("PJ") e como valor os parâmetros
 * que sobrescrevem {@code params} para esse segmento.
 */
public record RuleDefinition(String ruleId, int version, RuleStatus status, String expression,
        Map<String, Object> params, Map<String, Map<String, Object>> paramOverrides,
        int weight, String reasonTemplate) {}
