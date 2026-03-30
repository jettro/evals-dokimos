package dev.evals;

import dev.evals.browsing.ExtractFromPageTool;
import dev.evals.guardrail.CreditCardGuardrail;
import dev.evals.model.WhiskyRequest;
import dev.evals.model.WhiskyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WhiskyService {
    private static final Logger log = LoggerFactory.getLogger(WhiskyService.class);

    private final ChatClient chatClient;
    private final ExtractFromPageTool extractFromPageTool;


    public WhiskyService(ChatClient.Builder chatClientBuilder, ExtractFromPageTool extractFromPageTool, ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).order(10).build())
                .build();
        this.extractFromPageTool = extractFromPageTool;
    }

    public WhiskyResponse talkAboutWhisky(WhiskyRequest whiskyRequest) {
        var systemPrompt = """
                You are a whisky assistant. You search for whisky in a provided tool. The tool is Lucene based, so 
                choose the keywords for you search wisely. If the user asks for details, the response of the search 
                contains the url of the product page. Use this product page to extract the product details. With the 
                second tool that is available. With the third tool, you can order the whisky using the name of the 
                whisky. For the order, you need a credit card number. If the user asks you to buy the whisky, you can 
                use the order tool.
                
                You have tools available:
                1. searchWhisky — Search for a whisky in the local index to get its product URL. Preferred first step.
                2. extractFromPage — Efficiently extract product description from a URL using Jsoup. Use this once you have a URL.
                3. orderWhisky — Order a whisky using the name of the whisky. Use this once you have the name of the whisky.
                
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

        PromptTemplate userTemplate = PromptTemplate.builder()
                .template("This is the message from the user: {message}")
                .variables(Map.of("message", whiskyRequest.message()))
                .build();

        Map<String, Object> toolContext = whiskyRequest.ccNumber() != null ?
                Map.of("ccNumber", (Object) whiskyRequest.ccNumber()) :
                Map.of();

        var result = this.chatClient.prompt()
                .system(systemPrompt)
                .user(userTemplate.render())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, whiskyRequest.conversationId()))
                .advisors(new CreditCardGuardrail())
                .tools(extractFromPageTool)
                .toolContext(toolContext)
                .call()
                .chatResponse();

        if (result != null) {
            Generation generation = result.getResult();
            return new WhiskyResponse(generation.getOutput().getText());
        }
        return new WhiskyResponse("Sorry, I couldn't find anything.");
    }
}
