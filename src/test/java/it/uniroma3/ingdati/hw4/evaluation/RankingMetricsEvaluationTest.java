package it.uniroma3.ingdati.hw4.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.uniroma3.ingdati.hw4.indexing.WikiNewsAnalyzerFactory;
import it.uniroma3.ingdati.hw4.indexing.WikiNewsIndexer;
import it.uniroma3.ingdati.hw4.model.NewsArticle;
import it.uniroma3.ingdati.hw4.search.Bm25RankingStrategy;
import it.uniroma3.ingdati.hw4.search.NewsQueryBuilder;
import it.uniroma3.ingdati.hw4.search.RankingStrategy;
import it.uniroma3.ingdati.hw4.search.SearchHitItem;
import it.uniroma3.ingdati.hw4.search.SearchResponse;
import it.uniroma3.ingdati.hw4.search.TfIdfRankingStrategy;
import it.uniroma3.ingdati.hw4.search.WikiNewsSearchService;

// Valuta e confronta BM25 e TF-IDF su un benchmark controllato calcolando P@5, MAP@20 e nDCG@10.
class RankingMetricsEvaluationTest {

    @TempDir
    Path tempDir;

    private WikiNewsSearchService searchService;

    // Rappresenta una query di benchmark con i suoi giudizi di rilevanza graduati (docId -> grado 1..3).
    private static final class BenchmarkQuery {
        private final String queryText;
        private final String categoryFilter;
        private final Map<Long, Integer> relevanceGrades;

        private BenchmarkQuery(String queryText, String categoryFilter, Map<Long, Integer> relevanceGrades) {
            this.queryText = queryText;
            this.categoryFilter = categoryFilter;
            this.relevanceGrades = relevanceGrades;
        }
    }

    // Raccoglie le metriche aggregate su tutte le query per un singolo modello di ranking.
    private static final class ModelEvaluationSummary {
        private final String modelName;
        private final double meanP5;
        private final double map20;
        private final double meanNdcg10;

        private ModelEvaluationSummary(String modelName, double meanP5, double map20, double meanNdcg10) {
            this.modelName = modelName;
            this.meanP5 = meanP5;
            this.map20 = map20;
            this.meanNdcg10 = meanNdcg10;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Path indexPath = tempDir.resolve("eval-index");
        Analyzer analyzer = new WikiNewsAnalyzerFactory().createAnalyzer();
        NewsQueryBuilder queryBuilder = new NewsQueryBuilder(analyzer);

        WikiNewsIndexer indexer = new WikiNewsIndexer(indexPath, analyzer);
        List<NewsArticle> corpus = buildEvaluationCorpus();
        int indexedCount = indexer.indexArticles(corpus);
        assertEquals(corpus.size(), indexedCount);

        this.searchService = new WikiNewsSearchService(indexPath, analyzer, queryBuilder);
    }

    @Test
    void compareBm25AndTfIdf_onPrecisionMapAndNdcg() throws Exception {
        List<BenchmarkQuery> queries = buildBenchmarkQueries();

        RankingStrategy bm25 = new Bm25RankingStrategy();
        RankingStrategy tfidf = new TfIdfRankingStrategy();

        ModelEvaluationSummary bm25Summary = evaluateModel(bm25, queries);
        ModelEvaluationSummary tfidfSummary = evaluateModel(tfidf, queries);

        printComparisonTable(bm25Summary, tfidfSummary);

        // Verifica che tutte le metriche siano nell'intervallo valido [0.0, 1.0] e positive sul corpus di test
        assertValidMetricRange(bm25Summary.meanP5);
        assertValidMetricRange(bm25Summary.map20);
        assertValidMetricRange(bm25Summary.meanNdcg10);

        assertValidMetricRange(tfidfSummary.meanP5);
        assertValidMetricRange(tfidfSummary.map20);
        assertValidMetricRange(tfidfSummary.meanNdcg10);

        assertTrue(bm25Summary.meanP5 > 0.5, "BM25 dovrebbe ottenere P@5 > 0.5 sul benchmark");
        assertTrue(bm25Summary.map20 > 0.6, "BM25 dovrebbe ottenere MAP@20 > 0.6 sul benchmark");
        assertTrue(bm25Summary.meanNdcg10 > 0.7, "BM25 dovrebbe ottenere nDCG@10 > 0.7 sul benchmark");
    }

    private ModelEvaluationSummary evaluateModel(RankingStrategy strategy, List<BenchmarkQuery> queries) throws Exception {
        double sumP5 = 0.0;
        double sumNdcg10 = 0.0;
        List<Double> averagePrecisionsAt20 = new ArrayList<>();

        for (int i = 0; i < queries.size(); i++) {
            BenchmarkQuery bq = queries.get(i);

            // Recupera i primi 20 risultati per calcolare contemporaneamente P@5, MAP@20 e nDCG@10
            SearchResponse response = searchService.search(
                    bq.queryText,
                    bq.categoryFilter,
                    null,
                    null,
                    strategy,
                    1,
                    20
            );

            List<Long> rankedDocIds = extractRankedIds(response.hits());
            Set<Long> binaryRelevantIds = extractPositiveRelevanceIds(bq.relevanceGrades);

            double p5 = IrMetricsCalculator.precisionAtK(rankedDocIds, binaryRelevantIds, 5);
            double ap20 = IrMetricsCalculator.averagePrecisionAtK(rankedDocIds, binaryRelevantIds, 20);
            double ndcg10 = IrMetricsCalculator.ndcgAtK(rankedDocIds, bq.relevanceGrades, 10);

            sumP5 += p5;
            sumNdcg10 += ndcg10;
            averagePrecisionsAt20.add(Double.valueOf(ap20));
        }

        double meanP5 = sumP5 / (double) queries.size();
        double map20 = IrMetricsCalculator.meanAveragePrecisionAtK(averagePrecisionsAt20);
        double meanNdcg10 = sumNdcg10 / (double) queries.size();

        return new ModelEvaluationSummary(strategy.name(), meanP5, map20, meanNdcg10);
    }

    private List<Long> extractRankedIds(List<SearchHitItem> hits) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            ids.add(Long.valueOf(hits.get(i).identifier()));
        }
        return ids;
    }

    private Set<Long> extractPositiveRelevanceIds(Map<Long, Integer> relevanceGrades) {
        Set<Long> relevant = new HashSet<>();
        for (Map.Entry<Long, Integer> entry : relevanceGrades.entrySet()) {
            if (entry.getValue() != null && entry.getValue().intValue() > 0) {
                relevant.add(entry.getKey());
            }
        }
        return relevant;
    }

    private void assertValidMetricRange(double value) {
        assertTrue(value >= 0.0 && value <= 1.0, "Metrica fuori intervallo [0, 1]: " + value);
    }

    private void printComparisonTable(ModelEvaluationSummary bm25, ModelEvaluationSummary tfidf) {
        System.out.println("============================================================");
        System.out.println(" CONFRONTO MODELLI DI RANKING (Benchmark WikiNews HW4)");
        System.out.println("------------------------------------------------------------");
        System.out.printf(Locale.ROOT, "%-12s | %-10s | %-10s | %-10s%n", "Modello", "P@5", "MAP@20", "nDCG@10");
        System.out.println("------------------------------------------------------------");
        System.out.printf(Locale.ROOT, "%-12s | %-10.4f | %-10.4f | %-10.4f%n",
                bm25.modelName, bm25.meanP5, bm25.map20, bm25.meanNdcg10);
        System.out.printf(Locale.ROOT, "%-12s | %-10.4f | %-10.4f | %-10.4f%n",
                tfidf.modelName, tfidf.meanP5, tfidf.map20, tfidf.meanNdcg10);
        System.out.println("============================================================");
    }

    private List<BenchmarkQuery> buildBenchmarkQueries() {
        List<BenchmarkQuery> list = new ArrayList<>();

        // Query 1: Terremoto e protezione civile in Italia
        Map<Long, Integer> q1Grades = new HashMap<>();
        q1Grades.put(101L, 3);
        q1Grades.put(102L, 3);
        q1Grades.put(103L, 2);
        q1Grades.put(104L, 1);
        list.add(new BenchmarkQuery("terremoto protezione civile", null, q1Grades));

        // Query 2: Energia solare e transizione rinnovabile
        Map<Long, Integer> q2Grades = new HashMap<>();
        q2Grades.put(201L, 3);
        q2Grades.put(202L, 2);
        q2Grades.put(203L, 2);
        q2Grades.put(204L, 1);
        list.add(new BenchmarkQuery("energia solare fotovoltaico", null, q2Grades));

        // Query 3: Esplorazione spaziale e missioni lunari
        Map<Long, Integer> q3Grades = new HashMap<>();
        q3Grades.put(301L, 3);
        q3Grades.put(302L, 3);
        q3Grades.put(303L, 2);
        q3Grades.put(304L, 1);
        list.add(new BenchmarkQuery("missione spaziale luna", null, q3Grades));

        return list;
    }

    private List<NewsArticle> buildEvaluationCorpus() {
        List<NewsArticle> docs = new ArrayList<>();

        docs.add(article(101L,
                "Forte terremoto nel Centro Italia, interviene la protezione civile",
                2016,
                List.of("Italia", "Terremoti", "Disastri e incidenti"),
                "Una forte scossa di terremoto ha colpito il Centro Italia durante la notte. "
                        + "La protezione civile ha attivato immediatamente i soccorsi e allestito i campi per gli sfollati."));

        docs.add(article(102L,
                "Terremoto in Emilia: la protezione civile coordina le verifiche sugli edifici",
                2012,
                List.of("Italia", "Terremoti"),
                "Dopo il terremoto registrato in Emilia, i tecnici della protezione civile stanno verificando "
                        + "la stabilita delle scuole e degli edifici pubblici."));

        docs.add(article(103L,
                "Nuovo piano nazionale di prevenzione contro il rischio terremoto",
                2021,
                List.of("Italia", "Politica"),
                "Il governo ha presentato un piano pluriennale per la messa in sicurezza sismica "
                        + "e il rafforzamento della protezione civile nei comuni a piu alto rischio terremoto."));

        docs.add(article(104L,
                "Ricostruzione post sisma: bilanci a dieci anni dal terremoto",
                2019,
                List.of("Italia", "Cultura e società"),
                "A distanza di dieci anni dal terremoto si fa il punto sullo stato dei cantieri "
                        + "e sui fondi stanziati per la ricostruzione dei centri storici."));

        docs.add(article(105L,
                "Cronaca locale e viabilita cittadina nel fine settimana",
                2020,
                List.of("Italia"),
                "Durante la riunione di quartiere si e parlato di traffico, parcheggi, manutenzione stradale, "
                        + "raccolta rifiuti e illuminazione pubblica. Un cittadino ha citato di sfuggita il terremoto "
                        + "e la protezione civile senza alcun legame con il tema principale della cronaca locale."));

        docs.add(article(201L,
                "Nuovi impianti fotovoltaici spingono la produzione di energia solare in Italia",
                2023,
                List.of("Ambiente", "Scienza e tecnologia"),
                "La crescita del fotovoltaico porta a un nuovo massimo nella generazione di energia solare, "
                        + "riducendo la dipendenza dai combustibili fossili."));

        docs.add(article(202L,
                "Approvati gli incentivi per pannelli solari e comunita energetiche",
                2022,
                List.of("Economia", "Ambiente"),
                "Nuove agevolazioni per installare impianti fotovoltaici sui tetti residenziali "
                        + "e promuovere l'autoconsumo di energia solare."));

        docs.add(article(203L,
                "Europa: record estivo per l'energia solare nella rete elettrica",
                2024,
                List.of("Europa", "Ambiente"),
                "Secondo il rapporto energetico europeo, il fotovoltaico e l'energia solare hanno coperto "
                        + "una quota storica della domanda elettrica continentale."));

        docs.add(article(204L,
                "Rapporto annuale sulle fonti rinnovabili ed eolico offshore",
                2021,
                List.of("Ambiente"),
                "Il documento analizza la crescita dell'eolico, dell'idroelettrico e in parte dell'energia solare "
                        + "nel bilancio energetico nazionale."));

        docs.add(article(301L,
                "Lanciata la nuova missione spaziale verso l'orbita della Luna",
                2022,
                List.of("Scienza e tecnologia", "Spazio"),
                "Il razzo vettore e decollato con successo inaugurando la nuova missione spaziale "
                        + "destinata a preparare il ritorno umano sulla Luna."));

        docs.add(article(302L,
                "Test completati per il modulo abitativo della missione sulla Luna",
                2024,
                List.of("Scienza e tecnologia", "Spazio"),
                "Gli ingegneri aerospaziali hanno concluso le prove dei sistemi di supporto vitale "
                        + "per la prossima missione spaziale con equipaggio intorno alla Luna."));

        docs.add(article(303L,
                "Il telescopio spaziale invia nuove immagini ad alta risoluzione",
                2023,
                List.of("Scienza e tecnologia", "Spazio"),
                "Nuove osservazioni dello spazio profondo aiutano gli astronomi a studiare la formazione "
                        + "di galassie e sistemi planetari lontani."));

        docs.add(article(304L,
                "Approvato il budget europeo per la ricerca spaziale e i satelliti",
                2020,
                List.of("Europa", "Scienza e tecnologia"),
                "I ministri hanno definito i finanziamenti per i programmi satellitari di osservazione "
                        + "della Terra e le future attivita di esplorazione spaziale."));

        return docs;
    }

    private NewsArticle article(long id, String title, int year, List<String> categories, String content) {
        Instant created = Instant.parse(year + "-06-15T10:00:00Z");
        return new NewsArticle(
                id,
                title,
                "https://it.wikinews.org/wiki/Articolo_" + id,
                created,
                null,
                categories,
                content
        );
    }
}
