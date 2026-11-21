package it.uniroma3.ingdati.hw4.real_evaluation;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runs after qrels.tsv has been completed with a 0/1 label for every pool row. */
class RealEvaluationMetricsTest {

    @Test
    void evaluatesTheSavedWikiNewsRunsAfterJudgmentsAreComplete() throws Exception {
        Path projectDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path defaultArtifactDir = projectDir.resolve("target/real-evaluation-artifacts-final");
        Path artifactDir = Path.of(System.getProperty("realEvaluation.artifactDir",
                defaultArtifactDir.toString())).toAbsolutePath();
        Path qrelsPath = Path.of(System.getProperty("realEvaluation.qrelsPath",
                projectDir.resolve("src/test/resources/real-evaluation/qrels.tsv").toString())).toAbsolutePath();
        Path runPath = artifactDir.resolve("retrieval_runs.tsv");
        Path reportPath = artifactDir.resolve("metrics_report.tsv");
        Assumptions.assumeTrue(Files.isRegularFile(qrelsPath), "Qrel file has not been created yet");
        Assumptions.assumeTrue(Files.isRegularFile(runPath), "Generate the real retrieval pool first");

        List<String> judgments = Files.readAllLines(qrelsPath, StandardCharsets.UTF_8);
        boolean complete = judgments.size() > 1 && judgments.subList(1, judgments.size()).stream()
                .filter(line -> !line.isBlank())
                .allMatch(line -> {
                    String[] fields = line.split("\\t", -1);
                    return fields.length == 3 && (fields[2].trim().equals("0") || fields[2].trim().equals("1"));
                });
        Assumptions.assumeTrue(complete,
                "Fill the relevance column in src/test/resources/real-evaluation/qrels.tsv with 0 or 1");

        List<RealEvaluationQuery> queries = new RealEvaluationQueryLoader().load();
        List<RealEvaluationMetricsRunner.RankingSummary> summaries =
                new RealEvaluationMetricsRunner().evaluate(queries, qrelsPath, runPath);
        assertEquals(2, summaries.size());

        RealEvaluationMetricsRunner runner = new RealEvaluationMetricsRunner();
        runner.writeReport(reportPath, summaries);
        System.out.println(Files.readString(reportPath, StandardCharsets.UTF_8));
        System.out.println("Report written to " + reportPath);
    }
}
