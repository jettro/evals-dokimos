package dev.evals;

import dev.evals.browsing.AgenticWhiskyTool;
import dev.evals.indexing.LuceneDatastore;
import dev.evals.logging.MyLoggingAdvisor;
import dev.evals.model.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Use structured output
 * <p>
 * Write a RAG to find a product best math by name.
 * <p>
 * Write a tool to mark favorite products.
 * <p>
 * Use MCP to find more information using the website.
 */
@Service
public class ChatService {
    private final ChatClient.Builder chatClientBuilder;
    private final ChatClient chatClient;
    private final LuceneDatastore luceneDatastore;
    private final AgenticWhiskyTool agenticWhiskyTool;

    public ChatService(ChatClient.Builder chatClientBuilder,
                       LuceneDatastore luceneDatastore,
                       AgenticWhiskyTool agenticWhiskyTool) {
        this.chatClientBuilder = chatClientBuilder;
        this.luceneDatastore = luceneDatastore;
        this.agenticWhiskyTool = agenticWhiskyTool;

        this.chatClient = chatClientBuilder.build();
    }

    /**
     * This is an elemental LLM call, a single question and a single answer.
     *
     * @param message The message of the user containing the question.
     * @return The answer of the LLM, the assistant's response.
     */
    public String chat(String message) {
        var systemPrompt = """
                You are a helpful assistant. You know everything about whisky. Answer only questions about whisky.
                Reply you are not created to answer questions about other topics than whisky if users ask different
                questions.
                """;

        PromptTemplate userTemplate = PromptTemplate.builder()
                .template("This is the question from the user: {message}")
                .variables(Map.of("message", message))
                .build();

        return this.chatClient.prompt()
                .system(systemPrompt)
                .user(userTemplate.render())
                .call()
                .content();
    }

    public RagResponse chatWithRag(String message) {
        var rewriteQueryTransformer = RewriteQueryTransformer.builder()
                .targetSearchSystem("Lucene style search engine")
                .chatClientBuilder(chatClientBuilder.build().mutate())
                .build();

        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(rewriteQueryTransformer)
                .documentRetriever(this::searchAndConvert)
                .queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(true).build())
                .build();

        var response = this.chatClient.prompt()
                .user(message)
                .advisors(advisor)
                .call()
                .chatResponse();

        // Build the response from the chat result if the response has rag documents
        assert response != null;
        Object ragDocumentContext = response.getMetadata().get("rag_document_context");
        if (ragDocumentContext instanceof List<?> ragResults) {
            List<String> docs = ragResults.stream()
                    .map(Document.class::cast)
                    .map(Document::getText)
                    .toList();
            return new RagResponse(response.getResult().getOutput().getText(), docs);
        }

        return new RagResponse(response.getResult().getOutput().getText());
    }

    /**
     * Retrieves documents relevant to the given query using Lucene search.
     *
     * @param query The query to search for.
     * @return A list of documents retrieved from the search.
     */
    private List<Document> searchAndConvert(Query query) {
        try {
            SearchResponse searchResponse = luceneDatastore.search(new SearchRequest(query.text()));
            return searchResponse.results().stream()
                    .map(searchResult -> new Document(searchResult.content()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }


    public WhiskyChatResponse chatRAGTools(String query) {
        var loggingAdvisor = new MyLoggingAdvisor();
        List<ToolCallInfo> toolCallHistory = new ArrayList<>();

        String systemInstructions = """
                You are a whisky search assistant. The user asks you something about a specific whisky. You search for
                the whiskey in a provided tool. The tool is Lucene based, so choose the keywords for you search wisely. The 
                response of the search contains the url of the product page. Use this product page to extract the product 
                details. With the second tool that is available.
                
                You have tools available:
                1. searchWhisky — Search for a whisky in the local index to get its product URL. Preferred first step.
                2. extractFromPage — Efficiently extract product description from a URL using Jsoup. Use this once you have a URL.
                
                ## Recommended Strategy
                1. Call `searchWhisky` with keywords for a whisky name.
                2. If you find a result with a URL, use `extractFromPage` with that URL.
                If nothing is found, say you don't know.
                
                ## What to extract
                From the content, extract and return:
                - The full product name (from the heading)
                - The price
                - The category (from breadcrumb, e.g. Whisky / Malt)
                - The volume (if available in the description)
                - The alcohol percentage (from the description)
                - The FULL longer description — this is the most important field,
                  include ALL paragraphs from the "Beschrijving" tab including
                  nose/taste/finish notes
                
                If no results are found, clearly state that.
                """;


        List<Message> history = new ArrayList<>();
        history.add(new SystemMessage(systemInstructions));
        history.add(new UserMessage(query));

        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(agenticWhiskyTool))
                .internalToolExecutionEnabled(false)
                .build();

        ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

        ChatResponse chatResponse = this.chatClient.prompt()
                .messages(history)
                .options(options)
                .advisors(loggingAdvisor)
                .call()
                .chatResponse();

        while (true) {
            assert chatResponse != null;
            if (!chatResponse.hasToolCalls()) break;
            // Execute tool calls manually
            ToolExecutionResult executionResult = toolCallingManager.executeToolCalls(new Prompt(history, options),
                    chatResponse);

            // Add messages to history (includes the assistant message and the tool responses)
            List<Message> newMessages = executionResult.conversationHistory();

            // Track tool calls and responses for the final response
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

            // Find the ToolResponseMessage in the new history
            ToolResponseMessage toolResponseMessage = (ToolResponseMessage) newMessages.stream()
                    .filter(msg -> msg instanceof ToolResponseMessage)
                    .reduce((first, second) -> second) // Get the last one
                    .orElse(null);

            if (toolResponseMessage != null) {
                Map<String, String> responsesById = toolResponseMessage.getResponses().stream()
                        .collect(Collectors.toMap(tr -> tr.id(), tr -> String.valueOf(tr.responseData())));

                for (AssistantMessage.ToolCall tc : toolCalls) {
                    String response = responsesById.getOrDefault(tc.id(), "No response found");
                    toolCallHistory.add(new ToolCallInfo(tc.name(), tc.arguments(), response));
                }
            }

            history = newMessages;

            // Call the model again with the updated history
            chatResponse = this.chatClient.prompt()
                    .messages(history)
                    .options(options)
                    .advisors(loggingAdvisor)
                    .call()
                    .chatResponse();
        }

        // Final structured output conversion
        // We call the model one last time with the structured output instructions
        WhiskySearchResult result = this.chatClient.prompt()
                .messages(history)
                .options(options)
                .call()
                .entity(WhiskySearchResult.class);

        return new WhiskyChatResponse(result, toolCallHistory);
    }
}
