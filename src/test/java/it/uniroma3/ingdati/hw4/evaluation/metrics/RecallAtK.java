package it.uniroma3.ingdati.hw4.evaluation.metrics;

import it.uniroma3.ingdati.hw4.evaluation.model.BenchmarkQuery;
import java.util.List;

/**
 * Calculates Recall at rank k (R@k).
 * R@k = (Number of relevant documents in top k) / (Total known relevant documents)
 */
public class RecallAtK implements IRMetricStrategy {

    private final int k;

    public RecallAtK(int k) {
        this.k = k;
    }

    @Override
    public String getName() {
        return "R@" + k;
    }

    @Override
    public double calculate(BenchmarkQuery query, List<String> rankedDocIds) {
        int totalRelevant = query.getTotalRelevantDocuments();
        if (totalRelevant == 0 || rankedDocIds == null || rankedDocIds.isEmpty()) {
            return 0.0;
        }

        int limit = Math.min(k, rankedDocIds.size());
        int relevantRetrieved = 0;

        for (int i = 0; i < limit; i++) {
            if (query.isRelevant(rankedDocIds.get(i))) {
                relevantRetrieved++;
            }
        }

        return (double) relevantRetrieved / totalRelevant;
    }
}

