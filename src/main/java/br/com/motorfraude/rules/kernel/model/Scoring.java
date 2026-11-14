package br.com.motorfraude.rules.kernel.model;

/** Faixas de severidade: pontos >= high é ALTA; >= medium é MÉDIA; abaixo disso, nenhuma. */
public record Scoring(int mediumThreshold, int highThreshold) {}
