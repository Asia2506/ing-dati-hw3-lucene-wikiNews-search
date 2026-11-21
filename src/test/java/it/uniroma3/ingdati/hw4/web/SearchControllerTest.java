package it.uniroma3.ingdati.hw4.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import it.uniroma3.ingdati.hw4.indexing.WikiNewsIndexer;
import it.uniroma3.ingdati.hw4.ingestion.NdjsonArticleReader;
import it.uniroma3.ingdati.hw4.model.NewsArticle;
import it.uniroma3.ingdati.hw4.search.Bm25RankingStrategy;
import it.uniroma3.ingdati.hw4.search.SearchResponse;
import it.uniroma3.ingdati.hw4.search.WikiNewsSearchService;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @TempDir
    Path tempDir;

    @Mock
    private WikiNewsSearchService searchService;

    @Mock
    private NdjsonArticleReader articleReader;

    @Mock
    private WikiNewsIndexer indexer;

    private MockMvc mockMvc;
    private Path ndjsonPath;
    private Path indexPath;

    @BeforeEach
    void setUp() {
        ndjsonPath = tempDir.resolve("wiki.ndjson");
        indexPath = tempDir.resolve("index");

        SearchController controller = new SearchController(
                searchService,
                articleReader,
                indexer,
                ndjsonPath.toString(),
                indexPath.toString(),
                10
        );

        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void searchPage_whenIndexMissing_shouldSetIndexMissingFlag() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("indexMissing", true))
                .andExpect(model().attribute("searchResponse", (Object) null));
    }

    @Test
    void searchPage_whenIndexExists_shouldExecuteSearchAndPopulateModel() throws Exception {
        Files.createDirectories(indexPath);

        SearchResponse fakeResponse = new SearchResponse(
                List.of(),
                0L,
                List.of(),
                "BM25",
                12L,
                1,
                10
        );

        when(searchService.search(
                eq("terremoto"),
                eq("Terremoti"),
                eq(2000),
                eq(2010),
                any(Bm25RankingStrategy.class),
                eq(1),
                eq(10)
        )).thenReturn(fakeResponse);

        mockMvc.perform(get("/")
                        .param("q", "terremoto")
                        .param("category", "Terremoti")
                        .param("minYear", "2000")
                        .param("maxYear", "2010")
                        .param("ranking", "BM25"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("indexMissing", false))
                .andExpect(model().attribute("q", "terremoto"))
                .andExpect(model().attribute("category", "Terremoti"))
                .andExpect(model().attribute("ranking", "BM25"))
                .andExpect(model().attribute("searchResponse", fakeResponse));
    }

    @Test
    void articleDetail_whenFound_shouldReturnArticleView() throws Exception {
        NewsArticle article = new NewsArticle(
                500L,
                "Titolo prova",
                "https://it.wikinews.org/wiki/Prova",
                Instant.parse("2022-05-01T10:00:00Z"),
                null,
                List.of("Cronaca"),
                "Contenuto di prova dell'articolo."
        );

        when(searchService.findByIdentifier(500L)).thenReturn(Optional.of(article));

        mockMvc.perform(get("/article/500"))
                .andExpect(status().isOk())
                .andExpect(view().name("article"))
                .andExpect(model().attribute("article", article));
    }

    @Test
    void articleDetail_whenMissing_shouldRedirectWithFlashError() throws Exception {
        when(searchService.findByIdentifier(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/article/999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("errorMessage", "Articolo con ID 999 non trovato."));
    }

    @Test
    void reindex_whenNdjsonExists_shouldIndexAndRedirectWithStatus() throws Exception {
        Files.writeString(ndjsonPath, "{}", StandardCharsets.UTF_8);

        NewsArticle sample = new NewsArticle(
                1L,
                "News 1",
                "",
                null,
                null,
                List.of(),
                "Testo articolo 1"
        );
        List<NewsArticle> parsed = List.of(sample);

        when(articleReader.readArticles(ndjsonPath)).thenReturn(parsed);
        when(indexer.indexArticles(parsed)).thenReturn(1);

        mockMvc.perform(post("/reindex"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("statusMessage", "Indicizzazione completata: 1 articoli indicizzati."));

        verify(indexer).indexArticles(parsed);
    }

    @Test
    void reindex_whenNdjsonMissing_shouldRedirectWithError() throws Exception {
        mockMvc.perform(post("/reindex"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}