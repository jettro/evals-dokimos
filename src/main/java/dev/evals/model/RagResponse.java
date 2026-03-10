package dev.evals.model;

import java.util.List;

public record RagResponse(String content, List<String> foundDocuments) {
    public RagResponse(String text) {
        this(text, List.of());
    }
}
