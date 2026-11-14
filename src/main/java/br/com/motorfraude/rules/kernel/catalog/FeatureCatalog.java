package br.com.motorfraude.rules.kernel.catalog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * O vocabulário das regras: as únicas variáveis que uma expressão pode usar (plano 002, §3.1).
 * Uma feature nova exige código no motor, por isso é a única mudança de regra que pede deploy.
 */
public final class FeatureCatalog {

    /** Grupo reservado para os parâmetros da própria regra (mapa aberto). */
    public static final String PARAMS = "params";

    private static final FeatureCatalog STANDARD = standard();

    private final Map<String, FeatureType> features;
    private final Set<String> groups;

    private FeatureCatalog(Map<String, FeatureType> features) {
        this.features = Map.copyOf(features);
        var names = new java.util.TreeSet<String>();
        features.keySet().forEach(name -> names.add(name.substring(0, name.indexOf('.'))));
        this.groups = Set.copyOf(names);
    }

    public static FeatureCatalog standardCatalog() {
        return STANDARD;
    }

    /** Nome qualificado (ex.: {@code features.count5m}) para o tipo. */
    public Map<String, FeatureType> features() {
        return features;
    }

    /** Grupos conhecidos: tx, profile, features, lists. */
    public Set<String> groups() {
        return groups;
    }

    public boolean isGroup(String name) {
        return groups.contains(name);
    }

    public boolean contains(String group, String field) {
        return features.containsKey(group + "." + field);
    }

    private static FeatureCatalog standard() {
        var m = new LinkedHashMap<String, FeatureType>();
        m.put("tx.amount", FeatureType.INT);
        m.put("tx.type", FeatureType.STRING);
        m.put("tx.channel", FeatureType.STRING);
        m.put("tx.hourOfDay", FeatureType.INT);
        m.put("tx.counterpartyBank", FeatureType.STRING);

        m.put("profile.segment", FeatureType.STRING);
        m.put("profile.accountAgeDays", FeatureType.INT);
        m.put("profile.hoursSinceCredentialChange", FeatureType.INT);

        for (var name : new String[] {"count1m", "count5m", "count1h", "count24h", "count30d",
                "sum1h", "sum24h", "avgAmount30d", "maxAmount90d", "distinctBeneficiaries24h"}) {
            m.put("features." + name, FeatureType.INT);
        }
        m.put("features.isNewBeneficiary", FeatureType.BOOL);
        m.put("features.isNewDevice", FeatureType.BOOL);

        m.put("lists.counterpartyFlagged", FeatureType.BOOL);
        m.put("lists.deviceFlagged", FeatureType.BOOL);
        return new FeatureCatalog(m);
    }
}
