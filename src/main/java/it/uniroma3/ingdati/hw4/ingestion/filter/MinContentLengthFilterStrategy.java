package it.uniroma3.ingdati.hw4.ingestion.filter;

import it.uniroma3.ingdati.hw4.model.NewsArticle;

// Implementazione concreta del filtro post-parsing:
// verifica che il testo ripulito estratto dall'HTML raggiunga una lunghezza minima.
public class MinContentLengthFilterStrategy implements ParsedArticleFilterStrategy {

    // Soglia minima di caratteri richiesta sul campo content ripulito.
    private final int minLength;

    // Costruttore che imposta la soglia minima e valida che non sia negativa.
    public MinContentLengthFilterStrategy(int minLength) {
        if (minLength < 0) {
            throw new IllegalArgumentException("minLength must be >= 0");
        }
        this.minLength = minLength;
    }

    @Override
    public boolean accept(NewsArticle article) {
        // Se l'articolo o il suo testo pulito sono nulli, l'articolo non è valido.
        if (article == null || article.content() == null) {
            return false;
        }

        // Controlla la lunghezza del testo ripulito (senza spazi ai bordi) rispetto alla soglia.
        return article.content().trim().length() >= minLength;
    }
}