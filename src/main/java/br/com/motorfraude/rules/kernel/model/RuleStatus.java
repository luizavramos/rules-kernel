package br.com.motorfraude.rules.kernel.model;

/** Situação de uma versão de regra. Só ACTIVE soma pontos; SHADOW é avaliada e registrada, sem efeito. */
public enum RuleStatus {
    DRAFT,
    ACTIVE,
    SHADOW,
    DISABLED
}
