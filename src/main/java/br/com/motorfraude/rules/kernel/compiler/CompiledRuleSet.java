package br.com.motorfraude.rules.kernel.compiler;

import br.com.motorfraude.rules.kernel.evaluation.Evaluation;
import br.com.motorfraude.rules.kernel.evaluation.EvaluationContext;
import br.com.motorfraude.rules.kernel.evaluation.RuleError;
import br.com.motorfraude.rules.kernel.evaluation.RuleHit;
import br.com.motorfraude.rules.kernel.evaluation.Severity;
import br.com.motorfraude.rules.kernel.model.RuleDefinition;
import br.com.motorfraude.rules.kernel.model.RuleStatus;
import br.com.motorfraude.rules.kernel.model.Scoring;
import dev.cel.runtime.CelRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompiledRuleSet {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+\\.[A-Za-z0-9]+)}");

    record CompiledRule(RuleDefinition definition, CelRuntime.Program program) {}

    private final long version;
    private final Scoring scoring;
    private final List<CompiledRule> rules;

    CompiledRuleSet(long version, Scoring scoring, List<CompiledRule> rules) {
        this.version = version;
        this.scoring = scoring;
        this.rules = List.copyOf(rules);
    }

    public long version() {
        return version;
    }

    public Evaluation evaluate(EvaluationContext context) {
        var activeHits = new ArrayList<RuleHit>();
        var shadowHits = new ArrayList<RuleHit>();
        var errors = new ArrayList<RuleError>();
        int score = 0;

        for (CompiledRule compiled : rules) {
            var rule = compiled.definition();
            var params = effectiveParams(rule, context.segment());
            Object outcome;
            try {
                var variables = new HashMap<String, Object>(context.values());
                variables.put("params", params);
                outcome = compiled.program().eval(variables);
            } catch (Exception e) {
                errors.add(new RuleError(rule.ruleId(), rule.version(), e.getMessage()));
                continue;
            }
            if (!Boolean.TRUE.equals(outcome)) {
                continue;
            }
            var hit = new RuleHit(rule.ruleId(), rule.version(), rule.weight(), reason(rule.reasonTemplate(), params, context));
            if (rule.status() == RuleStatus.ACTIVE) {
                activeHits.add(hit);
                score += rule.weight();
            } else {
                shadowHits.add(hit);
            }
        }
        return new Evaluation(version, score, severity(score), List.copyOf(activeHits), List.copyOf(shadowHits), List.copyOf(errors));
    }

    private Severity severity(int score) {
        if (score >= scoring.highThreshold()) {
            return Severity.HIGH;
        }
        return score >= scoring.mediumThreshold() ? Severity.MEDIUM : Severity.NONE;
    }

    static Map<String, Object> effectiveParams(RuleDefinition rule, String segment) {
        var params = new HashMap<String, Object>();
        if (rule.params() != null) {
            rule.params().forEach((k, v) -> params.put(k, celValue(v)));
        }
        if (rule.paramOverrides() != null && segment != null && rule.paramOverrides().containsKey(segment)) {
            rule.paramOverrides().get(segment).forEach((k, v) -> params.put(k, celValue(v)));
        }
        return params;
    }

    private static Object celValue(Object value) {
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        return value;
    }

    private static String reason(String template, Map<String, Object> params, EvaluationContext context) {
        if (template == null) {
            return "";
        }
        return PLACEHOLDER.matcher(template).replaceAll(match -> {
            String name = match.group(1);
            Object value = name.startsWith("params.") ? params.get(name.substring("params.".length())) : context.values().get(name);
            return Matcher.quoteReplacement(value == null ? match.group() : String.valueOf(value));
        });
    }
}
