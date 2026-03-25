package dev.evals;

import dev.evals.indexing.LuceneDatastore;
import dev.evals.model.SearchRequest;
import dev.evals.model.SearchResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * A tool that fetches a URL and extracts content from a specific div element.
 */
@Component
public class ExtractFromPageTool {
    private static final Logger log = LoggerFactory.getLogger(ExtractFromPageTool.class);

    private final LuceneDatastore luceneDatastore;

    public ExtractFromPageTool(LuceneDatastore luceneDatastore) {
        this.luceneDatastore = luceneDatastore;
    }

    /**
     * Fetches a web page and extracts content from the element with ID "tab-description".
     *
     * @param url the URL of the page to fetch
     * @return the extracted content, or an error message if the fetch fails
     */
    @Tool(description = "Extract product description from a specific URL. " +
            "It looks for the element with id 'tab-description' which typically contains " +
            "the main product details and notes.")
    public String extractFromPage(@ToolParam(description = "The URL to extract content from") String url) {
        log.info(">>> ExtractFromPageTool: fetching {}", url);
        try {
            // Fetch and parse the page
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .get();

            // Find the specific element
            Element element = doc.getElementById("tab-description");
            if (element == null) {
                log.warn("<<< ExtractFromPageTool: element 'tab-description' not found at {}", url);
                return "Error: Element with id 'tab-description' not found on the page.";
            }

            // Extract content - returning text usually works better for LLMs than raw HTML
            // but we can return text with some structure or keep it as is.
            String content = element.text();
            
            // If the user wants the paragraphs, we might want to iterate over them to preserve spacing
            StringBuilder sb = new StringBuilder();
            for (Element p : element.select("p")) {
                sb.append(p.text()).append("\n\n");
            }
            
            String result = sb.length() > 0 ? sb.toString().trim() : content;

            log.info("<<< ExtractFromPageTool: OK, extracted {} chars", result.length());
            return result;
        } catch (Exception e) {
            log.error("<<< ExtractFromPageTool: error fetching {}: {}", url, e.getMessage());
            return "Error fetching the page: " + e.getMessage();
        }
    }

    /**
     * Searches for a whisky using the Lucene implementation and returns the product URL and other info.
     *
     * @param query the search query
     * @return the search results as a formatted string
     */
    @Tool(description = "Search for a whisky using the local Lucene index. " +
            "Returns product details including the 'detail_link' which is the URL of the product page.")
    public String searchWhisky(@ToolParam(description = "The search query (e.g. name of the whisky)") String query) {
        log.info(">>> searchWhisky: {}", query);
        try {
            SearchResponse response = luceneDatastore.search(new SearchRequest(query, 5));
            if (response.results().isEmpty()) {
                log.info("<<< searchWhisky: no results for {}", query);
                return "No whiskies found for the search query: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found whiskies:\n");
            for (SearchResponse.SearchResult result : response.results()) {
                sb.append("- Name: ").append(result.metadata().get("name")).append("\n");
                sb.append("  Price: ").append(result.metadata().get("price")).append("\n");
                sb.append("  URL: ").append(result.metadata().get("detail_link")).append("\n");
                sb.append("  Tags: ").append(result.metadata().get("tags")).append("\n\n");
            }

            log.info("<<< searchWhisky: found {} results", response.results().size());
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("<<< searchWhisky: error searching for {}: {}", query, e.getMessage());
            return "Error searching for whisky: " + e.getMessage();
        }
    }

    @Tool(description = "Place an order for a whisky using the provided name. Returns a message about the order status.")
    public String orderWhisky(@ToolParam(description = "The product name to order") String name) {
        log.info(">>> orderWhisky: {}", name);
        return "Order for " + name + " has been placed.";
    }
}
