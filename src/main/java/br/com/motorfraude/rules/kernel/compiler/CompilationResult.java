package br.com.motorfraude.rules.kernel.compiler;

import java.util.List;

public record CompilationResult(String ruleId, List<String> errors) {

    public boolean valid() {
        return errors.isEmpty();
    }
}
