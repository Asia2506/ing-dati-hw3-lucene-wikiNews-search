package it.uniroma3.ingdati.hw4.evaluation;

import it.uniroma3.ingdati.hw4.evaluation.model.BenchmarkQuery;
import it.uniroma3.ingdati.hw4.evaluation.model.EvaluationReport;
import it.uniroma3.ingdati.hw4.evaluation.runner.BenchmarkDataLoader;
import it.uniroma3.ingdati.hw4.evaluation.runner.IREvaluationRunner;
import it.uniroma3.ingdati.hw4.indexing.WikiNewsAnalyzerFactory;
import it.uniroma3.ingdati.hw4.indexing.WikiNewsIndexer;
import it.uniroma3.ingdati.hw4.model.NewsArticle;
import it.uniroma3.ingdati.hw4.search.Bm25RankingStrategy;
import it.uniroma3.ingdati.hw4.search.NewsQueryBuilder;
import it.uniroma3.ingdati.hw4.search.TfIdfRankingStrategy;
import it.uniroma3.ingdati.hw4.search.WikiNewsSearchService;
import org.apache.lucene.analysis.Analyzer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Evaluates BM25 and TF-IDF with the benchmark queries using the Cranfield methodology. */
class IREvaluationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Evaluate the WikiNews search service with the benchmark")
    void testIREvaluation() throws Exception {
        Analyzer analyzer = new WikiNewsAnalyzerFactory().createAnalyzer();
        try {
            Path indexPath = tempDir.resolve("evaluation-index");
            List<NewsArticle> corpus = buildEvaluationCorpus();
            assertThat(new WikiNewsIndexer(indexPath, analyzer).indexArticles(corpus))
                    .isEqualTo(corpus.size());

            List<BenchmarkQuery> queries = new BenchmarkDataLoader().loadBenchmarkDataset();
            assertThat(queries).hasSize(100);

            WikiNewsSearchService searchService = new WikiNewsSearchService(
                    indexPath, analyzer, new NewsQueryBuilder(analyzer));
            IREvaluationRunner runner = new IREvaluationRunner(searchService);
            EvaluationReport bm25 = runner.evaluate("BM25 (ItalianAnalyzer)", new Bm25RankingStrategy(), queries);
            EvaluationReport tfidf = runner.evaluate("TF-IDF (ItalianAnalyzer)", new TfIdfRankingStrategy(), queries);

            printComparisonSummary(bm25, tfidf);
            assertThat(bm25.getScore("P@5")).isGreaterThan(0.0);
            assertThat(bm25.getScore("MAP")).isGreaterThan(0.0);
            assertThat(bm25.getScore("MRR")).isGreaterThan(0.0);
            assertThat(bm25.getScore("NDCG@10")).isGreaterThan(0.0);
        } finally {
            analyzer.close();
        }
    }

    private List<NewsArticle> buildEvaluationCorpus() {
        List<NewsArticle> articles = new ArrayList<>(200);
        for (int i = 1; i <= 100; i++) {
            String topic = String.format("argomento%03d", i);
            articles.add(article(i * 2L - 1, topic + " notizia principale",
                    "approfondimento su " + topic + " e sviluppi recenti"));
            articles.add(article(i * 2L, "aggiornamento " + topic,
                    "resoconto e dettagli relativi a " + topic));
        }
        return articles;
    }

    private NewsArticle article(long id, String title, String content) {
        return new NewsArticle(id, title, "https://it.wikinews.org/wiki/" + id,
                Instant.parse("2024-01-01T00:00:00Z"), null, List.of("Italia"), content);
    }

    private void printComparisonSummary(EvaluationReport bm25, EvaluationReport tfidf) {
        System.out.println("\n==========================================================================================");
        System.out.println("                     INFORMATION RETRIEVAL EXPERIMENTAL EVALUATION (CRANFIELD)");
        System.out.println("==========================================================================================");
        System.out.printf("%-38s | %-7s | %-7s | %-7s | %-7s | %-7s | %-8s | %-8s%n",
                "Configuration", "P@5", "P@10", "R@10", "MRR", "MAP", "NDCG@10", "Avg Lat.");
        System.out.println("------------------------------------------------------------------------------------------");
        printRow(bm25);
        printRow(tfidf);
        System.out.println("==========================================================================================\n");
    }

    private void printRow(EvaluationReport report) {
        System.out.printf("%-38s | %-7.4f | %-7.4f | %-7.4f | %-7.4f | %-7.4f | %-8.4f | %d ms%n",
                report.getConfigurationName(), report.getScore("P@5"), report.getScore("P@10"),
                report.getScore("R@10"), report.getScore("MRR"), report.getScore("MAP"),
                report.getScore("NDCG@10"), report.getAverageLatencyMs());
    }
}
