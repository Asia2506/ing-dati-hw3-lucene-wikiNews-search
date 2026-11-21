package it.uniroma3.ingdati.hw4.ingestion.filter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.BaseJsonNode;

import java.util.Locale;
import java.util.Set;

// Implementazione concreta del filtro preventivo sui metadati del JSON.
// Evita di sprecare tempo su Jsoup e Lucene per pagine non utili o da cancellare.
public class MetadataPreFilterStrategy implements RawArticleFilterStrategy {

    // Insieme immutabile di categorie di servizio/cancellazione (tutte minuscole per confronto uniforme).
    // Set.of(...) offre una ricerca veloce e impedisce modifiche accidentali a runtime.
    private static final Set<String> EXCLUDED_CATEGORIES = Set.of(
            "da cancellare subito",
            "da cancellare",
            "pagine da controllare",
            "stub"
    );

    @Override
    public boolean accept(BaseJsonNode rawArticle) {
        // Se il nodo JSON è nullo o mancante, scarta l'articolo in sicurezza.
        if (rawArticle == null || rawArticle.isMissingNode()) {
            return false;
        }

        // path() naviga il JSON senza lanciare NullPointerException se un campo manca;
        // asText("") restituisce stringa vuota in quel caso; trim() toglie spazi iniziali/finali.
        String title = rawArticle.path("name").asText("").trim();
        String html = rawArticle.path("article_body").path("html").asText("").trim();

        // Se manca il titolo o manca il corpo HTML della notizia, non ha senso continuare.
        if (title.isEmpty() || html.isEmpty()) {
            return false;
        }

        // Controlla l'array "categories" del JSON: se contiene una categoria esclusa, scarta l'articolo.
        JsonNode categoriesNode = rawArticle.path("categories");
        if (categoriesNode.isArray()) {
            for (JsonNode catNode : categoriesNode) {
                // Converte in minuscolo usando Locale.ITALIAN per gestire bene i caratteri italiani.
                String normalizedCat = catNode.asText("").trim().toLowerCase(Locale.ITALIAN);
                if (EXCLUDED_CATEGORIES.contains(normalizedCat)) {
                    return false;
                }
            }
        }

        // Tutti i controlli preliminari sono superati: l'articolo può passare all'estrattore HTML.
        return true;
    }
}