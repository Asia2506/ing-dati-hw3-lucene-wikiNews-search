package it.uniroma3.ingdati.hw4.ingestion;

import it.uniroma3.ingdati.hw4.ingestion.extractor.HtmlExtractorStrategy;
import it.uniroma3.ingdati.hw4.ingestion.filter.ParsedArticleFilterStrategy;
import it.uniroma3.ingdati.hw4.ingestion.filter.RawArticleFilterStrategy;
import it.uniroma3.ingdati.hw4.model.NewsArticle;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.BaseJsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Coordina la lettura riga per riga del file NDJSON e applica filtri ed estrattore HTML.
// Non contiene regole fisse di filtro o pulizia: delega alle Strategy ricevute nel costruttore.
public class NdjsonArticleReader {

    // Mapper JSON di Jackson 3 (allineato al resto del progetto su Spring Boot 4.2.0).
    private final JsonMapper jsonMapper;

    // Strategia di filtro pre-parsing (sul record JSON grezzo).
    private final RawArticleFilterStrategy preFilter;

    // Strategia per estrarre e ripulire il testo dall'HTML grezzo (es. MainSectionJsoupExtractor).
    private final HtmlExtractorStrategy htmlExtractor;

    // Strategia di filtro post-parsing (sul NewsArticle già costruito).
    private final ParsedArticleFilterStrategy postFilter;

    // Costruttore che riceve le strategie dall'esterno (Dependency Injection manuale / Low Coupling).
    public NdjsonArticleReader(
            RawArticleFilterStrategy preFilter,
            HtmlExtractorStrategy htmlExtractor,
            ParsedArticleFilterStrategy postFilter
    ) {
        this.jsonMapper = new JsonMapper();
        this.preFilter = preFilter;
        this.htmlExtractor = htmlExtractor;
        this.postFilter = postFilter;
    }

    // Legge il file NDJSON in streaming riga per riga e restituisce la lista degli articoli validi.
    public List<NewsArticle> readArticles(Path ndjsonPath) throws IOException {
        List<NewsArticle> articles = new ArrayList<>();

        // try-with-resources chiude automaticamente il BufferedReader anche in caso di eccezione.
        // Files.newBufferedReader con UTF-8 legge una riga alla volta senza caricare tutto il file in RAM.
        try (BufferedReader reader = Files.newBufferedReader(ndjsonPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Salta eventuali righe vuote o composte solo da spazi.
                if (line.isBlank()) {
                    continue;
                }

                // Converte la singola riga JSON in un albero di nodi.
                JsonNode rootNode = jsonMapper.readTree(line);

                // Se la riga non è un oggetto JSON valido o non supera il filtro preventivo, la scarta subito.
                if (!(rootNode instanceof BaseJsonNode baseNode)) {
                    continue;
                }
                if (preFilter != null && !preFilter.accept(baseNode)) {
                    continue;
                }

                // Mappa i campi del JSON e ripulisce il corpo HTML tramite la strategia scelta.
                NewsArticle article = mapToArticle(rootNode);

                // Applica il filtro post-parsing (es. soglia minima di caratteri sul testo ripulito).
                if (postFilter != null && !postFilter.accept(article)) {
                    continue;
                }

                // L'articolo ha superato tutte le fasi di ingestione: viene aggiunto al risultato.
                articles.add(article);
            }
        }

        return articles;
    }

    // Estrae i campi rilevanti dal JsonNode di WikiNews e costruisce un NewsArticle immutabile.
    private NewsArticle mapToArticle(JsonNode node) {
        long identifier = node.path("identifier").asLong(0L);
        String title = node.path("name").asText("").trim();
        String url = node.path("url").asText("").trim();

        // Converte i timestamp ISO-8601 (es. "2020-11-01T11:32:27Z") in Instant;
        // se assenti o non validi, restituisce null senza far fallire l'intera lettura.
        Instant dateCreated = parseInstantOrNull(node.path("date_created").asText(""));
        Instant dateModified = parseInstantOrNull(node.path("date_modified").asText(""));

        // Nel dump WikiNews il campo si chiama "categoria" (con fallback su "categories" se presente).
        JsonNode categoriesNode = node.path("categoria");
        if (categoriesNode.isMissingNode() || categoriesNode.isNull()) {
            categoriesNode = node.path("categories");
        }
        List<String> categories = extractCategories(categoriesNode);

        // Estrae l'HTML grezzo e lo ripulisce tramite la strategia configurata.
        String rawHtml = node.path("article_body").path("html").asText("");
        String cleanContent = (htmlExtractor != null)
                ? htmlExtractor.extractCleanText(rawHtml)
                : rawHtml;

        return new NewsArticle(
                identifier,
                title,
                url,
                dateCreated,
                dateModified,
                categories,
                cleanContent
        );
    }

    // Prova a parsare una stringa ISO-8601 in Instant; se vuota o malformata torna null.
    private Instant parseInstantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    // Legge l'array JSON delle categorie, estrae "name" e restituisce categorie tematiche/utili ripulite.
    private List<String> extractCategories(JsonNode categoriesNode) {
        List<String> result = new ArrayList<>();
        if (!categoriesNode.isArray()) {
            return result;
        }

        for (JsonNode catNode : categoriesNode) {
            String rawName = "";
            if (catNode.isObject()) {
                rawName = catNode.path("name").asText("");
            } else if (catNode.isString()) {
                rawName = catNode.asText("");
            }

            String cleaned = cleanCategoryName(rawName);
            if (cleaned != null && !result.contains(cleaned)) {
                result.add(cleaned);
            }
        }
        return result;
    }

    // Ripulisce il nome della categoria e scarta etichette di servizio o date.
    private String cleanCategoryName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }

        String value = rawName.trim();
        if (value.regionMatches(true, 0, "Categoria:", 0, "Categoria:".length())) {
            value = value.substring("Categoria:".length()).trim();
        }
        value = value.replace('_', ' ').trim();

        if (value.isEmpty()) {
            return null;
        }

        String lower = value.toLowerCase(Locale.ROOT);

        // Esclude categorie tecniche / di servizio / archiviazione / prima pagina
        if (lower.equals("pubblicati")
                || lower.equals("articoli archiviati")
                || lower.equals("articoli da controllare per l'archiviazione")
                || lower.equals("notizie per wikivoyage scadute")
                || lower.equals("notizie da prima pagina")
                || lower.equals("articoli con box interwiki verso wikipedia")
                || lower.equals("articoli da migliorare")) {
            return null;
        }

        // Esclude categorie che rappresentano date (es. "13 gennaio 2006", "9 ottobre 2016")
        if (looksLikeItalianDateCategory(lower)) {
            return null;
        }

        return value;
    }

    private boolean looksLikeItalianDateCategory(String lowerValue) {
        String[] tokens = lowerValue.split("\\s+");
        if (tokens.length < 2) {
            return false;
        }
        boolean startsWithDayOrMonth = tokens[0].matches("\\d{1,2}") || isItalianMonth(tokens[0]);
        boolean hasYear = lowerValue.matches(".*\\b(19|20)\\d{2}\\b.*");
        return startsWithDayOrMonth && hasYear;
    }

    private boolean isItalianMonth(String token) {
        return token.equals("gennaio")
                || token.equals("febbraio")
                || token.equals("marzo")
                || token.equals("aprile")
                || token.equals("maggio")
                || token.equals("giugno")
                || token.equals("luglio")
                || token.equals("agosto")
                || token.equals("settembre")
                || token.equals("ottobre")
                || token.equals("novembre")
                || token.equals("dicembre");
    }
}