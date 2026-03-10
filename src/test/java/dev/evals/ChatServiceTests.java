package dev.evals;

import dev.evals.indexing.IndexingPipeline;
import dev.evals.model.RagResponse;
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
}
