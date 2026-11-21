package it.uniroma3.ingdati.hw4.ingestion.filter;

import it.uniroma3.ingdati.hw4.model.NewsArticle;

// Interfaccia della strategia (pattern GoF Strategy) per il filtro POST-PARSING:
// opera sull'oggetto NewsArticle già costruito e ripulito da Jsoup.
public interface ParsedArticleFilterStrategy {

    // Restituisce true se l'articolo ripulito è valido per l'indicizzazione in Lucene,
    // oppure false se va scartato (es. testo finale troppo corto o vuoto).
    boolean accept(NewsArticle article);
}