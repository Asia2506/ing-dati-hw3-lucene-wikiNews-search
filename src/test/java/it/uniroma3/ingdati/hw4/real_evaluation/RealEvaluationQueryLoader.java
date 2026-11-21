package it.uniroma3.ingdati.hw4.real_evaluation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the query and pilot fields from the real-evaluation JSON resource. */
public final class RealEvaluationQueryLoader {

    private static final Pattern QUERY = Pattern.compile(
            "\\{\\s*\"id\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*"
                    + "\"query\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*"
                    + "\"pilot\"\\s*:\\s*(true|false)", Pattern.DOTALL);

    public List<RealEvaluationQuery> load() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/real-evaluation/queries.json")) {
            if (input == null) {
                throw new IOException("Missing resource /real-evaluation/queries.json");
            }
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = QUERY.matcher(json);
            List<RealEvaluationQuery> queries = new ArrayList<>();
            while (matcher.find()) {
                queries.add(new RealEvaluationQuery(matcher.group(1), matcher.group(2),
                        Boolean.parseBoolean(matcher.group(3))));
            }
            if (queries.isEmpty()) {
                throw new IOException("No queries found in /real-evaluation/queries.json");
            }
            return List.copyOf(queries);
        }
    }
}
