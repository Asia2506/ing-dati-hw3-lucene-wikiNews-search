package it.uniroma3.ingdati.hw4.evaluation.metrics;

import it.uniroma3.ingdati.hw4.evaluation.model.BenchmarkQuery;
import java.util.List;

/**
 * Calculates Average Precision (AP) for a query.
 * Averaged across all queries, this yields Mean Average Precision (MAP).
 */
public class AveragePrecision implements IRMetricStrategy {

    @Override
    public String getName() {
        return "MAP";
    }

    @Override
    public double calculate(BenchmarkQuery query, List<String> rankedDocIds) {
        int totalRelevant = query.getTotalRelevantDocuments();
        if (totalRelevant == 0 || rankedDocIds == null || rankedDocIds.isEmpty()) {
            return 0.0;
        }

        double cumulativePrecision = 0.0;
        int relevantSoFar = 0;

        for (int rank = 0; rank < rankedDocIds.size(); rank++) {
            if (query.isRelevant(rankedDocIds.get(rank))) {
                relevantSoFar++;
                double precisionAtRank = (double) relevantSoFar / (rank + 1);
                cumulativePrecision += precisionAtRank;
            }
        }

        return cumulativePrecision / totalRelevant;
    }
}

