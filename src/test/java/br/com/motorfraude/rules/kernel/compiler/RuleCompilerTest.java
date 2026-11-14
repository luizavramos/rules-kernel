package br.com.motorfraude.rules.kernel.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.motorfraude.rules.kernel.model.RuleDefinition;
import br.com.motorfraude.rules.kernel.model.RuleStatus;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuleCompilerTest {

    private final RuleCompiler compiler = new RuleCompiler();

    private static RuleDefinition rule(String expression, Map<String, Object> params) {
        return new RuleDefinition("RX", 1, RuleStatus.DRAFT, expression, params, Map.of(), 10, "motivo");
    }

    private static RuleDefinition rule(String expression) {
        return rule(expression, Map.of());
    }

    @Test
    @DisplayName("expressão válida com parâmetro compila sem erros")
    void valida() {
        var result = compiler.compile(rule("features.count5m > params.maxCount", Map.of("maxCount", 5)));

        assertThat(result.errors()).isEmpty();
        assertThat(result.valid()).isTrue();
        assertThat(result.ruleId()).isEqualTo("RX");
    }

    @Test
    @DisplayName("feature inexistente: erro com o nome da feature")
    void featureDesconhecida() {
        var result = compiler.compile(rule("features.contagem5m > 3"));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("feature desconhecida: contagem5m");
    }

    @Test
    @DisplayName("grupo inexistente: erro de variável desconhecida")
    void grupoDesconhecido() {
        var result = compiler.compile(rule("foo.bar > 1"));

        assertThat(result.errors()).contains("variável desconhecida: foo");
    }

    @Test
    @DisplayName("todos os erros de feature aparecem juntos, não só o primeiro")
    void varios() {
        var result = compiler.compile(rule("features.a > 1 && features.b > 2"));

        assertThat(result.errors()).contains("feature desconhecida: a", "feature desconhecida: b");
    }

    @Test
    @DisplayName("tipo errado: erro de tipo")
    void tipoErrado() {
        var result = compiler.compile(rule("tx.amount > \"mil\""));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.startsWith("erro de tipo:"));
    }

    @Test
    @DisplayName("expressão que não devolve verdadeiro/falso é rejeitada")
    void naoBooleana() {
        var result = compiler.compile(rule("tx.amount + 1"));

        assertThat(result.errors()).contains("a expressão deve resultar em verdadeiro ou falso");
    }

    @Test
    @DisplayName("erro de sintaxe")
    void sintaxe() {
        var result = compiler.compile(rule("tx.amount >"));

        assertThat(result.errors()).anyMatch(e -> e.startsWith("erro de sintaxe:"));
    }

    @Test
    @DisplayName("params.x sem x definido na regra: erro com o nome do parâmetro")
    void parametroNaoDefinido() {
        var result = compiler.compile(rule("features.count5m > params.maxCount"));

        assertThat(result.errors()).contains("parâmetro não definido: maxCount");
    }

    @Test
    @DisplayName("override de segmento só pode sobrescrever parâmetros que existem")
    void overrideDeParametroInexistente() {
        var rule = new RuleDefinition("RX", 1, RuleStatus.DRAFT, "features.count5m > params.maxCount",
                Map.of("maxCount", 5), Map.of("PJ", Map.of("outro", 30)), 10, "motivo");

        assertThat(compiler.compile(rule).errors()).contains("override do segmento PJ usa parâmetro não definido: outro");
    }

    @Test
    @DisplayName("override válido compila")
    void overrideValido() {
        var rule = new RuleDefinition("RX", 1, RuleStatus.DRAFT, "features.count5m > params.maxCount",
                Map.of("maxCount", 5), Map.of("PJ", Map.of("maxCount", 30)), 10, "motivo");

        assertThat(compiler.compile(rule).valid()).isTrue();
    }

    @Test
    @DisplayName("a função própria max(int, int) está disponível")
    void funcaoMax() {
        var result = compiler.compile(rule("tx.amount > max(100000, features.avgAmount30d * 5)"));

        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("expressão com nós demais é rejeitada, e a mensagem diz o limite")
    void nosDemais() {
        var small = new RuleCompiler(30, 50);
        var expression = "tx.amount > 1" + " && tx.amount > 1".repeat(20);

        var result = small.compile(rule(expression));

        assertThat(result.errors()).anyMatch(e -> e.startsWith("expressão complexa demais:") && e.contains("máximo 30"));
    }

    @Test
    @DisplayName("expressão profunda demais é rejeitada")
    void profundidadeDemais() {
        var shallow = new RuleCompiler(1000, 5);
        var expression = "!(".repeat(8) + "tx.amount > 1" + ")".repeat(8);

        var result = shallow.compile(rule(expression));

        assertThat(result.errors()).anyMatch(e -> e.startsWith("expressão profunda demais:") && e.contains("máximo 5"));
    }

    @Test
    @DisplayName("os limites padrão aceitam uma regra composta realista")
    void limitesPadrao() {
        var expression = "features.count30d >= params.minHistory && tx.amount > max(params.floor, features.avgAmount30d * params.multiplier)";

        var result = compiler.compile(rule(expression, Map.of("minHistory", 5, "floor", 100000, "multiplier", 5)));

        assertThat(result.errors()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("todas as features do catálogo (plano 3.1) estão declaradas, com o tipo certo")
    @ValueSource(strings = {
            "tx.amount > 0", "tx.type == 'PIX'", "tx.channel == 'API'", "tx.hourOfDay < 6", "tx.counterpartyBank == '001'",
            "profile.segment == 'PJ'", "profile.accountAgeDays < 30", "profile.hoursSinceCredentialChange >= 0",
            "features.count1m > 0", "features.count5m > 0", "features.count1h > 0", "features.count24h > 0", "features.count30d > 0",
            "features.sum1h > 0", "features.sum24h > 0", "features.avgAmount30d > 0", "features.maxAmount90d > 0",
            "features.isNewBeneficiary", "features.isNewDevice", "features.distinctBeneficiaries24h > 0",
            "lists.counterpartyFlagged", "lists.deviceFlagged"})
    void catalogo(String expression) {
        assertThat(compiler.compile(rule(expression)).errors()).isEmpty();
    }

    @Test
    @DisplayName("bool usado como número é erro de tipo (o catálogo tem os tipos certos)")
    void tipoDoCatalogo() {
        assertThat(compiler.compile(rule("features.isNewDevice > 3")).errors()).anyMatch(e -> e.startsWith("erro de tipo:"));
    }
}
