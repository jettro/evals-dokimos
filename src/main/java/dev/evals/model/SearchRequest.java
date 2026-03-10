package dev.evals.model;

public record SearchRequest(String query, int topK) {
    public SearchRequest(String query) {
        this(query, 10);
    }
}
