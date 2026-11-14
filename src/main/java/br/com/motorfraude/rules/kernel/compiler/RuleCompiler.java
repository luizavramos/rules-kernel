package br.com.motorfraude.rules.kernel.compiler;

import br.com.motorfraude.rules.kernel.catalog.FeatureCatalog;
import br.com.motorfraude.rules.kernel.catalog.FeatureType;
import br.com.motorfraude.rules.kernel.model.RuleDefinition;
import br.com.motorfraude.rules.kernel.model.RuleSetDefinition;
import br.com.motorfraude.rules.kernel.model.RuleStatus;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelValidationResult;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.types.CelType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compila e valida regras contra o catálogo de features. Os erros saem em português, com o nome da feature ou do
 * parâmetro, e todos juntos. O custo da expressão é limitado por número de nós e profundidade (o cel-java não tem
 * estimador de custo; ver SPIKE.md).
 */
public final class RuleCompiler {

    public static final int DEFAULT_MAX_NODES = 200;
    public static final int DEFAULT_MAX_DEPTH = 24;
    /** Tamanho máximo do texto da expressão, em caracteres. */
    public static final int MAX_EXPRESSION_LENGTH = 2000;

    /** Nome da função própria, declarada no compilador e implementada no avaliador. */
    static final String MAX_FUNCTION = "max";
    static final String MAX_OVERLOAD = "max_int_int";

    private final FeatureCatalog catalog = FeatureCatalog.standardCatalog();
    private final CelCompiler cel;
    private final CelRuntime runtime;
    private final int maxNodes;
    private final int maxDepth;

    public RuleCompiler() {
        this(DEFAULT_MAX_NODES, DEFAULT_MAX_DEPTH);
    }

    public RuleCompiler(int maxNodes, int maxDepth) {
        this.maxNodes = maxNodes;
        this.maxDepth = maxDepth;
        var builder = CelCompilerFactory.standardCelCompilerBuilder();
        catalog.features().forEach((name, type) -> builder.addVar(name, celType(type)));
        builder.addVar(FeatureCatalog.PARAMS, MapType.create(SimpleType.STRING, SimpleType.DYN));
        builder.addFunctionDeclarations(CelFunctionDecl.newFunctionDeclaration(MAX_FUNCTION,
                CelOverloadDecl.newGlobalOverload(MAX_OVERLOAD, SimpleType.INT, SimpleType.INT, SimpleType.INT)));
        this.cel = builder.build();
        this.runtime = CelRuntimeFactory.standardCelRuntimeBuilder()
                .addFunctionBindings(CelFunctionBinding.from(MAX_OVERLOAD, Long.class, Long.class, Math::max))
                .build();
    }

    /**
     * Compila as regras ACTIVE e SHADOW do conjunto (DRAFT e DISABLED não entram). Se qualquer uma falhar, lança
     * {@link RuleSetCompilationException} com o erro de cada uma: o conjunto nunca é publicado pela metade.
     */
    public CompiledRuleSet compileAll(RuleSetDefinition set) {
        var scoring = set.scoring();
        if (scoring == null || scoring.mediumThreshold() <= 0 || scoring.mediumThreshold() > scoring.highThreshold()) {
            throw new IllegalArgumentException("scoring inválido: é preciso 0 < média <= alta");
        }
        var failures = new ArrayList<CompilationResult>();
        var compiled = new ArrayList<CompiledRuleSet.CompiledRule>();
        for (RuleDefinition rule : set.rules()) {
            if (rule.status() != RuleStatus.ACTIVE && rule.status() != RuleStatus.SHADOW) {
                continue;
            }
            var result = compile(rule);
            if (!result.valid()) {
                failures.add(result);
                continue;
            }
            try {
                compiled.add(new CompiledRuleSet.CompiledRule(rule, runtime.createProgram(astOf(cel.compile(rule.expression())))));
            } catch (CelEvaluationException e) {
                failures.add(new CompilationResult(rule.ruleId(), List.of("erro ao preparar a regra: " + e.getMessage())));
            }
        }
        if (!failures.isEmpty()) {
            throw new RuleSetCompilationException(failures);
        }
        return new CompiledRuleSet(set.version(), scoring, compiled);
    }

    public CompilationResult compile(RuleDefinition rule) {
        var errors = new LinkedHashSet<String>();
        checkOverrides(rule, errors);

        var expression = rule.expression() == null ? "" : rule.expression();
        if (expression.isBlank()) {
            errors.add("a expressão não pode ser vazia");
            return result(rule, errors);
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            errors.add("expressão longa demais: " + expression.length() + " caracteres (máximo " + MAX_EXPRESSION_LENGTH + ")");
            return result(rule, errors);
        }

        var parsed = cel.parse(expression);
        if (parsed.hasError()) {
            issues(parsed).forEach(m -> errors.add("erro de sintaxe: " + m));
            return result(rule, errors);
        }

        var root = CelNavigableAst.fromAst(astOf(parsed)).getRoot();
        if (!checkComplexity(root, errors)) {
            return result(rule, errors);
        }
        int before = errors.size();
        checkNames(root, rule, errors);
        if (errors.size() > before) {
            return result(rule, errors);
        }

        var checked = cel.check(astOf(parsed));
        if (checked.hasError()) {
            issues(checked).forEach(m -> errors.add("erro de tipo: " + m));
            return result(rule, errors);
        }
        CelType type = astOf(checked).getResultType();
        if (!SimpleType.BOOL.equals(type) && !SimpleType.DYN.equals(type)) {
            errors.add("a expressão deve resultar em verdadeiro ou falso");
        }
        return result(rule, errors);
    }

    private boolean checkComplexity(CelNavigableExpr root, Set<String> errors) {
        long nodes = root.allNodes().count();
        if (nodes > maxNodes) {
            errors.add("expressão complexa demais: " + nodes + " nós (máximo " + maxNodes + ")");
            return false;
        }
        int depth = root.allNodes().mapToInt(CelNavigableExpr::depth).max().orElse(0);
        if (depth > maxDepth) {
            errors.add("expressão profunda demais: profundidade " + depth + " (máximo " + maxDepth + ")");
            return false;
        }
        return true;
    }

    /** Nomes antes do verificador de tipos, para a mensagem dizer qual feature ou parâmetro não existe. */
    private void checkNames(CelNavigableExpr root, RuleDefinition rule, Set<String> errors) {
        var definedParams = rule.params() == null ? Set.<String>of() : rule.params().keySet();
        root.allNodes().forEach(node -> {
            CelExpr expr = node.expr();
            if (expr.exprKind().getKind() == Kind.SELECT) {
                var select = expr.select();
                if (select.operand().exprKind().getKind() != Kind.IDENT) {
                    return;
                }
                String group = select.operand().ident().name();
                String field = select.field();
                if (FeatureCatalog.PARAMS.equals(group)) {
                    if (!definedParams.contains(field)) {
                        errors.add("parâmetro não definido: " + field);
                    }
                } else if (catalog.isGroup(group)) {
                    if (!catalog.contains(group, field)) {
                        errors.add("feature desconhecida: " + field);
                    }
                } else {
                    errors.add("variável desconhecida: " + group);
                }
            } else if (expr.exprKind().getKind() == Kind.IDENT && !isSelectOperand(node)) {
                errors.add("variável desconhecida: " + expr.ident().name());
            }
        });
    }

    private static boolean isSelectOperand(CelNavigableExpr node) {
        return node.parent().map(p -> p.expr().exprKind().getKind() == Kind.SELECT).orElse(false);
    }

    private static void checkOverrides(RuleDefinition rule, Set<String> errors) {
        if (rule.paramOverrides() == null) {
            return;
        }
        var defined = rule.params() == null ? Set.<String>of() : rule.params().keySet();
        for (Map.Entry<String, Map<String, Object>> segment : rule.paramOverrides().entrySet()) {
            for (String key : segment.getValue().keySet()) {
                if (!defined.contains(key)) {
                    errors.add("override do segmento " + segment.getKey() + " usa parâmetro não definido: " + key);
                }
            }
        }
    }

    private static List<String> issues(CelValidationResult result) {
        var messages = new ArrayList<String>();
        for (String line : result.getErrorString().split("\\R")) {
            if (line.startsWith("ERROR: ")) {
                messages.add(line.substring("ERROR: <input>:".length()).strip());
            }
        }
        return messages.isEmpty() ? List.of(result.getErrorString().strip()) : messages;
    }

    private static CompilationResult result(RuleDefinition rule, Set<String> errors) {
        return new CompilationResult(rule.ruleId(), List.copyOf(errors));
    }

    /** Só é chamado depois de conferir que não há erro, então a exceção do CEL não ocorre. */
    private static dev.cel.common.CelAbstractSyntaxTree astOf(CelValidationResult result) {
        try {
            return result.getAst();
        } catch (dev.cel.common.CelValidationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static CelType celType(FeatureType type) {
        return switch (type) {
            case INT -> SimpleType.INT;
            case BOOL -> SimpleType.BOOL;
            case STRING -> SimpleType.STRING;
        };
    }
}
