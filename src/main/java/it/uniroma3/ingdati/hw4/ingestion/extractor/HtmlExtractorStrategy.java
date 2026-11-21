package it.uniroma3.ingdati.hw4.ingestion.extractor;

// Contratto per l'estrazione del testo pulito a partire dall'HTML grezzo dell'articolo.
public interface HtmlExtractorStrategy {

    // Riceve il codice HTML grezzo (es. article_body.html del JSON)
    // e restituisce il testo ripulito pronto per l'indicizzazione e gli snippet.
    String extractCleanText(String rawHtml);
}