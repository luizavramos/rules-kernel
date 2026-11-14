package br.com.motorfraude.rules.kernel.evaluation;

import java.util.List;

public record Evaluation(long ruleSetVersion, int score, Severity severity, List<RuleHit> activeHits,
        List<RuleHit> shadowHits, List<RuleError> errors) {}
