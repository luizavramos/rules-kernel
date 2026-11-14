package br.com.motorfraude.rules.kernel.model;

import java.time.Instant;
import java.util.List;

public record RuleSetDefinition(long version, Instant publishedAt, Scoring scoring, List<RuleDefinition> rules) {}
