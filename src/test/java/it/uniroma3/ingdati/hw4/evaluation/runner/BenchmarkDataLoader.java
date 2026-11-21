package it.uniroma3.ingdati.hw4.evaluation.runner;

import it.uniroma3.ingdati.hw4.evaluation.model.BenchmarkQuery;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads the small benchmark JSON format used by evaluation tests, without extra dependencies. */
public class BenchmarkDataLoader {

    private static final Pattern QUERY_OBJECT = Pattern.compile(
            "\\{\\s*\\\"id\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"\\s*,\\s*"
                    + "\\\"query\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"\\s*,\\s*"
                    + "\\\"qrels\\\"\\s*:\\s*\\{([^}]*)}\\s*}", Pattern.DOTALL);
    private static final Pattern QREL = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*(-?\\d+)");

    public List<BenchmarkQuery> loadBenchmarkDataset() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/benchmark/benchmark_dataset.json")) {
            if (input == null) {
                throw new IllegalStateException("Benchmark dataset /benchmark/benchmark_dataset.json not found");
            }
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            List<BenchmarkQuery> queries = new ArrayList<>();
            Matcher objects = QUERY_OBJECT.matcher(json);
            while (objects.find()) {
                Map<String, Integer> qrels = new LinkedHashMap<>();
                Matcher entries = QREL.matcher(objects.group(3));
                while (entries.find()) {
                    qrels.put(entries.group(1), Integer.parseInt(entries.group(2)));
                }
                queries.add(new BenchmarkQuery(objects.group(1), objects.group(2), qrels));
            }
            if (queries.isEmpty()) {
                throw new IllegalArgumentException("No benchmark queries found in benchmark_dataset.json");
            }
            return queries;
        }
    }
}
