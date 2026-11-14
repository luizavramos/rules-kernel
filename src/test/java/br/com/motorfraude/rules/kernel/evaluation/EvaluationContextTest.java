package br.com.motorfraude.rules.kernel.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.motorfraude.rules.kernel.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T206 — o contexto só aceita o que o catálogo declara, com o tipo certo, e exige todas as features. */
class EvaluationContextTest {

    @Test
    @DisplayName("contexto completo expõe os valores e o segmento")
    void completo() {
        var context = TestContexts.neutral().put("profile.segment", "PJ").build();

        assertThat(context.segment()).isEqualTo("PJ");
        assertThat(context.values()).containsEntry("tx.amount", 10_000L).containsEntry("features.isNewDevice", false);
    }

    @Test
    @DisplayName("inteiros menores (Integer) viram long")
    void integerViraLong() {
        var context = TestContexts.neutral().put("tx.amount", 500).build();

        assertThat(context.values().get("tx.amount")).isEqualTo(500L);
    }

    @Test
    @DisplayName("feature fora do catálogo é rejeitada")
    void desconhecida() {
        assertThatThrownBy(() -> TestContexts.neutral().put("features.nada", 1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("features.nada");
    }

    @Test
    @DisplayName("tipo errado é rejeitado, com o nome da feature")
    void tipoErrado() {
        assertThatThrownBy(() -> TestContexts.neutral().put("tx.amount", "caro"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tx.amount");
        assertThatThrownBy(() -> TestContexts.neutral().put("features.isNewDevice", 1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("features.isNewDevice");
    }

    @Test
    @DisplayName("faltando feature, o build diz quais")
    void faltando() {
        assertThatThrownBy(() -> EvaluationContext.builder().put("tx.amount", 1L).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("faltam features")
                .hasMessageContaining("features.count5m");
    }
}
