# T201 — Spike do cel-java (spec 002)

**Resultado: passou, com um desvio do plano (custo).** Versão testada: `dev.cel:cel:0.15.0` (a mais recente no Maven Central em 2026-11-14).
Testes do spike: `CelSpikeTest` e `CelSpikeFunctionsTest` (6 testes, descartáveis depois da T205).

## O que funcionou
| Necessidade | Resultado |
|---|---|
| Compilar `features.count5m > params.maxCount` com tipos | Funciona. Variáveis declaradas com nome qualificado (`addVar("features.count5m", INT)`) e `params` como `map(string, dyn)` |
| Avaliar com contexto | Funciona: `program.eval(Map.of("features.count5m", 7L, "params", Map.of("maxCount", 5L)))` dá `true`/`false` |
| Feature inexistente | Erro de compilação: `undeclared reference to 'features'`. A mensagem do plano ("feature desconhecida: contagem5m") precisa ser **montada pelo kernel** a partir do erro |
| Tipo errado (`tx.amount > "mil"`) | Erro de compilação: `found no matching overload for '_>_' applied to '(int, string)'` |
| Função própria `max(int,int)` | Funciona: `CelFunctionDecl` + `CelOverloadDecl.newGlobalOverload` na compilação e `CelFunctionBinding.from("max_int_int", Long.class, Long.class, Math::max)` no runtime |
| Erro na avaliação (divisão por zero) | `CelEvaluationException` ("/ by zero"): o kernel captura e conta a regra como não disparada |

## Desvio do plano: não existe estimativa de custo
O plano (§2) previa `CelEstimatedCost`. **O cel-java 0.15.0 não tem estimador de custo** (nenhuma classe de custo em nenhum artefato `dev.cel`; esse recurso existe no cel-go, não no Java).
Alternativas que existem e que cobrem o objetivo (FR-002, barrar expressão cara):
- `CelOptions.maxParseExpressionNodeCount` (testado: erro `expression node limit (20) exceeded`), `maxExpressionCodePointSize`, `maxParseRecursionDepth`;
- `AstDepthLimitValidator` (profundidade da árvore);
- `comprehensionMaxIterations` (laços; o catálogo atual não tem listas, então não há laço).

**Como o catálogo só tem escalares e booleanos, o custo é limitado pelo tamanho da expressão.** Proposta: o "custo máximo" vira um limite de nós e de profundidade configuráveis. Não muda a arquitetura (continua CEL), só o mecanismo da checagem. O plano e a spec (FR-002, T204) devem ser ajustados.

## Plano B (JSONLogic)
Não acionado: o CEL atende tipos, funções próprias e limites.
