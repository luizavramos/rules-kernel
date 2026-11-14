package br.com.motorfraude.rules.kernel.evaluation;

import java.util.List;

/** Resultado de avaliar um conjunto: só as regras ACTIVE somam pontos; as SHADOW ficam à parte. */
public record Evaluation(long ruleSetVersion, int score, Severity severity, List<RuleHit> activeHits,
        List<RuleHit> shadowHits, List<RuleError> errors) {}
