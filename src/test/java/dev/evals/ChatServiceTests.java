package dev.evals;

import dev.evals.indexing.IndexingPipeline;
import dev.evals.model.RagResponse;
import dev.evals.model.WhiskyChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChatServiceTests {

    @Autowired
    ChatService chatService;

    @Autowired
    IndexingPipeline indexingPipeline;

    @Test
    void testChatRag() throws Exception {
        RagResponse ragResponse = chatService.chatRag("Find me a whisky from Milroy's");
        assertNotNull(ragResponse);
        System.out.println("RAG Response: " + ragResponse.content());
    }

    @Test
    void testChatTools() throws Exception {
        // Ensure index is populated
        indexingPipeline.runIndexing(false);

        WhiskyChatResponse response = chatService.chatRAGTools("Find me information about Togouchi Beer Cask");
        assertNotNull(response);
        assertNotNull(response.result());
        assertNotNull(response.toolCalls());

        System.out.println("Result: " + response.result());
        System.out.println("Tool Calls:");
        response.toolCalls().forEach(tc -> {
            System.out.println("- " + tc.name() + "(" + tc.arguments() + ") -> " + tc.response());
        });

        // The model should have used at least searchWhisky and extractFromPage
        boolean searchCalled = response.toolCalls().stream().anyMatch(tc -> tc.name().equals("searchWhisky"));
        boolean extractCalled = response.toolCalls().stream().anyMatch(tc -> tc.name().equals("extractFromPage"));

        assertTrue(searchCalled, "searchWhisky should have been called");
        assertTrue(extractCalled, "extractFromPage should have been called");
    }
}
