package dev.evals;

import dev.evals.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController( "/")
@Tag(name = "Chat API", description = "Endpoints for interacting with the AI chat and RAG tools")
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final WhiskyService whiskyService;

    public ChatController(ChatService chatService, WhiskyService whiskyService) {
        this.chatService = chatService;
        this.whiskyService = whiskyService;
        logger.info("ChatController initialized");
    }

    @PostMapping("/chat")
    @Operation(summary = "Simple chat message", description = "Sends a message to the AI and returns the response")
    @ApiResponse(responseCode = "200", description = "Successful response")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        logger.info("Received chat message: {}", request.message());
        var response = chatService.chat(request.message());
        return new ChatResponse(response);
    }

    @PostMapping("/chatrag")
    @Operation(summary = "Chat with RAG tools", description = "Sends a message to the AI using RAG tools and returns the whisky search result")
    @ApiResponse(responseCode = "200", description = "Successful response")
    public WhiskySearchResult chatRag(@RequestBody ChatRequest request) {
        logger.info("Received chat rag message: {}", request.message());
        var response = chatService.chatRAGTools(request.message());
        return response.result();
    }

    @PostMapping("/whiskychat")
    @Operation(summary = "Chat about whisky", description = "Sends a message to the AI about whisky, can find whisky by name, find more details and buy the whisky.")
    @ApiResponse(responseCode = "200", description = "Successful response")
    public WhiskyResponse whiskyChat(@RequestBody WhiskyRequest request) {
        logger.info("Received whisky chat request: {}", request);
        return whiskyService.talkAboutWhisky(request);
    }
}
