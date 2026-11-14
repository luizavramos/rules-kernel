package br.com.motorfraude.rules.kernel.evaluation;

import br.com.motorfraude.rules.kernel.catalog.FeatureCatalog;
import br.com.motorfraude.rules.kernel.catalog.FeatureType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

public final class EvaluationContext {

    public static final String SEGMENT = "profile.segment";

    private final Map<String, Object> values;

    private EvaluationContext(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> values() {
        return values;
    }

    public String segment() {
        return (String) values.get(SEGMENT);
    }

    public static final class Builder {
        private static final FeatureCatalog CATALOG = FeatureCatalog.standardCatalog();
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Builder put(String qualifiedName, Object value) {
            FeatureType type = CATALOG.features().get(qualifiedName);
            if (type == null) {
                throw new IllegalArgumentException("feature desconhecida: " + qualifiedName);
            }
            values.put(qualifiedName, normalize(qualifiedName, type, value));
            return this;
        }

        public EvaluationContext build() {
            var missing = new TreeSet<>(CATALOG.features().keySet());
            missing.removeAll(values.keySet());
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("faltam features: " + missing);
            }
            return new EvaluationContext(new LinkedHashMap<>(values));
        }

        private static Object normalize(String name, FeatureType type, Object value) {
            return switch (type) {
                case INT -> {
                    if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
                        yield ((Number) value).longValue();
                    }
                    throw new IllegalArgumentException(name + " deve ser inteiro (recebido: " + describe(value) + ")");
                }
                case BOOL -> {
                    if (value instanceof Boolean) {
                        yield value;
                    }
                    throw new IllegalArgumentException(name + " deve ser verdadeiro ou falso (recebido: " + describe(value) + ")");
                }
                case STRING -> {
                    if (value instanceof String) {
                        yield value;
                    }
                    throw new IllegalArgumentException(name + " deve ser texto (recebido: " + describe(value) + ")");
                }
            };
        }

        private static String describe(Object value) {
            return value == null ? "nulo" : value.getClass().getSimpleName();
        }
    }
}
