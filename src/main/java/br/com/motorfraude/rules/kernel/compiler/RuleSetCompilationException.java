package br.com.motorfraude.rules.kernel.compiler;

import java.util.List;

/** O conjunto não compila: pelo menos uma regra tem erro. Carrega o resultado de cada regra que falhou. */
public final class RuleSetCompilationException extends RuntimeException {

    private final transient List<CompilationResult> failures;

    public RuleSetCompilationException(List<CompilationResult> failures) {
        super("conjunto de regras inválido: " + failures.stream()
                .map(f -> f.ruleId() + " " + f.errors()).reduce((a, b) -> a + "; " + b).orElse(""));
        this.failures = List.copyOf(failures);
    }

    public List<CompilationResult> failures() {
        return failures;
    }
}
