package it.uniroma3.ingdati.hw4.evaluation.runner;

import it.uniroma3.ingdati.hw4.evaluation.metrics.AveragePrecision;
import it.uniroma3.ingdati.hw4.evaluation.metrics.IRMetricStrategy;
import it.uniroma3.ingdati.hw4.evaluation.metrics.NdcgAtK;
import it.uniroma3.ingdati.hw4.evaluation.metrics.PrecisionAtK;
import it.uniroma3.ingdati.hw4.evaluation.metrics.RecallAtK;
import it.uniroma3.ingdati.hw4.evaluation.metrics.ReciprocalRank;
import it.uniroma3.ingdati.hw4.evaluation.model.BenchmarkQuery;
import it.uniroma3.ingdati.hw4.evaluation.model.EvaluationReport;
import it.uniroma3.ingdati.hw4.search.RankingStrategy;
import it.uniroma3.ingdati.hw4.search.SearchHitItem;
import it.uniroma3.ingdati.hw4.search.WikiNewsSearchService;

import java.util.List;

/**
 * Controller/Runner executing IR evaluation across all benchmark queries
 * and calculating aggregate metrics according to Cranfield methodology.
 */
public class IREvaluationRunner {

    private final WikiNewsSearchService searchService;
    private final List<IRMetricStrategy> metrics;

    public IREvaluationRunner(WikiNewsSearchService searchService) {
        this.searchService = searchService;
        this.metrics = List.of(
                new PrecisionAtK(5),
                new PrecisionAtK(10),
                new RecallAtK(10),
                new ReciprocalRank(),
                new AveragePrecision(),
                new NdcgAtK(10)
        );
    }

    /**
     * Evaluates a specific ranking configuration on a list of benchmark queries.
     */
    public EvaluationReport evaluate(String configName, RankingStrategy rankingStrategy, List<BenchmarkQuery> queries) throws Exception {
        EvaluationReport report = new EvaluationReport(configName);

        long totalLatencyMs = 0;
        int queryCount = queries.size();

        // Accumulated metric sums across queries
        double[] metricSums = new double[metrics.size()];

        for (BenchmarkQuery bq : queries) {
            long start = System.currentTimeMillis();
            var response = searchService.search(bq.getQuery(), null, null, null, rankingStrategy, 1, 20);
            long elapsed = System.currentTimeMillis() - start;
            totalLatencyMs += elapsed;

            List<String> retrievedDocIds = response.hits().stream()
                    .map(SearchHitItem::identifier)
                    .map(String::valueOf)
                    .toList();
            java.util.Map<String, Integer> numericQrels = new java.util.HashMap<>();
            bq.getQrels().forEach((id, grade) -> numericQrels.put(id, grade));
            BenchmarkQuery evaluationQuery = new BenchmarkQuery(bq.getId(), bq.getQuery(), numericQrels);

            // Compute each metric for this query
            for (int i = 0; i < metrics.size(); i++) {
                double score = metrics.get(i).calculate(evaluationQuery, retrievedDocIds);
                metricSums[i] += score;
            }
        }

        // Store averaged results into the report
        for (int i = 0; i < metrics.size(); i++) {
            double meanScore = metricSums[i] / (queryCount > 0 ? queryCount : 1);
            report.addMetricScore(metrics.get(i).getName(), meanScore);
        }

        report.setAverageLatencyMs(totalLatencyMs / (queryCount > 0 ? queryCount : 1));
        return report;
    }
}

