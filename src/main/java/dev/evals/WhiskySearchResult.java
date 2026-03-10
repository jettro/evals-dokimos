package dev.evals;

public record WhiskySearchResult(
        String name,
        String price,
        String category,
        String volume,
        String alcoholPercentage,
        String description,
        boolean found
) {
    public static WhiskySearchResult notFound(String query) {
        return new WhiskySearchResult(query, null, null, null, null,
                "No results found on the website for: " + query, false);
    }
}
