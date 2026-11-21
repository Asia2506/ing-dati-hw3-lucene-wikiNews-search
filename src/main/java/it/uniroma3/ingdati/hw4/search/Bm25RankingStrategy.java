package it.uniroma3.ingdati.hw4.search;

import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;

// Implementazione concreta del ranking basata su BM25.
public class Bm25RankingStrategy implements RankingStrategy {

    @Override
    public Similarity createSimilarity() {
        return new BM25Similarity();
    }

    @Override
    public String name() {
        return "BM25";
    }
}