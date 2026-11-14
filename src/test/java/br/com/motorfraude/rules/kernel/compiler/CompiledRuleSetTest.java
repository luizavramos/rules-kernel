package br.com.motorfraude.rules.kernel.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.motorfraude.rules.kernel.TestContexts;
import br.com.motorfraude.rules.kernel.evaluation.Severity;
import br.com.motorfraude.rules.kernel.model.RuleDefinition;
import br.com.motorfraude.rules.kernel.model.RuleSetDefinition;
import br.com.motorfraude.rules.kernel.model.RuleStatus;
import br.com.motorfraude.rules.kernel.model.Scoring;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CompiledRuleSetTest {

    private static final Scoring SCORING = new Scoring(40, 70);
    private final RuleCompiler compiler = new RuleCompiler();

    private static RuleDefinition rule(String id, RuleStatus status, String expression, Map<String, Object> params, int weight) {
        return new RuleDefinition(id, 1, status, expression, params, Map.of(), weight, "regra " + id);
    }

    private static RuleDefinition active(String id, String expression, int weight) {
        return rule(id, RuleStatus.ACTIVE, expression, Map.of(), weight);
    }

    private CompiledRuleSet compile(RuleDefinition... rules) {
        return compiler.compileAll(new RuleSetDefinition(7, Instant.parse("2026-11-14T12:00:00Z"), SCORING, List.of(rules)));
    }

    @Test
    @DisplayName("a versão do conjunto acompanha a definição")
    void versao() {
        assertThat(compile(active("R1", "lists.counterpartyFlagged", 10)).version()).isEqualTo(7);
        var evaluation = compile(active("R1", "lists.counterpartyFlagged", 10)).evaluate(TestContexts.neutral().build());
        assertThat(evaluation.ruleSetVersion()).isEqualTo(7);
    }

    @Test
    @DisplayName("regra dispara quando a condição é verdadeira e não dispara quando é falsa")
    void disparaENaoDispara() {
        var set = compile(rule("R07", RuleStatus.ACTIVE, "features.count5m > params.maxCount", Map.of("maxCount", 5), 25));

        var hit = set.evaluate(TestContexts.neutral().put("features.count5m", 7L).build());
        var miss = set.evaluate(TestContexts.neutral().put("features.count5m", 5L).build());

        assertThat(hit.activeHits()).extracting("ruleId").containsExactly("R07");
        assertThat(hit.score()).isEqualTo(25);
        assertThat(miss.activeHits()).isEmpty();
        assertThat(miss.score()).isZero();
    }

    @Test
    @DisplayName("override do segmento PJ: o mesmo contexto dispara para PF e não para PJ")
    void overridePorSegmento() {
        var rule = new RuleDefinition("R07", 1, RuleStatus.ACTIVE, "features.count5m > params.maxCount",
                Map.of("maxCount", 5), Map.of("PJ", Map.of("maxCount", 30)), 25, "r");
        var set = compile(rule);

        var pf = set.evaluate(TestContexts.neutral().put("features.count5m", 10L).put("profile.segment", "PF").build());
        var pj = set.evaluate(TestContexts.neutral().put("features.count5m", 10L).put("profile.segment", "PJ").build());
        var pjAlto = set.evaluate(TestContexts.neutral().put("features.count5m", 31L).put("profile.segment", "PJ").build());

        assertThat(pf.activeHits()).hasSize(1);
        assertThat(pj.activeHits()).isEmpty();
        assertThat(pjAlto.activeHits()).hasSize(1);
    }

    @Test
    @DisplayName("SHADOW aparece à parte e não soma pontos")
    void sombra() {
        var set = compile(
                active("A", "tx.amount > 0", 30),
                rule("S", RuleStatus.SHADOW, "tx.amount > 0", Map.of(), 50));

        var evaluation = set.evaluate(TestContexts.neutral().build());

        assertThat(evaluation.activeHits()).extracting("ruleId").containsExactly("A");
        assertThat(evaluation.shadowHits()).extracting("ruleId").containsExactly("S");
        assertThat(evaluation.score()).isEqualTo(30);
    }

    @Test
    @DisplayName("regras DRAFT e DISABLED num conjunto são ignoradas")
    void ignoraRascunhoEDesligada() {
        var set = compile(
                rule("D", RuleStatus.DRAFT, "tx.amount > 0", Map.of(), 50),
                rule("X", RuleStatus.DISABLED, "tx.amount > 0", Map.of(), 50));

        var evaluation = set.evaluate(TestContexts.neutral().build());

        assertThat(evaluation.activeHits()).isEmpty();
        assertThat(evaluation.shadowHits()).isEmpty();
    }

    @Test
    @DisplayName("erro na avaliação: a regra conta como não disparada, vira RuleError e as outras seguem")
    void erroDeRegra() {
        var set = compile(
                active("DIV", "tx.amount / features.count1m > 1", 50),
                active("OK", "tx.amount > 0", 20));

        var evaluation = set.evaluate(TestContexts.neutral().put("features.count1m", 0L).build());

        assertThat(evaluation.activeHits()).extracting("ruleId").containsExactly("OK");
        assertThat(evaluation.score()).isEqualTo(20);
        assertThat(evaluation.errors()).hasSize(1);
        assertThat(evaluation.errors().get(0).ruleId()).isEqualTo("DIV");
        assertThat(evaluation.errors().get(0).message()).contains("by zero");
    }

    @ParameterizedTest(name = "{0} pontos -> {1}")
    @DisplayName("severidade pelas faixas 40 e 70")
    @CsvSource({"0,NONE", "39,NONE", "40,MEDIUM", "69,MEDIUM", "70,HIGH", "120,HIGH"})
    void severidade(int pontos, Severity esperada) {
        var set = compile(active("R", "tx.amount > 0", pontos));

        var evaluation = set.evaluate(TestContexts.neutral().build());

        assertThat(evaluation.score()).isEqualTo(pontos);
        assertThat(evaluation.severity()).isEqualTo(esperada);
    }

    @Test
    @DisplayName("os pesos das regras ACTIVE que dispararam se somam")
    void soma() {
        var set = compile(active("A", "tx.amount > 0", 25), active("B", "tx.amount > 0", 30), active("C", "tx.amount < 0", 99));

        var evaluation = set.evaluate(TestContexts.neutral().build());

        assertThat(evaluation.score()).isEqualTo(55);
        assertThat(evaluation.severity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    @DisplayName("o motivo é preenchido com parâmetros efetivos e valores do contexto")
    void motivo() {
        var rule = new RuleDefinition("R07", 3, RuleStatus.ACTIVE, "features.count5m > params.maxCount", Map.of("maxCount", 5),
                Map.of("PJ", Map.of("maxCount", 30)), 25, "Mais de {params.maxCount} transações em 5 min ({features.count5m})");
        var set = compile(rule);

        var pf = set.evaluate(TestContexts.neutral().put("features.count5m", 7L).build());
        var pj = set.evaluate(TestContexts.neutral().put("features.count5m", 31L).put("profile.segment", "PJ").build());

        assertThat(pf.activeHits().get(0).reason()).isEqualTo("Mais de 5 transações em 5 min (7)");
        assertThat(pj.activeHits().get(0).reason()).isEqualTo("Mais de 30 transações em 5 min (31)");
        assertThat(pf.activeHits().get(0).version()).isEqualTo(3);
        assertThat(pf.activeHits().get(0).weight()).isEqualTo(25);
    }

    @Test
    @DisplayName("placeholder desconhecido no motivo fica como está, sem derrubar a avaliação")
    void motivoComPlaceholderDesconhecido() {
        var rule = new RuleDefinition("R", 1, RuleStatus.ACTIVE, "tx.amount > 0", Map.of(), Map.of(), 10, "valor {tx.nada}");

        var evaluation = compile(rule).evaluate(TestContexts.neutral().build());

        assertThat(evaluation.activeHits().get(0).reason()).isEqualTo("valor {tx.nada}");
    }

    @Test
    @DisplayName("parâmetros decimais e booleanos funcionam na avaliação")
    void parametrosDeOutrosTipos() {
        var dispara = compile(rule("R", RuleStatus.ACTIVE, "params.fator * 2.0 > 2.5 && params.ligada", Map.of("fator", 1.5, "ligada", true), 10));
        var desligada = compile(rule("R", RuleStatus.ACTIVE, "params.fator * 2.0 > 2.5 && params.ligada", Map.of("fator", 1.5, "ligada", false), 10));

        assertThat(dispara.evaluate(TestContexts.neutral().build()).activeHits()).hasSize(1);
        assertThat(desligada.evaluate(TestContexts.neutral().build()).activeHits()).isEmpty();
    }

    @Test
    @DisplayName("conjunto com regra inválida não compila e a exceção diz qual regra e por quê")
    void conjuntoInvalido() {
        assertThatThrownBy(() -> compile(active("OK", "tx.amount > 0", 10), active("RUIM", "features.contagem5m > 3", 10)))
                .isInstanceOf(RuleSetCompilationException.class)
                .hasMessageContaining("RUIM")
                .hasMessageContaining("feature desconhecida: contagem5m")
                .satisfies(e -> assertThat(((RuleSetCompilationException) e).failures()).extracting("ruleId").containsExactly("RUIM"));
    }

    @Test
    @DisplayName("conjunto vazio avalia sem pontos")
    void vazio() {
        var evaluation = compile().evaluate(TestContexts.neutral().build());

        assertThat(evaluation.score()).isZero();
        assertThat(evaluation.severity()).isEqualTo(Severity.NONE);
    }
}
