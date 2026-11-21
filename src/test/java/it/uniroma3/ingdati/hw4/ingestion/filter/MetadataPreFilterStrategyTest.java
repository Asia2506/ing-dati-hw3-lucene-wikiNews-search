package it.uniroma3.ingdati.hw4.ingestion.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// Test unitari per il filtro preventivo sui metadati JSON (MetadataPreFilterStrategy).
class MetadataPreFilterStrategyTest {

    private JsonMapper jsonMapper;
    private MetadataPreFilterStrategy filter;

    @BeforeEach
    void setUp() {
        // Prepara mapper Jackson 3 e filtro puliti prima di ogni test.
        jsonMapper = new JsonMapper();
        filter = new MetadataPreFilterStrategy();
    }

    @Test
    void accept_shouldReturnTrueForValidArticle() {
        // Costruisce un oggetto JSON valido con titolo, corpo HTML e categoria normale.
        ObjectNode article = jsonMapper.createObjectNode();
        article.put("name", "Notizia di prova");
        ArrayNode categories = article.putArray("categories");
        categories.add("Politica");
        ObjectNode body = article.putObject("article_body");
        body.put("html", "<section data-mw-section-id=\"0\"><p>Testo notizia.</p></section>");

        assertTrue(filter.accept(article));
    }

    @Test
    void accept_shouldRejectWhenTitleIsMissingOrBlank() {
        // Titolo vuoto: deve essere scartato subito senza passare a Jsoup.
        ObjectNode article = jsonMapper.createObjectNode();
        article.put("name", "   ");
        article.putObject("article_body").put("html", "<p>Contenuto presente</p>");

        assertFalse(filter.accept(article));
    }

    @Test
    void accept_shouldRejectWhenHtmlBodyIsEmpty() {
        // Corpo HTML mancante o vuoto: deve essere scartato.
        ObjectNode article = jsonMapper.createObjectNode();
        article.put("name", "Titolo senza corpo");
        article.putObject("article_body").put("html", "");

        assertFalse(filter.accept(article));
    }

    @Test
    void accept_shouldRejectWhenCategoryIsExcluded() {
        // Categoria di servizio/cancellazione (anche con maiuscole/spazi): deve essere scartato.
        ObjectNode article = jsonMapper.createObjectNode();
        article.put("name", "Voce da rimuovere");
        ArrayNode categories = article.putArray("categories");
        categories.add("  Da Cancellare Subito  ");
        article.putObject("article_body").put("html", "<p>Contenuto qualsiasi</p>");

        assertFalse(filter.accept(article));
    }
}