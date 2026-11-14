package br.com.motorfraude.rules.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T201 — spike, parte 2: função própria max(int,int), limites de tamanho e erro de avaliação. */
class CelSpikeFunctionsTest {

    private static CelCompiler compiler(CelOptions options) {
        return CelCompilerFactory.standardCelCompilerBuilder()
                .setOptions(options)
                .addVar("tx.amount", SimpleType.INT)
                .addVar("features.avgAmount30d", SimpleType.INT)
                .addFunctionDeclarations(CelFunctionDecl.newFunctionDeclaration("max",
                        CelOverloadDecl.newGlobalOverload("max_int_int", SimpleType.INT, SimpleType.INT, SimpleType.INT)))
                .build();
    }

    private final CelCompiler compiler = compiler(CelOptions.DEFAULT);
    private final dev.cel.runtime.CelRuntime runtime = CelRuntimeFactory.standardCelRuntimeBuilder()
            .addFunctionBindings(CelFunctionBinding.from("max_int_int", Long.class, Long.class, Math::max))
            .build();

    @Test
    void funcaoMaxPropria() throws Exception {
        var program = runtime.createProgram(compiler.compile("tx.amount > max(100000, features.avgAmount30d * 5)").getAst());
        assertThat(program.eval(Map.of("tx.amount", 600_000L, "features.avgAmount30d", 100_000L))).isEqualTo(true);
        assertThat(program.eval(Map.of("tx.amount", 400_000L, "features.avgAmount30d", 100_000L))).isEqualTo(false);
    }

    @Test
    void expressaoGrandeDemaisERejeitadaPeloLimiteDeNos() {
        var small = compiler(CelOptions.current().maxParseExpressionNodeCount(20).build());
        var big = "tx.amount > 1 " + "&& tx.amount > 1 ".repeat(40);
        var result = small.compile(big);
        System.out.println("LIMITE: " + (result.hasError() ? result.getErrorString() : "aceitou"));
        assertThat(result.hasError()).isTrue();
        assertThat(small.compile("tx.amount > 1").hasError()).isFalse();
    }

    @Test
    void divisaoPorZeroEErroDeAvaliacao() throws Exception {
        var program = runtime.createProgram(compiler.compile("tx.amount / features.avgAmount30d > 1").getAst());
        assertThatThrownBy(() -> program.eval(Map.of("tx.amount", 10L, "features.avgAmount30d", 0L)))
                .hasMessageContaining("by zero");
    }
}
