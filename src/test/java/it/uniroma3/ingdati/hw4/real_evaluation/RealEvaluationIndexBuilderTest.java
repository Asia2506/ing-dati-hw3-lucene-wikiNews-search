package it.uniroma3.ingdati.hw4.real_evaluation;

import it.uniroma3.ingdati.hw4.indexing.WikiNewsAnalyzerFactory;
import org.apache.lucene.analysis.Analyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealEvaluationIndexBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsAnIsolatedIndexFromTheConfiguredWikiNewsDump() throws Exception {
        Path dumpPath = Path.of("data", "itwikinews_namespace_0_0.ndjson").toAbsolutePath();
        Path indexPath = tempDir.resolve("wikinews-real-evaluation-index");
        Analyzer analyzer = new WikiNewsAnalyzerFactory().createAnalyzer();
        try {
            RealEvaluationIndexBuilder.IndexBuildSummary summary =
                    new RealEvaluationIndexBuilder().build(dumpPath, indexPath, analyzer);

            assertTrue(summary.inputLines() > 0);
            assertTrue(summary.acceptedArticles() > 0);
            assertEquals(summary.acceptedArticles(), summary.indexedOperations());
            assertTrue(summary.documentsInIndex() > 0);
            System.out.printf("Real WikiNews evaluation index: lines=%d accepted=%d indexed=%d documents=%d%n",
                    summary.inputLines(), summary.acceptedArticles(), summary.indexedOperations(),
                    summary.documentsInIndex());
        } finally {
            analyzer.close();
        }
    }
}
