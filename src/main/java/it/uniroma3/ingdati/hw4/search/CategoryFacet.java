package it.uniroma3.ingdati.hw4.search;

// Rappresenta una faccetta di categoria con il numero di documenti corrispondenti.
public record CategoryFacet(
        String category,
        int count
) {
}