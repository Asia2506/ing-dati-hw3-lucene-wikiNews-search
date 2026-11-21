package it.uniroma3.ingdati.hw4.real_evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealEvaluationMetricsRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void computesOnlyTheSpecifiedBinaryRelevanceMetrics() throws Exception {
        Path qrels = tempDir.resolve("qrels.tsv");
        Path runs = tempDir.resolve("runs.tsv");
        Set<String> candidateIds = new LinkedHashSet<>(List.of("11", "12"));
        for (int rank = 1; rank <= 20; rank++) {
            if (rank != 1 && rank != 3) candidateIds.add(Integer.toString(100 + rank));
            if (rank != 2 && rank != 4) candidateIds.add(Integer.toString(200 + rank));
        }
        StringBuilder qrelContents = new StringBuilder("query_id\tarticle_id\trelevance\n");
        for (String id : candidateIds) {
            qrelContents.append("Q001\t").append(id).append("\t")
                    .append(id.equals("11") || id.equals("12") ? 1 : 0).append('\n');
        }
        Files.writeString(qrels, qrelContents);
        StringBuilder contents = new StringBuilder("query_id\tranking\trank\tarticle_id\tscore\tquery_latency_ms\n");
        for (int rank = 1; rank <= 20; rank++) {
            long latency = 5;
            String bm25Id = rank == 1 ? "11" : rank == 3 ? "12" : Integer.toString(100 + rank);
            String tfIdfId = rank == 2 ? "11" : rank == 4 ? "12" : Integer.toString(200 + rank);
            contents.append("Q001\tBM25\t").append(rank).append('\t').append(bm25Id)
                    .append("\t1.0\t").append(latency).append('\n');
            contents.append("Q001\tTF-IDF\t").append(rank).append('\t').append(tfIdfId)
                    .append("\t1.0\t").append(latency + 2).append('\n');
        }
        Files.writeString(runs, contents);

        List<RealEvaluationMetricsRunner.RankingSummary> reports = new RealEvaluationMetricsRunner().evaluate(
                List.of(new RealEvaluationQuery("Q001", "test query", true)), qrels, runs);
        RealEvaluationMetricsRunner.RankingSummary bm25 = reports.get(0);
        RealEvaluationMetricsRunner.RankingSummary tfidf = reports.get(1);

        assertEquals(0.4, bm25.precisionAt5(), 1e-9);
        assertEquals(0.2, bm25.precisionAt10(), 1e-9);
        assertEquals(1.0, bm25.recallAt10(), 1e-9);
        assertEquals(1.0, bm25.meanReciprocalRankAt20(), 1e-9);
        assertEquals(5.0 / 6.0, bm25.meanAveragePrecisionAt20(), 1e-9);
        assertEquals(0.5, tfidf.meanReciprocalRankAt20(), 1e-9);

        Path reportPath = tempDir.resolve("report.tsv");
        new RealEvaluationMetricsRunner().writeReport(reportPath, reports);
        String report = Files.readString(reportPath);
        assertTrue(report.contains("MAP@20"));
        assertTrue(!report.contains("NDCG"));
    }

    @Test
    void rejectsUnjudgedPoolDocuments() throws Exception {
        Path qrels = tempDir.resolve("incomplete-qrels.tsv");
        Path runs = tempDir.resolve("runs.tsv");
        Files.writeString(qrels, "query_id\tarticle_id\trelevance\nQ001\t11\t1\n");
        StringBuilder contents = new StringBuilder("query_id\tranking\trank\tarticle_id\tscore\tquery_latency_ms\n");
        for (int rank = 1; rank <= 20; rank++) {
            contents.append("Q001\tBM25\t").append(rank).append('\t').append(rank == 1 ? "11" : "d" + rank)
                    .append("\t1.0\t1\n");
            contents.append("Q001\tTF-IDF\t").append(rank).append('\t').append(rank == 1 ? "11" : "e" + rank)
                    .append("\t1.0\t1\n");
        }
        Files.writeString(runs, contents);
        assertThrows(java.io.IOException.class, () -> new RealEvaluationMetricsRunner().evaluate(
                List.of(new RealEvaluationQuery("Q001", "test query", false)), qrels, runs));
    }
}
