package it.uniroma3.ingdati.hw4.search;

import java.util.List;

// Risultato completo di una ricerca: documenti paginati, faccette, modello usato e tempo impiegato.
public record SearchResponse(
        List<SearchHitItem> hits,
        long totalHits,
        List<CategoryFacet> categoryFacets,
        String rankingModel,
        long elapsedMillis,
        int page,
        int pageSize
) {
    public SearchResponse {
        hits = (hits == null) ? List.of() : List.copyOf(hits);
        categoryFacets = (categoryFacets == null) ? List.of() : List.copyOf(categoryFacets);
    }

    // Calcola il numero totale di pagine per la barra di paginazione della Web UI.
    public int totalPages() {
        if (totalHits <= 0 || pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalHits / pageSize);
    }
}