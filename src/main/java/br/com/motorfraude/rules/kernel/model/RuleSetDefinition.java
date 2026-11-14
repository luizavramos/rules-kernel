package br.com.motorfraude.rules.kernel.model;

import java.time.Instant;
import java.util.List;

/** Conjunto publicado: versão crescente, faixas de severidade e as regras ACTIVE e SHADOW. */
public record RuleSetDefinition(long version, Instant publishedAt, Scoring scoring, List<RuleDefinition> rules) {}
