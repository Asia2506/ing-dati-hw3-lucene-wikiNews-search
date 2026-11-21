package it.uniroma3.ingdati.hw4.search;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import it.uniroma3.ingdati.hw4.indexing.WikiNewsAnalyzerFactory;
import it.uniroma3.ingdati.hw4.model.NewsArticle;

// Servizio centrale di ricerca su Lucene (scelte Q2 + F2):
// - Supporta ranking intercambiabile (BM25 / TF-IDF)
// - Calcola faccette sulle categorie tramite FacetsCollector
// - Genera snippet evidenziati tramite UnifiedHighlighter
public class WikiNewsSearchService {

    private static final int MAX_FACET_ITEMS = 15;
    private static final int FALLBACK_SNIPPET_LENGTH = 220;

    private final Path indexPath;
    private final Analyzer analyzer;
    private final NewsQueryBuilder queryBuilder;

    public WikiNewsSearchService(Path indexPath, Analyzer analyzer, NewsQueryBuilder queryBuilder) {
        this.indexPath = indexPath;
        this.analyzer = analyzer;
        this.queryBuilder = queryBuilder;
    }

    // Esegue una ricerca completa con paginazione, ranking scelto, faccette e snippet.
    public SearchResponse search(
            String queryText,
            String category,
            Integer minYear,
            Integer maxYear,
            RankingStrategy rankingStrategy,
            int page,
            int pageSize
    ) throws IOException, ParseException {

        long startTimeNanos = System.nanoTime();

        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        RankingStrategy activeRanking = (rankingStrategy != null)
                ? rankingStrategy
                : new Bm25RankingStrategy();

        Query query = queryBuilder.buildQuery(queryText, category, minYear, maxYear);

        try (Directory dir = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(dir)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(activeRanking.createSimilarity());

            int numHitsToCollect = safePage * safePageSize;

            TopDocs topDocs;
            List<CategoryFacet> categoryFacets;

            if (reader.numDocs() > 0) {
                TopScoreDocCollectorManager topDocsManager =
                        new TopScoreDocCollectorManager(numHitsToCollect, numHitsToCollect);
                topDocs = searcher.search(query, topDocsManager);

                // Raccoglie i documenti corrispondenti alla query per calcolare i conteggi delle faccette.
                FacetsCollector facetsCollector = new FacetsCollector();
                searcher.search(query, facetsCollector);
                categoryFacets = extractCategoryFacets(reader, facetsCollector);
            } else {
                topDocs = searcher.search(query, numHitsToCollect);
                categoryFacets = List.of();
            }

            // Calcola la finestra di documenti per la pagina richiesta.
            ScoreDoc[] allScoreDocs = topDocs.scoreDocs;
            int startIndex = (safePage - 1) * safePageSize;
            int endIndex = Math.min(startIndex + safePageSize, allScoreDocs.length);

            List<SearchHitItem> hitItems = new ArrayList<>();
            if (startIndex < endIndex) {
                ScoreDoc[] pageScoreDocs = Arrays.copyOfRange(allScoreDocs, startIndex, endIndex);
                TopDocs pageTopDocs = new TopDocs(topDocs.totalHits, pageScoreDocs);

                // Genera snippet evidenziati sul campo content (se c'è una query testuale).
                String[] snippets = generateSnippets(searcher, query, pageTopDocs, queryText);
                StoredFields storedFields = searcher.storedFields();

                for (int i = 0; i < pageScoreDocs.length; i++) {
                    ScoreDoc scoreDoc = pageScoreDocs[i];
                    Document doc = storedFields.document(scoreDoc.doc);

                    long id = Long.parseLong(doc.get(WikiNewsAnalyzerFactory.FIELD_IDENTIFIER));
                    String title = doc.get(WikiNewsAnalyzerFactory.FIELD_TITLE);
                    String url = doc.get(WikiNewsAnalyzerFactory.FIELD_URL);
                    String content = doc.get(WikiNewsAnalyzerFactory.FIELD_CONTENT);

                    String[] rawCats = doc.getValues(WikiNewsAnalyzerFactory.FIELD_CATEGORIES);
                    List<String> categories = (rawCats != null) ? Arrays.asList(rawCats) : List.of();

                    Integer year = null;
                    if (doc.getField(WikiNewsAnalyzerFactory.FIELD_YEAR) != null) {
                        year = doc.getField(WikiNewsAnalyzerFactory.FIELD_YEAR).numericValue().intValue();
                    }

                    String snippet = (snippets != null && i < snippets.length && snippets[i] != null && !snippets[i].isBlank())
                            ? snippets[i]
                            : buildFallbackSnippet(content);

                    hitItems.add(new SearchHitItem(
                            id,
                            title,
                            url,
                            snippet,
                            categories,
                            year,
                            scoreDoc.score
                    ));
                }
            }

            long elapsedMillis = (System.nanoTime() - startTimeNanos) / 1_000_000L;

            return new SearchResponse(
                    hitItems,
                    topDocs.totalHits.value,
                    categoryFacets,
                    activeRanking.name(),
                    elapsedMillis,
                    safePage,
                    safePageSize
            );
        }
    }

    // Recupera un singolo articolo completo dall'indice dato il suo identifier (per la vista di dettaglio in Fase 4).
    public Optional<NewsArticle> findByIdentifier(long identifier) throws IOException {
        try (Directory dir = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(dir)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            Query idQuery = new TermQuery(
                    new Term(WikiNewsAnalyzerFactory.FIELD_IDENTIFIER, Long.toString(identifier))
            );
            TopDocs topDocs = searcher.search(idQuery, 1);
            if (topDocs.scoreDocs.length == 0) {
                return Optional.empty();
            }

            Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
            String title = doc.get(WikiNewsAnalyzerFactory.FIELD_TITLE);
            String url = doc.get(WikiNewsAnalyzerFactory.FIELD_URL);
            String content = doc.get(WikiNewsAnalyzerFactory.FIELD_CONTENT);

            String[] rawCats = doc.getValues(WikiNewsAnalyzerFactory.FIELD_CATEGORIES);
            List<String> categories = (rawCats != null) ? Arrays.asList(rawCats) : List.of();

            Instant dateCreated = readInstantField(doc, WikiNewsAnalyzerFactory.FIELD_DATE_CREATED);
            Instant dateModified = readInstantField(doc, WikiNewsAnalyzerFactory.FIELD_DATE_MODIFIED);

            return Optional.of(new NewsArticle(
                    identifier,
                    title,
                    url,
                    dateCreated,
                    dateModified,
                    categories,
                    content
            ));
        }
    }

    // Calcola i conteggi delle categorie tramite SortedSetDocValuesFacetCounts.
    private List<CategoryFacet> extractCategoryFacets(DirectoryReader reader, FacetsCollector facetsCollector)
            throws IOException {
        try {
            SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(
                    reader,
                    WikiNewsAnalyzerFactory.FIELD_CATEGORIES
            );
            Facets facets = new SortedSetDocValuesFacetCounts(state, facetsCollector);
            FacetResult facetResult = facets.getTopChildren(MAX_FACET_ITEMS, WikiNewsAnalyzerFactory.FIELD_CATEGORIES);

            if (facetResult == null || facetResult.labelValues == null) {
                return List.of();
            }

            List<CategoryFacet> results = new ArrayList<>();
            for (LabelAndValue lv : facetResult.labelValues) {
                results.add(new CategoryFacet(lv.label, lv.value.intValue()));
            }
            return results;
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    // Estrae gli snippet evidenziati con UnifiedHighlighter quando c'è una query testuale.
    private String[] generateSnippets(IndexSearcher searcher, Query query, TopDocs topDocs, String queryText)
            throws IOException {
        if (queryText == null || queryText.isBlank() || topDocs.scoreDocs.length == 0) {
            return new String[0];
        }
        UnifiedHighlighter highlighter = new UnifiedHighlighter(searcher, analyzer);
        return highlighter.highlight(WikiNewsAnalyzerFactory.FIELD_CONTENT, query, topDocs, 2);
    }

    // Se non ci sono termini evidenziati (es. ricerca per sola categoria/anno), mostra i primi caratteri del testo.
    private String buildFallbackSnippet(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        if (content.length() <= FALLBACK_SNIPPET_LENGTH) {
            return content;
        }
        return content.substring(0, FALLBACK_SNIPPET_LENGTH) + "...";
    }

    private Instant readInstantField(Document doc, String fieldName) {
        if (doc.getField(fieldName) == null) {
            return null;
        }
        long epochSeconds = doc.getField(fieldName).numericValue().longValue();
        return Instant.ofEpochSecond(epochSeconds);
    }
}