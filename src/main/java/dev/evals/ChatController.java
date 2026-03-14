package dev.evals;

import dev.evals.model.ChatRequest;
import dev.evals.model.ChatResponse;
import dev.evals.model.WhiskySearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController( "/")
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
        logger.info("ChatController initialized");
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        logger.info("Received chat message: {}", request.message());
        var response = chatService.chat(request.message());
        return new ChatResponse(response);
    }

    @PostMapping("/chatrag")
    public WhiskySearchResult chatRag(@RequestBody ChatRequest request) {
        logger.info("Received chat rag message: {}", request.message());
        var response = chatService.chatRAGTools(request.message());
        return response.result();
    }

}
