package it.uniroma3.ingdati.hw4.real_evaluation;

import it.uniroma3.ingdati.hw4.indexing.WikiNewsAnalyzerFactory;
import it.uniroma3.ingdati.hw4.search.Bm25RankingStrategy;
import it.uniroma3.ingdati.hw4.search.NewsQueryBuilder;
import it.uniroma3.ingdati.hw4.search.RankingStrategy;
import it.uniroma3.ingdati.hw4.search.SearchHitItem;
import it.uniroma3.ingdati.hw4.search.TfIdfRankingStrategy;
import it.uniroma3.ingdati.hw4.search.WikiNewsSearchService;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runs both ranking models and exports a blinded, deduplicated pool for manual relevance judgments. */
public final class RealEvaluationPoolGenerator {

    private static final int POOL_DEPTH = 20;

    public PoolSummary generate(Path indexPath, Path outputDirectory,
                                List<RealEvaluationQuery> queries, Analyzer analyzer) throws Exception {
        Files.createDirectories(outputDirectory);
        Path poolPath = outputDirectory.resolve("judgment_pool.csv");
        Path runsPath = outputDirectory.resolve("retrieval_runs.tsv");
        Path qrelsTemplatePath = outputDirectory.resolve("qrels_template.tsv");
        if (Files.exists(poolPath) || Files.exists(runsPath) || Files.exists(qrelsTemplatePath)) {
            throw new IOException("Pool output already exists; choose a new output directory: " + outputDirectory);
        }

        WikiNewsSearchService service = new WikiNewsSearchService(
                indexPath, analyzer, new NewsQueryBuilder(analyzer));
        RankingStrategy[] strategies = {new Bm25RankingStrategy(), new TfIdfRankingStrategy()};
        long totalHits = 0;
        int zeroResultQueries = 0;

        try (BufferedWriter pool = Files.newBufferedWriter(poolPath, StandardCharsets.UTF_8);
             BufferedWriter runs = Files.newBufferedWriter(runsPath, StandardCharsets.UTF_8);
             BufferedWriter qrels = Files.newBufferedWriter(qrelsTemplatePath, StandardCharsets.UTF_8);
             Directory directory = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(directory)) {

            IndexSearcher indexSearcher = new IndexSearcher(reader);
            pool.write("query_id,query,article_id,title,url,categories,year,content,relevance");
            pool.newLine();
            runs.write("query_id\tranking\trank\tarticle_id\tscore\tquery_latency_ms");
            runs.newLine();
            qrels.write("query_id\tarticle_id\trelevance");
            qrels.newLine();

            for (RealEvaluationQuery query : queries) {
                Map<Long, Candidate> candidates = new LinkedHashMap<>();
                for (RankingStrategy strategy : strategies) {
                    var response = service.search(query.query(), null, null, null,
                            strategy, 1, POOL_DEPTH);
                    if (response.hits().isEmpty()) {
                        zeroResultQueries++;
                    }
                    String rankingName = strategy.name();
                    for (int rank = 0; rank < response.hits().size(); rank++) {
                        SearchHitItem hit = response.hits().get(rank);
                        totalHits++;
                        Candidate candidate = candidates.computeIfAbsent(hit.identifier(), ignored -> new Candidate(hit));
                        candidate.setRank(rankingName, rank + 1);
                        runs.write(query.id() + "\t" + rankingName + "\t" + (rank + 1) + "\t"
                                + hit.identifier() + "\t" + String.format(Locale.ROOT, "%.8f", hit.score())
                                + "\t" + response.elapsedMillis());
                        runs.newLine();
                    }
                }

                for (Candidate candidate : candidates.values()) {
                    Document stored = loadStoredDocument(indexSearcher, candidate.hit.identifier());
                    String content = stored.get(WikiNewsAnalyzerFactory.FIELD_CONTENT);
                    pool.write(csv(query.id()));
                    pool.write(',');
                    pool.write(csv(query.query()));
                    pool.write(',');
                    pool.write(csv(Long.toString(candidate.hit.identifier())));
                    pool.write(',');
                    pool.write(csv(candidate.hit.title()));
                    pool.write(',');
                    pool.write(csv(candidate.hit.url()));
                    pool.write(',');
                    pool.write(csv(String.join("; ", candidate.hit.categories())));
                    pool.write(',');
                    pool.write(csv(candidate.hit.year() == null ? "" : candidate.hit.year().toString()));
                    pool.write(',');
                    pool.write(csv(content == null ? "" : content));
                    pool.write(',');
                    pool.newLine();
                    qrels.write(query.id() + "\t" + candidate.hit.identifier() + "\t");
                    qrels.newLine();
                }
            }
        }
        return new PoolSummary(queries.size(), totalHits, zeroResultQueries, poolPath, runsPath, qrelsTemplatePath);
    }

    private Document loadStoredDocument(IndexSearcher searcher, long id) throws IOException {
        TopDocs matches = searcher.search(new TermQuery(new Term(
                WikiNewsAnalyzerFactory.FIELD_IDENTIFIER, Long.toString(id))), 1);
        if (matches.scoreDocs.length == 0) {
            throw new IOException("Search result ID is missing from evaluation index: " + id);
        }
        return searcher.storedFields().document(matches.scoreDocs[0].doc);
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static final class Candidate {
        private final SearchHitItem hit;
        private final Map<String, Integer> ranks = new LinkedHashMap<>();

        private Candidate(SearchHitItem hit) {
            this.hit = hit;
        }

        private void setRank(String ranking, int rank) {
            ranks.put(ranking, rank);
        }
    }

    public record PoolSummary(int queryCount, long rawResultCount, int zeroResultQueries,
                              Path judgmentPool, Path retrievalRuns, Path qrelsTemplate) {
    }
}
