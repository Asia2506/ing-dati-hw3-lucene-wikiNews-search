package it.uniroma3.ingdati.hw4.search;

import java.util.List;

// Rappresenta un singolo risultato di ricerca restituito da Lucene.
public record SearchHitItem(
        long identifier,
        String title,
        String url,
        String snippet,
        List<String> categories,
        Integer year,
        float score
) {
    public SearchHitItem {
        categories = (categories == null) ? List.of() : List.copyOf(categories);
    }
}