package it.uniroma3.ingdati.hw4.evaluation.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates benchmark evaluation scores across all queries.
 */
public class EvaluationReport {

    private final String configurationName;
    private final Map<String, Double> metricScores;
    private long averageLatencyMs;

    public EvaluationReport(String configurationName) {
        this.configurationName = configurationName;
        this.metricScores = new LinkedHashMap<>();
    }

    public void addMetricScore(String metricName, double score) {
        metricScores.put(metricName, score);
    }

    public String getConfigurationName() {
        return configurationName;
    }

    public Map<String, Double> getMetricScores() {
        return metricScores;
    }

    public double getScore(String metricName) {
        return metricScores.getOrDefault(metricName, 0.0);
    }

    public long getAverageLatencyMs() {
        return averageLatencyMs;
    }

    public void setAverageLatencyMs(long averageLatencyMs) {
        this.averageLatencyMs = averageLatencyMs;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== Configuration: %s (Avg Latency: %d ms) ===%n", configurationName, averageLatencyMs));
        metricScores.forEach((m, s) -> sb.append(String.format("%-10s: %.4f%n", m, s)));
        return sb.toString();
    }
}

