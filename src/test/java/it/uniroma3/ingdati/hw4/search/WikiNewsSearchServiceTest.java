package it.uniroma3.ingdati.hw4.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.uniroma3.ingdati.hw4.indexing.WikiNewsAnalyzerFactory;
import it.uniroma3.ingdati.hw4.indexing.WikiNewsIndexer;
import it.uniroma3.ingdati.hw4.model.NewsArticle;

// Test di integrazione mirato per la Fase 3 (NewsQueryBuilder, RankingStrategy, UnifiedHighlighter, FacetsCollector).
class WikiNewsSearchServiceTest {

    @TempDir
    Path tempIndexDir;

    private Analyzer analyzer;
    private WikiNewsSearchService searchService;

    @BeforeEach
    void setUp() throws IOException {
        WikiNewsAnalyzerFactory factory = new WikiNewsAnalyzerFactory();
        this.analyzer = factory.createAnalyzer();

        NewsArticle articleTitleMatch = new NewsArticle(
                1L,
                "Energia solare in Italia",
                "https://it.wikinews.org/wiki/Energia_solare_in_Italia",
                Instant.parse("2023-04-10T10:00:00Z"),
                null,
                List.of("Energia", "Ambiente"),
                "Nuovi impianti fotovoltaici sono stati inaugurati nel sud del paese."
        );

        NewsArticle articleBodyMatch = new NewsArticle(
                2L,
                "Nuove norme sulle infrastrutture",
                "https://it.wikinews.org/wiki/Nuove_norme_sulle_infrastrutture",
                Instant.parse("2022-07-20T14:30:00Z"),
                null,
                List.of("Economia", "Energia"),
                "Il decreto cita anche progetti legati all'energia solare e alle reti elettriche."
        );

        NewsArticle articleOtherTopic = new NewsArticle(
                3L,
                "Campionato di pallavolo femminile",
                "https://it.wikinews.org/wiki/Pallavolo",
                Instant.parse("2024-02-01T18:00:00Z"),
                null,
                List.of("Sport"),
                "La squadra ha vinto la partita decisiva con un ottimo servizio."
        );

        WikiNewsIndexer indexer = new WikiNewsIndexer(tempIndexDir, this.analyzer);
        indexer.indexArticles(List.of(articleTitleMatch, articleBodyMatch, articleOtherTopic));

        NewsQueryBuilder queryBuilder = new NewsQueryBuilder(this.analyzer);
        this.searchService = new WikiNewsSearchService(tempIndexDir, this.analyzer, queryBuilder);
    }

    @Test
    void search_shouldBoostTitleMatchesAndHighlightSnippetsWithBm25() throws IOException, ParseException {
        SearchResponse response = searchService.search(
                "energia solare",
                null,
                null,
                null,
                new Bm25RankingStrategy(),
                1,
                10
        );

        assertEquals(2L, response.totalHits());
        assertEquals("BM25", response.rankingModel());
        assertEquals(2, response.hits().size());

        // Grazie al titleBoost, l'articolo con "Energia solare" nel titolo deve arrivare primo.
        SearchHitItem firstHit = response.hits().get(0);
        assertEquals(1L, firstHit.identifier());
        assertTrue(firstHit.score() > response.hits().get(1).score());

        // Il secondo risultato ha "energia solare" nel content: UnifiedHighlighter deve evidenziarlo.
        SearchHitItem secondHit = response.hits().get(1);
        assertEquals(2L, secondHit.identifier());
        assertTrue(secondHit.snippet().contains("<b>"));
    }

    @Test
    void search_shouldSupportTfIdfRankingStrategy() throws IOException, ParseException {
        SearchResponse response = searchService.search(
                "energia",
                null,
                null,
                null,
                new TfIdfRankingStrategy(),
                1,
                10
        );

        assertEquals("TF-IDF", response.rankingModel());
        assertEquals(2L, response.totalHits());
    }

    @Test
    void search_shouldComputeCategoryFacetsAndFilterByCategoryAndYear() throws IOException, ParseException {
        // Ricerca ampia su "Energia" (filtro per categoria): verifichiamo faccette e conteggi.
        SearchResponse allEnergy = searchService.search(
                null,
                "Energia",
                null,
                null,
                new Bm25RankingStrategy(),
                1,
                10
        );

        assertEquals(2L, allEnergy.totalHits());
        assertFalse(allEnergy.categoryFacets().isEmpty());

        // Se restringiamo l'anno minimo al 2023, resta solo l'articolo del 2023.
        SearchResponse filteredByYear = searchService.search(
                null,
                "Energia",
                2023,
                2024,
                new Bm25RankingStrategy(),
                1,
                10
        );

        assertEquals(1L, filteredByYear.totalHits());
        assertEquals(1L, filteredByYear.hits().get(0).identifier());
    }

    @Test
    void findByIdentifier_shouldReturnArticleWhenPresent() throws IOException {
        Optional<NewsArticle> found = searchService.findByIdentifier(2L);
        assertTrue(found.isPresent());
        assertEquals("Nuove norme sulle infrastrutture", found.get().title());

        Optional<NewsArticle> missing = searchService.findByIdentifier(999999L);
        assertTrue(missing.isEmpty());
    }
}


