package it.uniroma3.ingdati.hw4.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Classe di utilita' non istanziabile (costruttore privato e metodi statici)
// per calcolare le metriche classiche di Information Retrieval: P@k, MAP@k e nDCG@k.
public final class IrMetricsCalculator {

    private IrMetricsCalculator() {
    }

    // Precision@k (P@k): misura quanti documenti rilevanti compaiono nei primi k risultati restituiti.
    // Formula: (numero di documenti rilevanti nelle prime k posizioni) / k.
    public static double precisionAtK(List<Long> rankedDocIds, Set<Long> relevantDocIds, int k) {
        // Caso limite: k non positivo, lista dei risultati vuota o nessun documento rilevante noto.
        if (k <= 0 || rankedDocIds == null || rankedDocIds.isEmpty() || relevantDocIds == null || relevantDocIds.isEmpty()) {
            return 0.0;
        }

        // Evita IndexOutOfBounds se il sistema restituisce meno di k documenti.
        int limit = Math.min(k, rankedDocIds.size());
        int relevantFound = 0;

        // Scorre le prime 'limit' posizioni (indici da 0 a limit - 1).
        for (int i = 0; i < limit; i++) {
            Long docId = rankedDocIds.get(i);
            if (relevantDocIds.contains(docId)) {
                relevantFound++;
            }
        }

        // Divide sempre per k (non per 'limit'), secondo la definizione standard di P@k.
        return (double) relevantFound / (double) k;
    }

    // Average Precision@k (AP@k) per una SINGOLA query:
    // premia i sistemi che posizionano i documenti rilevanti il piu' in alto possibile.
    // Per ogni posizione r (da 1 a k) in cui si trova un documento rilevante, si calcola P@r,
    // si sommano questi valori e si divide per min(k, numero totale di rilevanti).
    public static double averagePrecisionAtK(List<Long> rankedDocIds, Set<Long> relevantDocIds, int k) {
        if (k <= 0 || rankedDocIds == null || rankedDocIds.isEmpty() || relevantDocIds == null || relevantDocIds.isEmpty()) {
            return 0.0;
        }

        int limit = Math.min(k, rankedDocIds.size());
        int relevantSoFar = 0;
        double sumPrecisions = 0.0;

        for (int i = 0; i < limit; i++) {
            // Il rango (posizione in classifica) parte da 1, mentre l'indice della lista parte da 0.
            int rank = i + 1;
            Long docId = rankedDocIds.get(i);

            if (relevantDocIds.contains(docId)) {
                relevantSoFar++;
                // Precision calcolata esattamente al rango corrente 'rank'.
                double precisionAtRank = (double) relevantSoFar / (double) rank;
                sumPrecisions += precisionAtRank;
            }
        }

        // Normalizzazione standard tronca a k: al massimo si possono recuperare min(k, |relevant|) rilevanti entro k.
        int denominator = Math.min(k, relevantDocIds.size());
        if (denominator == 0) {
            return 0.0;
        }

        return sumPrecisions / (double) denominator;
    }

    // Mean Average Precision@k (MAP@k) su un INSIEME di query:
    // fa la media aritmetica delle Average Precision ottenute sulle singole query.
    public static double meanAveragePrecisionAtK(List<Double> averagePrecisions) {
        if (averagePrecisions == null || averagePrecisions.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        for (Double ap : averagePrecisions) {
            if (ap != null) {
                sum += ap.doubleValue();
            }
        }

        return sum / (double) averagePrecisions.size();
    }

    // Normalized Discounted Cumulative Gain@k (nDCG@k):
    // valuta il ranking considerando gradi di rilevanza non binari (es. 0 = non rilevante, 1, 2, 3 = molto rilevante)
    // e applicando uno sconto logaritmico man mano che si scende di posizione.
    // Formula: nDCG@k = DCG@k / IDCG@k, dove IDCG@k e' il DCG della classifica ideale (ordinata per rilevanza decrescente).
    public static double ndcgAtK(List<Long> rankedDocIds, Map<Long, Integer> relevanceGrades, int k) {
        if (k <= 0 || rankedDocIds == null || rankedDocIds.isEmpty() || relevanceGrades == null || relevanceGrades.isEmpty()) {
            return 0.0;
        }

        // 1. Calcola il DCG@k effettivo sulla classifica prodotta dal sistema.
        double dcg = computeDcgAtK(rankedDocIds, relevanceGrades, k);

        // 2. Estrae i soli gradi di rilevanza positivi (> 0) per costruire la classifica ideale.
        List<Integer> idealGains = new ArrayList<>();
        for (Integer grade : relevanceGrades.values()) {
            if (grade != null && grade.intValue() > 0) {
                idealGains.add(Integer.valueOf(grade.intValue()));
            }
        }
        Collections.sort(idealGains, Collections.reverseOrder());

        // Se non ci sono documenti con rilevanza positiva, nDCG@k e' 0.0.
        if (idealGains.isEmpty()) {
            return 0.0;
        }

        // 3. Calcola l'IDCG@k (massimo DCG@k teoricamente raggiungibile per quella query).
        double idcg = computeIdealDcgAtK(idealGains, k);
        if (idcg == 0.0) {
            return 0.0;
        }

        // 4. Normalizza tra 0.0 e 1.0.
        return dcg / idcg;
    }

    // Calcola il Discounted Cumulative Gain@k (DCG@k) lungo i primi 'limit' documenti restituiti.
    // Gain esponenziale: 2^rel - 1 (enfatizza i documenti altamente rilevanti).
    // Sconto logaritmico in base 2: log2(rank + 1), con rank = 1, 2, ...
    private static double computeDcgAtK(List<Long> rankedDocIds, Map<Long, Integer> relevanceGrades, int k) {
        int limit = Math.min(k, rankedDocIds.size());
        double dcg = 0.0;

        for (int i = 0; i < limit; i++) {
            int rank = i + 1;
            Long docId = rankedDocIds.get(i);
            int rel = relevanceGrades.getOrDefault(docId, 0).intValue();

            if (rel > 0) {
                double gain = Math.pow(2.0, rel) - 1.0;
                double discount = Math.log(rank + 1.0) / Math.log(2.0);
                dcg += gain / discount;
            }
        }

        return dcg;
    }

    // Calcola l'Ideal DCG@k (IDCG@k) a partire dai gradi di rilevanza gia' ordinati in senso decrescente.
    private static double computeIdealDcgAtK(List<Integer> sortedDescGrades, int k) {
        int limit = Math.min(k, sortedDescGrades.size());
        double idcg = 0.0;

        for (int i = 0; i < limit; i++) {
            int rank = i + 1;
            int rel = sortedDescGrades.get(i).intValue();

            if (rel > 0) {
                double gain = Math.pow(2.0, rel) - 1.0;
                double discount = Math.log(rank + 1.0) / Math.log(2.0);
                idcg += gain / discount;
            }
        }

        return idcg;
    }
}