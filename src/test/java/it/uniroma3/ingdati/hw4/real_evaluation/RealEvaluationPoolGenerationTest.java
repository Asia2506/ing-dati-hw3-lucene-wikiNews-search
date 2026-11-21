package it.uniroma3.ingdati.hw4.real_evaluation;

import it.uniroma3.ingdati.hw4.indexing.WikiNewsAnalyzerFactory;
import org.apache.lucene.analysis.Analyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealEvaluationPoolGenerationTest {

    @TempDir
    Path tempDir;

    @Test
    void runsTheFullBenchmarkAndExportsARelevanceJudgmentPool() throws Exception {
        Path projectDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path dumpPath = projectDir.resolve("data/itwikinews_namespace_0_0.ndjson");
        Path indexPath = tempDir.resolve("wikinews-real-evaluation-index");
        Path outputPath = Path.of(System.getProperty("realEvaluation.outputDir",
                projectDir.resolve("target/real-evaluation-artifacts-final").toString()));
        List<RealEvaluationQuery> queries = new RealEvaluationQueryLoader().load();
        assertEquals(100, queries.size());
        assertEquals(10, queries.stream().filter(RealEvaluationQuery::pilot).count());

        Analyzer analyzer = new WikiNewsAnalyzerFactory().createAnalyzer();
        try {
            new RealEvaluationIndexBuilder().build(dumpPath, indexPath, analyzer);
            RealEvaluationPoolGenerator.PoolSummary summary = new RealEvaluationPoolGenerator()
                    .generate(indexPath, outputPath, queries, analyzer);

            assertEquals(100, summary.queryCount());
            assertTrue(summary.rawResultCount() > 0);
            System.out.printf("Real WikiNews retrieval pool: queries=%d rawResults=%d zeroResultRuns=%d%n",
                    summary.queryCount(), summary.rawResultCount(), summary.zeroResultQueries());
            System.out.println("Blinded candidate pool: " + summary.judgmentPool().toAbsolutePath());
            System.out.println("Raw ranking runs: " + summary.retrievalRuns().toAbsolutePath());
            System.out.println("Qrel template: " + summary.qrelsTemplate().toAbsolutePath());
        } finally {
            analyzer.close();
        }
    }
}
