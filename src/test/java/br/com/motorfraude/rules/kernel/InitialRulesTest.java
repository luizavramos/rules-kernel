package br.com.motorfraude.rules.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.motorfraude.rules.kernel.compiler.RuleCompiler;
import br.com.motorfraude.rules.kernel.evaluation.EvaluationContext;
import br.com.motorfraude.rules.kernel.evaluation.Severity;
import br.com.motorfraude.rules.kernel.model.RuleDefinition;
import br.com.motorfraude.rules.kernel.model.RuleSetDefinition;
import br.com.motorfraude.rules.kernel.model.RuleStatus;
import br.com.motorfraude.rules.kernel.model.Scoring;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class InitialRulesTest {

    private static final JsonNode ROOT = load();
    private static final Scoring SCORING = new Scoring(ROOT.get("scoring").get("mediumThreshold").asInt(), ROOT.get("scoring").get("highThreshold").asInt());
    private final RuleCompiler compiler = new RuleCompiler();

    private static JsonNode load() {
        try (InputStream in = InitialRulesTest.class.getResourceAsStream("/initial-rules.json")) {
            return JsonMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object scalar(JsonNode node) {
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        return node.asString();
    }

    private static Map<String, Object> map(JsonNode node) {
        var result = new LinkedHashMap<String, Object>();
        if (node != null) {
            node.properties().forEach(e -> result.put(e.getKey(), scalar(e.getValue())));
        }
        return result;
    }

    private static RuleDefinition rule(JsonNode json, RuleStatus status) {
        var overrides = new LinkedHashMap<String, Map<String, Object>>();
        if (json.has("paramOverrides")) {
            json.get("paramOverrides").properties().forEach(e -> overrides.put(e.getKey(), map(e.getValue())));
        }
        return new RuleDefinition(json.get("ruleId").asString(), 1, status, json.get("expression").asString(),
                map(json.get("params")), overrides, json.get("weight").asInt(), json.get("reasonTemplate").asString());
    }

    private static List<RuleDefinition> allRules(RuleStatus status) {
        var rules = new ArrayList<RuleDefinition>();
        ROOT.get("rules").forEach(r -> rules.add(rule(r, status)));
        return rules;
    }

    private static EvaluationContext context(JsonNode overrides) {
        var builder = TestContexts.neutral();
        map(overrides).forEach(builder::put);
        return builder.build();
    }

    @Test
    @DisplayName("são 8 regras, e todas as expressões compilam sem erros")
    void todasCompilam() {
        assertThat(allRules(RuleStatus.ACTIVE)).hasSize(8);
        for (var rule : allRules(RuleStatus.ACTIVE)) {
            assertThat(compiler.compile(rule).errors()).as(rule.ruleId()).isEmpty();
        }
    }

    @TestFactory
    @DisplayName("cada regra dispara e não dispara nos casos dados")
    Stream<DynamicTest> casos() {
        var tests = new ArrayList<DynamicTest>();
        for (JsonNode json : ROOT.get("rules")) {
            var id = json.get("ruleId").asString();
            var set = compiler.compileAll(new RuleSetDefinition(1, Instant.EPOCH, SCORING, List.of(rule(json, RuleStatus.ACTIVE))));
            boolean fires = false;
            boolean silent = false;
            for (JsonNode c : json.get("cases")) {
                boolean expected = c.get("expectedHit").asBoolean();
                fires |= expected;
                silent |= !expected;
                tests.add(DynamicTest.dynamicTest(id + " · " + c.get("name").asString(), () -> {
                    var evaluation = set.evaluate(context(c.get("context")));
                    assertThat(evaluation.errors()).isEmpty();
                    assertThat(evaluation.activeHits().stream().anyMatch(h -> h.ruleId().equals(id))).isEqualTo(expected);
                }));
            }
            assertThat(fires && silent).as(id + " precisa de ao menos um caso que dispara e um que não dispara").isTrue();
        }
        return tests.stream();
    }

    @Test
    @DisplayName("o conjunto inicial compila inteiro, e o motivo de cada disparo vem preenchido")
    void conjuntoInicialEMotivos() {
        var set = compiler.compileAll(new RuleSetDefinition(1, Instant.EPOCH, SCORING, allRules(RuleStatus.ACTIVE)));

        var evaluation = set.evaluate(context(ROOT.get("rules").get(4).get("cases").get(0).get("context")));

        assertThat(evaluation.activeHits()).hasSize(1);
        assertThat(evaluation.activeHits().get(0).reason()).isEqualTo("6 transações em 5 min (limite 5)");
    }

    @Test
    @DisplayName("sequestro de conta (R01 + R03 + R12) chega a ALTA; lista de bloqueio sozinha (R10) também")
    void severidades() {
        var set = compiler.compileAll(new RuleSetDefinition(1, Instant.EPOCH, SCORING, allRules(RuleStatus.ACTIVE)));
        var takeover = TestContexts.neutral()
                .put("profile.hoursSinceCredentialChange", 3L).put("features.isNewDevice", true)
                .put("features.isNewBeneficiary", true).put("tx.amount", 250_000L).build();
        var blocklist = TestContexts.neutral().put("lists.counterpartyFlagged", true).build();
        var rajada = TestContexts.neutral().put("features.count5m", 8L).build();

        var a = set.evaluate(takeover);
        assertThat(a.activeHits()).extracting("ruleId").contains("R01", "R03", "R12");
        assertThat(a.score()).isGreaterThanOrEqualTo(70);
        assertThat(a.severity()).isEqualTo(Severity.HIGH);
        assertThat(set.evaluate(blocklist).severity()).isEqualTo(Severity.HIGH);
        assertThat(set.evaluate(rajada).severity()).isEqualTo(Severity.NONE);
        var neutro = set.evaluate(TestContexts.neutral().build());
        assertThat(neutro.activeHits()).isEmpty();
        assertThat(neutro.score()).isZero();
    }
}
