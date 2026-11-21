package it.uniroma3.ingdati.hw4.search;

import org.apache.lucene.search.similarities.Similarity;

// Strategia (pattern GoF Strategy) per scegliere il modello di ranking in Lucene (Q2):
// permette di passare facilmente da BM25 a TF-IDF (ClassicSimilarity).
public interface RankingStrategy {

    // Restituisce l'istanza di Similarity da assegnare a IndexSearcher.
    Similarity createSimilarity();

    // Nome leggibile del modello di ranking (utile per statistiche e Web UI).
    String name();
}