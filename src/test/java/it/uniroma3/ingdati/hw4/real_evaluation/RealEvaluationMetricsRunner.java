package it.uniroma3.ingdati.hw4.real_evaluation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Computes binary relevance metrics from blinded qrels and the saved BM25/TF-IDF runs. */
public final class RealEvaluationMetricsRunner {

    public List<RankingSummary> evaluate(List<RealEvaluationQuery> queries, Path qrelsPath, Path runsPath)
            throws IOException {
        Map<String, Map<String, Integer>> qrels = loadQrels(qrelsPath);
        Map<String, Map<String, List<RunHit>>> runs = loadRuns(runsPath);
        List<RankingSummary> summaries = new ArrayList<>();

        for (String ranking : List.of("BM25", "TF-IDF")) {
            double p5 = 0;
            double p10 = 0;
            double recall10 = 0;
            double reciprocalRank = 0;
            double averagePrecision20 = 0;
            double latencyMs = 0;
            int queriesWithoutRelevantDocuments = 0;

            for (RealEvaluationQuery query : queries) {
                Map<String, Integer> queryQrels = qrels.get(query.id());
                List<RunHit> hits = runs.getOrDefault(query.id(), Map.of()).get(ranking);
                if (queryQrels == null || queryQrels.isEmpty()) {
                    throw new IOException("No judgments found for query " + query.id());
                }
                boolean hasRelevantDocuments = queryQrels.values().stream().anyMatch(value -> value == 1);
                if (!hasRelevantDocuments) queriesWithoutRelevantDocuments++;
                if (hits == null || hits.isEmpty()) {
                    throw new IOException("No saved results for " + query.id() + " / " + ranking);
                }
                for (RunHit hit : hits) {
                    if (!queryQrels.containsKey(hit.articleId)) {
                        throw new IOException("Missing manual judgment for pool document "
                                + query.id() + "/" + hit.articleId);
                    }
                }

                Set<String> relevant = new HashSet<>();
                queryQrels.forEach((id, grade) -> {
                    if (grade == 1) relevant.add(id);
                });
                int found5 = countRelevant(hits, relevant, 5);
                int found10 = countRelevant(hits, relevant, 10);
                p5 += found5 / 5.0;
                p10 += found10 / 10.0;
                // With no relevant document in the judged pool, recall is undefined; report it as 0
                // and expose the count of such queries alongside the aggregate.
                recall10 += relevant.isEmpty() ? 0 : (double) found10 / relevant.size();
                reciprocalRank += reciprocalRankAt20(hits, relevant);
                averagePrecision20 += averagePrecisionAt20(hits, relevant);
                latencyMs += hits.get(0).latencyMs;
            }

            double n = queries.size();
            summaries.add(new RankingSummary(ranking, p5 / n, p10 / n, recall10 / n,
                    reciprocalRank / n, averagePrecision20 / n, latencyMs / n,
                    queriesWithoutRelevantDocuments));
        }
        return List.copyOf(summaries);
    }

    public void writeReport(Path reportPath, List<RankingSummary> summaries) throws IOException {
        Files.createDirectories(reportPath.toAbsolutePath().getParent());
        StringBuilder report = new StringBuilder("ranking\tP@5\tP@10\tR@10\tMRR@20\tMAP@20\taverage_latency_ms\tqueries_without_relevant_judgments\n");
        for (RankingSummary summary : summaries) {
            report.append(String.format(java.util.Locale.ROOT, "%s\t%.6f\t%.6f\t%.6f\t%.6f\t%.6f\t%.3f\t%d%n",
                    summary.ranking(), summary.precisionAt5(), summary.precisionAt10(), summary.recallAt10(),
                    summary.meanReciprocalRankAt20(), summary.meanAveragePrecisionAt20(), summary.averageLatencyMs(),
                    summary.queriesWithoutRelevantDocuments()));
        }
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);
    }

    private Map<String, Map<String, Integer>> loadQrels(Path path) throws IOException {
        Map<String, Map<String, Integer>> qrels = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            if (fields.length != 3 || fields[2].isBlank()) {
                throw new IOException("Incomplete qrel at " + path + ":" + (i + 1)
                        + "; fill every relevance field with 0 or 1");
            }
            int grade;
            try {
                grade = Integer.parseInt(fields[2].trim());
            } catch (NumberFormatException ex) {
                throw new IOException("Invalid relevance label at " + path + ":" + (i + 1), ex);
            }
            if (grade != 0 && grade != 1) {
                throw new IOException("Relevance labels must be 0 or 1 at " + path + ":" + (i + 1));
            }
            Integer prior = qrels.computeIfAbsent(fields[0], ignored -> new LinkedHashMap<>())
                    .put(fields[1], grade);
            if (prior != null) throw new IOException("Duplicate qrel for " + fields[0] + "/" + fields[1]);
        }
        return qrels;
    }

    private Map<String, Map<String, List<RunHit>>> loadRuns(Path path) throws IOException {
        Map<String, Map<String, List<RunHit>>> runs = new HashMap<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String[] fields = lines.get(i).split("\\t", -1);
            if (fields.length != 6) throw new IOException("Malformed run row at " + path + ":" + (i + 1));
            try {
                RunHit hit = new RunHit(fields[3], Integer.parseInt(fields[2]),
                        Double.parseDouble(fields[4]), Long.parseLong(fields[5]));
                runs.computeIfAbsent(fields[0], ignored -> new HashMap<>())
                        .computeIfAbsent(fields[1], ignored -> new ArrayList<>()).add(hit);
            } catch (NumberFormatException ex) {
                throw new IOException("Malformed numeric value at " + path + ":" + (i + 1), ex);
            }
        }
        runs.values().forEach(byRanking -> byRanking.values().forEach(hits ->
                hits.sort(Comparator.comparingInt(RunHit::rank))));
        return runs;
    }

    private int countRelevant(List<RunHit> hits, Set<String> relevant, int k) {
        int count = 0;
        for (int i = 0; i < Math.min(k, hits.size()); i++) {
            if (relevant.contains(hits.get(i).articleId)) count++;
        }
        return count;
    }

    private double reciprocalRankAt20(List<RunHit> hits, Set<String> relevant) {
        for (RunHit hit : hits) {
            if (relevant.contains(hit.articleId)) return 1.0 / hit.rank;
        }
        return 0;
    }

    private double averagePrecisionAt20(List<RunHit> hits, Set<String> relevant) {
        if (relevant.isEmpty()) return 0;
        int seenRelevant = 0;
        double precisionSum = 0;
        for (RunHit hit : hits) {
            if (relevant.contains(hit.articleId)) {
                seenRelevant++;
                precisionSum += (double) seenRelevant / hit.rank;
            }
        }
        return precisionSum / relevant.size();
    }

    private record RunHit(String articleId, int rank, double score, long latencyMs) {
    }

    public record RankingSummary(String ranking, double precisionAt5, double precisionAt10, double recallAt10,
                                 double meanReciprocalRankAt20, double meanAveragePrecisionAt20,
                                 double averageLatencyMs, int queriesWithoutRelevantDocuments) {
    }
}
