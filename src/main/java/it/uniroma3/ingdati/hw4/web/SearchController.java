package it.uniroma3.ingdati.hw4.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.lucene.queryparser.classic.ParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.uniroma3.ingdati.hw4.indexing.WikiNewsIndexer;
import it.uniroma3.ingdati.hw4.ingestion.NdjsonArticleReader;
import it.uniroma3.ingdati.hw4.model.NewsArticle;
import it.uniroma3.ingdati.hw4.search.Bm25RankingStrategy;
import it.uniroma3.ingdati.hw4.search.RankingStrategy;
import it.uniroma3.ingdati.hw4.search.SearchResponse;
import it.uniroma3.ingdati.hw4.search.TfIdfRankingStrategy;
import it.uniroma3.ingdati.hw4.search.WikiNewsSearchService;

// Controller MVC per la Web UI del motore di ricerca su WikiNews.
@Controller
public class SearchController {

    private final WikiNewsSearchService searchService;
    private final NdjsonArticleReader articleReader;
    private final WikiNewsIndexer indexer;
    private final Path ndjsonPath;
    private final Path indexPath;
    private final int defaultPageSize;

    public SearchController(
            WikiNewsSearchService searchService,
            NdjsonArticleReader articleReader,
            WikiNewsIndexer indexer,
            @Value("${wikinews.ndjson-path:data/itwikinews_namespace_0_0.ndjson}") String ndjsonPathStr,
            @Value("${wikinews.index-path:index}") String indexPathStr,
            @Value("${wikinews.default-page-size:10}") int defaultPageSize
    ) {
        this.searchService = searchService;
        this.articleReader = articleReader;
        this.indexer = indexer;
        this.ndjsonPath = Path.of(ndjsonPathStr);
        this.indexPath = Path.of(indexPathStr);
        this.defaultPageSize = defaultPageSize;
    }

    @GetMapping("/")
    public String searchPage(
            @RequestParam(name = "q", required = false) String queryText,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "minYear", required = false) Integer minYear,
            @RequestParam(name = "maxYear", required = false) Integer maxYear,
            @RequestParam(name = "ranking", required = false, defaultValue = "BM25") String ranking,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            Model model
    ) {
        String cleanQuery = (queryText != null && !queryText.isBlank()) ? queryText.trim() : null;
        String cleanCategory = (category != null && !category.isBlank()) ? category.trim() : null;
        RankingStrategy strategy = resolveRankingStrategy(ranking);

        model.addAttribute("q", cleanQuery != null ? cleanQuery : "");
        model.addAttribute("category", cleanCategory != null ? cleanCategory : "");
        model.addAttribute("minYear", minYear);
        model.addAttribute("maxYear", maxYear);
        model.addAttribute("ranking", strategy.name());
        model.addAttribute("ndjsonExists", Files.exists(ndjsonPath));

        boolean indexExists = Files.exists(indexPath) && Files.isDirectory(indexPath);
        if (!indexExists) {
            model.addAttribute("indexMissing", true);
            model.addAttribute("searchResponse", null);
            return "search";
        }

        try {
            SearchResponse response = searchService.search(
                    cleanQuery,
                    cleanCategory,
                    minYear,
                    maxYear,
                    strategy,
                    page,
                    defaultPageSize
            );
            model.addAttribute("indexMissing", false);
            model.addAttribute("searchResponse", response);
        } catch (ParseException ex) {
            model.addAttribute("indexMissing", false);
            model.addAttribute("errorMessage", "Query non valida: " + ex.getMessage());
            model.addAttribute("searchResponse", null);
        } catch (IOException ex) {
            model.addAttribute("indexMissing", true);
            model.addAttribute("infoMessage", "Indice non ancora inizializzato o non leggibile.");
            model.addAttribute("searchResponse", null);
        }

        return "search";
    }

    @GetMapping("/article/{id}")
    public String articleDetail(@PathVariable("id") long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Optional<NewsArticle> articleOpt = searchService.findByIdentifier(id);
            if (articleOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Articolo con ID " + id + " non trovato.");
                return "redirect:/";
            }
            model.addAttribute("article", articleOpt.get());
            return "article";
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Errore durante il recupero dell'articolo: " + ex.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping("/reindex")
    public String reindex(RedirectAttributes redirectAttributes) {
        if (!Files.exists(ndjsonPath)) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "File NDJSON non trovato in: " + ndjsonPath.toAbsolutePath()
            );
            return "redirect:/";
        }

        try {
            List<NewsArticle> articles = articleReader.readArticles(ndjsonPath);
            int indexed = indexer.indexArticles(articles);
            redirectAttributes.addFlashAttribute(
                    "statusMessage",
                    "Indicizzazione completata: " + indexed + " articoli indicizzati."
            );
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Errore durante l'indicizzazione: " + ex.getMessage()
            );
        }

        return "redirect:/";
    }

    private RankingStrategy resolveRankingStrategy(String rankingParam) {
        if ("TF-IDF".equalsIgnoreCase(rankingParam) || "TFIDF".equalsIgnoreCase(rankingParam)) {
            return new TfIdfRankingStrategy();
        }
        return new Bm25RankingStrategy();
    }
}