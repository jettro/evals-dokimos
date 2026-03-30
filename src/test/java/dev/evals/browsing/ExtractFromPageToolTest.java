package dev.evals.browsing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtractFromPageToolTest {

    @Test
    void testExtractFromPageSuccess() {
        ExtractFromPageTool tool = new ExtractFromPageTool(null);
        String url = "https://slijterijdehelm.nl/winkel/whisky/malt/togouchi-beer-cask/";
        String result = tool.extractFromPage(url);
        
        assertNotNull(result);
        assertFalse(result.startsWith("Error"));
        assertTrue(result.contains("Togouchi Beer Cask"));
        assertTrue(result.contains("IPA-biervat"));
    }

    @Test
    void testExtractFromPageNotFound() {
        ExtractFromPageTool tool = new ExtractFromPageTool(null);
        // Use a page that likely doesn't have the tab-description id
        String result = tool.extractFromPage("https://www.google.com");
        assertTrue(result.contains("Error: Element with id 'tab-description' not found"));
    }

    @Test
    void testExtractFromPageInvalidUrl() {
        ExtractFromPageTool tool = new ExtractFromPageTool(null);
        String result = tool.extractFromPage("invalid-url");
        assertTrue(result.contains("Error fetching the page"));
    }
}
