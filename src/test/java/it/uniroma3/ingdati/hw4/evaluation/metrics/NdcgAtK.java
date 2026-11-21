package it.uniroma3.ingdati.hw4.evaluation.metrics;

import it.uniroma3.ingdati.hw4.evaluation.model.BenchmarkQuery;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calculates Normalized Discounted Cumulative Gain at rank k (NDCG@k).
 * Handles graded relevance judgments (e.g. 0=non-relevant, 1=relevant, 2=highly relevant).
 */
public class NdcgAtK implements IRMetricStrategy {

    private final int k;

    public NdcgAtK(int k) {
        this.k = k;
    }

    @Override
    public String getName() {
        return "NDCG@" + k;
    }

    @Override
    public double calculate(BenchmarkQuery query, List<String> rankedDocIds) {
        if (rankedDocIds == null || rankedDocIds.isEmpty() || k <= 0) {
            return 0.0;
        }

        // 1. Calculate DCG@k
        double dcg = 0.0;
        int limit = Math.min(k, rankedDocIds.size());

        for (int i = 0; i < limit; i++) {
            int grade = query.getRelevanceGrade(rankedDocIds.get(i));
            if (grade > 0) {
                // DCG formula: (2^rel - 1) / log2(rank + 1)
                double gain = Math.pow(2, grade) - 1.0;
                double discount = Math.log(i + 2) / Math.log(2); // log2(i + 2)
                dcg += gain / discount;
            }
        }

        // 2. Calculate Ideal DCG@k (IDCG@k)
        List<Integer> allGrades = new ArrayList<>(query.getQrels().values());
        allGrades.sort(Collections.reverseOrder());

        double idcg = 0.0;
        int idealLimit = Math.min(k, allGrades.size());
        for (int i = 0; i < idealLimit; i++) {
            int grade = allGrades.get(i);
            if (grade > 0) {
                double gain = Math.pow(2, grade) - 1.0;
                double discount = Math.log(i + 2) / Math.log(2);
                idcg += gain / discount;
            }
        }

        if (idcg == 0.0) {
            return 0.0;
        }

        return dcg / idcg;
    }
}

