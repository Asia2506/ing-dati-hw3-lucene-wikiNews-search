package it.uniroma3.ingdati.hw4.search;

import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;

// Implementazione concreta del ranking basata su TF-IDF classico (ClassicSimilarity).
public class TfIdfRankingStrategy implements RankingStrategy {

    @Override
    public Similarity createSimilarity() {
        return new ClassicSimilarity();
    }

    @Override
    public String name() {
        return "TF-IDF";
    }
}