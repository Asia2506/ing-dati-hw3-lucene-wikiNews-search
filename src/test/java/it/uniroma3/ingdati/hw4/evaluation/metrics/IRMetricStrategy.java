package it.uniroma3.ingdati.hw4.evaluation.metrics;

import it.uniroma3.ingdati.hw4.evaluation.model.BenchmarkQuery;
import java.util.List;

/**
 * Strategy interface (GoF) for Information Retrieval evaluation metrics.
 */
public interface IRMetricStrategy {

    /**
     * Metric name (e.g. "P@10", "MAP", "NDCG@10").
     */
    String getName();

    /**
     * Calculates the metric for a single query and its ranked retrieved doc IDs.
     *
     * @param query benchmark query with ground truth judgments
     * @param rankedDocIds ordered list of retrieved document IDs
     * @return score in [0.0, 1.0]
     */
    double calculate(BenchmarkQuery query, List<String> rankedDocIds);
}

