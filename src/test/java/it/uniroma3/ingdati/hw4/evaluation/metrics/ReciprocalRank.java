package it.uniroma3.ingdati.hw4.evaluation.metrics;

import it.uniroma3.ingdati.hw4.evaluation.model.BenchmarkQuery;
import java.util.List;

/**
 * Calculates Reciprocal Rank (RR): reciprocal of the position of the first relevant document.
 * Averaged across queries, this yields Mean Reciprocal Rank (MRR).
 */
public class ReciprocalRank implements IRMetricStrategy {

    @Override
    public String getName() {
        return "MRR";
    }

    @Override
    public double calculate(BenchmarkQuery query, List<String> rankedDocIds) {
        if (rankedDocIds == null || rankedDocIds.isEmpty()) {
            return 0.0;
        }

        for (int rank = 0; rank < rankedDocIds.size(); rank++) {
            if (query.isRelevant(rankedDocIds.get(rank))) {
                return 1.0 / (rank + 1);
            }
        }

        return 0.0;
    }
}

