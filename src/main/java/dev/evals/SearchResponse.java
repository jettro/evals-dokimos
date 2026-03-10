package dev.evals;

import java.util.List;
import java.util.Map;

public record SearchResponse(List<SearchResult> results) {
    public record SearchResult(String content, Map<String, Object> metadata, float score) {}
}
