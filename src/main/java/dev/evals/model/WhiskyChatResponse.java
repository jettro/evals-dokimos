package dev.evals.model;

import java.util.List;

public record WhiskyChatResponse(WhiskySearchResult result, List<ToolCallInfo> toolCalls) {}
