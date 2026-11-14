package br.com.motorfraude.rules.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cel.common.CelValidationException;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T201 — spike: o cel-java compila com tipos, avalia e rejeita o que está errado? Descartável depois da T205. */
class CelSpikeTest {

    private final CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder()
            .addVar("features.count5m", SimpleType.INT)
            .addVar("tx.amount", SimpleType.INT)
            .addVar("params", MapType.create(SimpleType.STRING, SimpleType.DYN))
            .build();
    private final CelRuntime runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();

    @Test
    void compilaEAvalia() throws Exception {
        var ast = compiler.compile("features.count5m > params.maxCount").getAst();
        var program = runtime.createProgram(ast);
        Object hit = program.eval(Map.of("features.count5m", 7L, "tx.amount", 1L, "params", Map.of("maxCount", 5L)));
        Object miss = program.eval(Map.of("features.count5m", 3L, "tx.amount", 1L, "params", Map.of("maxCount", 5L)));
        assertThat(hit).isEqualTo(true);
        assertThat(miss).isEqualTo(false);
    }

    @Test
    void featureDesconhecidaEErroDeCompilacao() {
        var result = compiler.compile("features.contagem5m > 3");
        System.out.println("DESCONHECIDA: " + result.getErrorString());
        assertThat(result.hasError()).isTrue();
    }

    @Test
    void tipoErradoEErroDeCompilacao() {
        var result = compiler.compile("tx.amount > \"mil\"");
        System.out.println("TIPO: " + result.getErrorString());
        assertThat(result.hasError()).isTrue();
    }
}
