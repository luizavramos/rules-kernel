package br.com.motorfraude.rules.kernel;

import br.com.motorfraude.rules.kernel.evaluation.EvaluationContext;

/** Contexto neutro com todas as features do catálogo; cada teste muda só o que importa. */
public final class TestContexts {

    private TestContexts() {}

    public static EvaluationContext.Builder neutral() {
        return EvaluationContext.builder()
                .put("tx.amount", 10_000L)
                .put("tx.type", "PIX")
                .put("tx.channel", "MOBILE")
                .put("tx.hourOfDay", 14L)
                .put("tx.counterpartyBank", "001")
                .put("profile.segment", "PF")
                .put("profile.accountAgeDays", 400L)
                .put("profile.hoursSinceCredentialChange", -1L)
                .put("features.count1m", 0L)
                .put("features.count5m", 1L)
                .put("features.count1h", 1L)
                .put("features.count24h", 3L)
                .put("features.count30d", 40L)
                .put("features.sum1h", 10_000L)
                .put("features.sum24h", 30_000L)
                .put("features.avgAmount30d", 20_000L)
                .put("features.maxAmount90d", 90_000L)
                .put("features.isNewBeneficiary", false)
                .put("features.isNewDevice", false)
                .put("features.distinctBeneficiaries24h", 2L)
                .put("lists.counterpartyFlagged", false)
                .put("lists.deviceFlagged", false);
    }
}
