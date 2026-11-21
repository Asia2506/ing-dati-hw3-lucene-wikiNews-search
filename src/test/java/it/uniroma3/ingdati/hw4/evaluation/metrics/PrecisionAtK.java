package it.uniroma3.ingdati.hw4.evaluation.metrics;

import it.uniroma3.ingdati.hw4.evaluation.model.BenchmarkQuery;
import java.util.List;

/**
 * Calculates Precision at rank k (P@k).
 * P@k = (Number of relevant documents in top k) / k
 */
public class PrecisionAtK implements IRMetricStrategy {

    private final int k;

    public PrecisionAtK(int k) {
        this.k = k;
    }

    @Override
    public String getName() {
        return "P@" + k;
    }

    @Override
    public double calculate(BenchmarkQuery query, List<String> rankedDocIds) {
        if (rankedDocIds == null || rankedDocIds.isEmpty() || k <= 0) {
            return 0.0;
        }

        int limit = Math.min(k, rankedDocIds.size());
        int relevantRetrieved = 0;

        for (int i = 0; i < limit; i++) {
            if (query.isRelevant(rankedDocIds.get(i))) {
                relevantRetrieved++;
            }
        }

        return (double) relevantRetrieved / k;
    }
}

