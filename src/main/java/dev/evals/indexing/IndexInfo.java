package dev.evals.indexing;

public record IndexInfo(int numDocs, int maxDoc, int numDeletedDocs, boolean hasDeletions) {
}
