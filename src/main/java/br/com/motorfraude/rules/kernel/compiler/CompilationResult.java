package br.com.motorfraude.rules.kernel.compiler;

import java.util.List;

/** Resultado da compilação de uma regra: sem erros, a regra é válida. */
public record CompilationResult(String ruleId, List<String> errors) {

    public boolean valid() {
        return errors.isEmpty();
    }
}
