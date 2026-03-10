package dev.evals.indexing;

import dev.evals.model.SearchRequest;
import dev.evals.model.SearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LuceneDatastoreTests {

    @Autowired
    LuceneDatastore luceneDatastore;

    @Autowired
    IndexingPipeline indexingPipeline;

    @Test
    void testIndexingAndSearching() throws Exception {
        // Run indexing with recreate = true
        indexingPipeline.runIndexing(true);

        // Search for a specific whisky
        SearchRequest request = new SearchRequest("Milroy's", 5);
        SearchResponse response = luceneDatastore.search(request);

        assertNotNull(response);
        assertFalse(response.results().isEmpty(), "Should find at least one Milroy's whisky");
        
        System.out.println("Search results for 'Milroy's':");
        response.results().forEach(r -> {
            System.out.println("- " + r.metadata().get("name") + " (score: " + r.score() + ")");
        });

        // Verify metadata
        SearchResponse.SearchResult first = response.results().getFirst();
        assertTrue(first.metadata().containsKey("name"));
        assertTrue(first.metadata().containsKey("price"));
        assertTrue(first.metadata().containsKey("tags"));
    }

    @Test
    void testSearchNoResults() throws Exception {
        indexingPipeline.runIndexing(false);
        
        SearchRequest request = new SearchRequest("NonExistentWhisky12345", 5);
        SearchResponse response = luceneDatastore.search(request);

        assertNotNull(response);
        assertTrue(response.results().isEmpty(), "Should not find any results for non-existent whisky");
    }

    @Test
    void testCount() throws Exception {
        indexingPipeline.runIndexing(true);
        int total = luceneDatastore.count();
        System.out.println("Total documents indexed: " + total);
        assertTrue(total > 0, "There should be more than 0 documents");
        assertEquals(953, total, "There should be exactly 953 documents in the whisky CSV");
    }

    @Test
    void testGetIndexInfo() throws Exception {
        indexingPipeline.runIndexing(true);
        IndexInfo info = luceneDatastore.getIndexInfo();
        
        assertNotNull(info);
        assertEquals(953, info.numDocs());
        assertEquals(953, info.maxDoc());
        assertEquals(0, info.numDeletedDocs());
        assertFalse(info.hasDeletions());
    }
}
