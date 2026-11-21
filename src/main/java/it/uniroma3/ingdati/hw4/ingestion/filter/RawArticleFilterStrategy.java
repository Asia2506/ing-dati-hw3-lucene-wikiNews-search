package it.uniroma3.ingdati.hw4.ingestion.filter;

import tools.jackson.databind.node.BaseJsonNode;

// Interfaccia della strategia (pattern GoF Strategy) per il filtro PRE-PARSING:
// opera sul nodo JSON grezzo letto dalla riga dell'NDJSON prima di fare il parsing HTML con Jsoup.
public interface RawArticleFilterStrategy {

    // Restituisce true se il record JSON deve proseguire verso l'estrazione HTML,
    // oppure false se va scartato subito (es. pagina senza titolo/testo o in categoria di servizio).
    boolean accept(BaseJsonNode rawArticle);
}