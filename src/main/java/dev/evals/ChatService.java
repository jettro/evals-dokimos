package dev.evals;

import dev.evals.indexing.LuceneDatastore;
import dev.evals.logging.MyLoggingAdvisor;
import dev.evals.model.RagResponse;
import dev.evals.model.SearchRequest;
import dev.evals.model.SearchResponse;
import dev.evals.model.WhiskySearchResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Service;

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
    private final BrowserTool browserTool;

    public ChatService(ChatClient.Builder chatClientBuilder,
                       LuceneDatastore luceneDatastore,
                       BrowserTool browserTool) {
        this.chatClientBuilder = chatClientBuilder;
        this.luceneDatastore = luceneDatastore;
        this.browserTool = browserTool;

        ChatOptions chatOptions = ChatOptions.builder()
                .model("gpt-5-mini")
                .temperature(1.0)
                .build();

        this.chatClient = chatClientBuilder
                .defaultOptions(chatOptions)
                .build();
    }

    public String chat(String message) {
        PromptTemplate template = new PromptTemplate("You are a helpful assistant. The user is asking about whisky. " +
                "Question: {message}");
        return this.chatClient.prompt()
                .user(template.render(Map.of("message", message)))
                .call()
                .content();
    }

    public RagResponse chatRag(String message) {
        var transformer = RewriteQueryTransformer.builder()
                .promptTemplate(PromptTemplate.builder()
                        .template("Rewrite the following query to be suitable for a search on {target}: {query}. " +
                                "Extract only the terms for the query.")
                        .build())
                .targetSearchSystem("Lucene style search engine")
                .chatClientBuilder(chatClientBuilder.build().mutate())
                .build();

        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(transformer)
                .documentRetriever(query -> {
                    try {
                        SearchResponse searchResponse = luceneDatastore.search(new SearchRequest(query.text()));
                        System.out.println("Search results for '" + query.text() + "':");
                        return searchResponse.results().stream()
                                .map(searchResult -> new Document(searchResult.content()))
                                .toList();
                    } catch (Exception e) {
                        return List.of();
                    }
                })
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .promptTemplate(PromptTemplate.builder()
                                .template("""
                                        You are a whisky expert. Use the following context to answer the question.
                                        If the information is not in the context, say you don't know.
                                        
                                        Context:
                                        {context}
                                        
                                        Question:
                                        {query}
                                        """)
                                .build())
                        .build())
                .build();

        var response = this.chatClient.prompt()
                .user(message)
                .advisors(advisor)
                .call()
                .chatResponse();
        Object ragDocumentContext = response.getMetadata().get("rag_document_context");
        if (ragDocumentContext instanceof List<?> list) {
            System.out.println("RAG Document Context:");
            list.forEach(document -> {
                System.out.println("- " + document);
            });
            list.stream().map(Document.class::cast).forEach(System.out::println);
            List<String> docs = list.stream().map(Document.class::cast).map(Document::getText).toList();
            String joinedDocs = docs.stream().collect(Collectors.joining("\n---\n"));
            System.out.println("Joined documents: " + joinedDocs);
            return new RagResponse(response.getResult().getOutput().getText(), docs);
        }

        return new RagResponse(response.getResult().getOutput().getText());
    }

    public WhiskySearchResult chatTools(String message) {
        var loggingAdvisor = new MyLoggingAdvisor();
        return this.chatClient.prompt(
                        """
                                You are a whisky search assistant. The user provides the name of a whisky and you must
                                find it on the website https://slijterijdehelm.nl using the agent-browser CLI tool.
                                
                                You have a tool available to execute agent-browser commands.
                                
                                ## agent-browser Quick Reference
                                - `open <url>` — Navigate to a URL
                                - `snapshot -i` — List interactive elements with refs (@e1, @e2, ...)
                                - `snapshot -s "<css-selector>"` — Snapshot scoped to a CSS selector
                                - `fill @<ref> "text"` — Clear field and type text
                                - `click @<ref>` — Click an element
                                - `press Enter` — Press a key
                                - `wait --load networkidle` — Wait for page to finish loading
                                - `close` — Close the browser
                                
                                IMPORTANT: After every click or navigation, refs are invalidated. Always re-snapshot
                                to get fresh refs before interacting again.
                                
                                ## Steps to follow
                                1. Run `open https://slijterijdehelm.nl`
                                2. Run `snapshot -i` to find interactive elements
                                3. Handle any popups/overlays that may appear before searching.
                                   Look at the snapshot output and:
                                   - If you see a cookie consent popup (buttons containing words like
                                     "accepteren", "cookies", "toestaan", or "akkoord"), click the
                                     accept button. Then re-snapshot.
                                   - If you see an age verification overlay (links/buttons containing
                                     "ouder dan 18" or "18 jaar" or similar), click to confirm.
                                     Then re-snapshot.
                                   - If neither popup is visible, just continue to the next step.
                                   Do NOT get stuck looking for popups that aren't there.
                                4. Find the search field (a searchbox, often labeled "Zoeken") and fill it:
                                   `fill @<ref> "<whisky name>"`
                                5. Submit: `press Enter`
                                6. Wait: `wait --load networkidle`
                                7. Re-snapshot: `snapshot -i` to see search results
                                8. Click the most relevant product link matching the whisky name
                                9. Wait: `wait --load networkidle`
                                10. Extract the product info using a scoped snapshot:
                                    `snapshot -s ".elementor-location-single"`
                                    This returns the product name, price, description tabs, and details
                                    without all the navigation noise.
                                11. Close: `close`
                                
                                ## What to extract
                                From the scoped snapshot, extract and return:
                                - The full product name (from the heading)
                                - The price
                                - The category (from breadcrumb, e.g. Whisky / Malt)
                                - The volume (if available in the description)
                                - The alcohol percentage (from the description)
                                - The FULL longer description — this is the most important field,
                                  include ALL paragraphs from the "Beschrijving" tab including
                                  nose/taste/finish notes
                                
                                If no results are found, clearly state that.
                                Always close the browser when done.
                                """
                )
                .user(message)
                .tools(browserTool)
                .advisors(loggingAdvisor,
                        MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(20).build()).build())
                .call()
                .entity(WhiskySearchResult.class);
    }
}
