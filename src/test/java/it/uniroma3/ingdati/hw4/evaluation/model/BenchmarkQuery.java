package it.uniroma3.ingdati.hw4.evaluation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.Map;

/**
 * Domain model representing a benchmark evaluation query with relevance assessments (qrels).
 */
public class BenchmarkQuery {

    @JsonProperty("id")
    private String id;

    @JsonProperty("query")
    private String query;

    @JsonProperty("qrels")
    private Map<String, Integer> qrels; // docId -> relevance grade (e.g. 0, 1, 2)

    public BenchmarkQuery() {}

    public BenchmarkQuery(String id, String query, Map<String, Integer> qrels) {
        this.id = id;
        this.query = query;
        this.qrels = qrels != null ? qrels : Collections.emptyMap();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Map<String, Integer> getQrels() {
        return qrels != null ? qrels : Collections.emptyMap();
    }

    public void setQrels(Map<String, Integer> qrels) {
        this.qrels = qrels;
    }

    public boolean isRelevant(String docId) {
        Integer rel = getQrels().get(docId);
        return rel != null && rel > 0;
    }

    public int getRelevanceGrade(String docId) {
        return getQrels().getOrDefault(docId, 0);
    }

    public int getTotalRelevantDocuments() {
        int count = 0;
        for (int grade : getQrels().values()) {
            if (grade > 0) count++;
        }
        return count;
    }
}

