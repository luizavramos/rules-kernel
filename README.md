# rules-kernel

Biblioteca com a **linguagem de regras** do motor: o modelo de uma regra, o catálogo de variáveis que ela pode usar, o compilador e o avaliador de expressões [CEL](https://github.com/google/cel-spec). É usada por dois serviços, para que os dois entendam a regra do mesmo jeito:

- `rule-management` compila a regra na hora de salvar (e ao rodar os casos de teste), para recusar expressão inválida antes de ela chegar ao motor;
- `detection-engine` compila o conjunto de regras ativo e o avalia a cada transação, em memória.

Não tem servidor, banco nem Kafka. Só depende do `cel-java` (`dev.cel:cel`).

## O que tem dentro

| Pacote | O que faz |
|---|---|
| `model` | `RuleDefinition` (id, versão, status, expressão, parâmetros, ajustes por segmento, peso e texto do motivo), `RuleSetDefinition` (versão, `Scoring` e a lista de regras) e `RuleStatus` (`ACTIVE` ou `SHADOW`) |
| `catalog` | `FeatureCatalog`: as variáveis permitidas e o tipo de cada uma (inteiro, booleano ou texto) |
| `compiler` | `RuleCompiler`: valida e compila. `compile(regra)` devolve o resultado de uma regra; `compileAll(conjunto)` devolve um `CompiledRuleSet` ou lança `RuleSetCompilationException` com todas as falhas |
| `evaluation` | `EvaluationContext` (os valores de uma transação), `Evaluation` (resultado), `RuleHit`, `RuleError` e `Severity` |

## As variáveis que uma regra enxerga

Uma regra só pode citar variáveis do catálogo; qualquer outro nome é erro de compilação.

| Grupo | Variáveis |
|---|---|
| `tx` | `amount`, `type`, `channel`, `hourOfDay`, `counterpartyBank` |
| `profile` | `segment`, `accountAgeDays`, `hoursSinceCredentialChange` |
| `features` | `count1m`, `count5m`, `count1h`, `count24h`, `count30d`, `sum1h`, `sum24h`, `avgAmount30d`, `maxAmount90d`, `distinctBeneficiaries24h`, `isNewBeneficiary`, `isNewDevice` |
| `lists` | `counterpartyFlagged`, `deviceFlagged` |
| `params` | os parâmetros da própria regra, como `params.limit` |

Valores monetários são inteiros (centavos). A função `max(a, b)` para inteiros também está disponível.

## Como a avaliação funciona

1. Cada regra tem uma expressão que devolve verdadeiro ou falso, por exemplo `tx.amount > params.limit`.
2. Os `params` podem ser trocados por segmento do cliente (`paramOverrides`), então o mesmo limite pode ser diferente para quem tem outro perfil.
3. Regra `ACTIVE` que dispara soma o seu `weight` ao score. Regra `SHADOW` que dispara só aparece em `shadowHits`, sem pontuar: serve para testar uma regra com tráfego real antes de ativá-la.
4. O score vira severidade pelos limites do conjunto (`Scoring`): `MEDIUM` a partir do limite médio, `HIGH` a partir do alto, `NONE` abaixo. No motor os limites são 40 e 70.
5. Uma regra que dá erro ao avaliar (divisão por zero, por exemplo) não derruba a transação: entra em `errors` e conta como não disparada.

O compilador também limita o tamanho da expressão (2.000 caracteres, 200 nós e profundidade 24 por padrão), para que uma regra mal escrita não custe caro na decisão em tempo real.

## Exemplo

```java
var compiler = new RuleCompiler();
var set = compiler.compileAll(new RuleSetDefinition(1, Instant.now(), new Scoring(40, 70), List.of(
        new RuleDefinition("valor-alto", 1, RuleStatus.ACTIVE, "tx.amount > params.limit",
                Map.of("limit", 500_000), Map.of(), 70, "Valor acima de {params.limit}"))));

var context = EvaluationContext.builder()
        .put("tx.amount", 900_000L)
        // ... uma chamada put para cada variável do catálogo; build() recusa se faltar alguma
        .build();

Evaluation result = set.evaluate(context);   // score, severidade e as regras que dispararam
```

## Build e testes

```bash
./mvnw verify        # testes unitários e ArchUnit (não precisa de Docker)
./mvnw install       # instala em ~/.m2 para os serviços usarem
```

Precisa do `motor-fraude-parent` instalado antes. A ordem completa está no "Passo a passo" do repositório de visão geral: https://github.com/luizavramos/fraud-detection-engine
