package dev.evals.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

public class CreditCardGuardrail implements CallAdvisor {
    private static final Logger log = LoggerFactory.getLogger(CreditCardGuardrail.class);
    private static final String CREDIT_CARD_GUARDRAIL = "credit_card_guardrail";
    
    private static final String CREDIT_CARD_REGEX = "\\b(?:\\d[ -]?){13,16}\\b";
    
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        log.info("AdviseCall for CreditCardGuardrail called");
        String userText = chatClientRequest.prompt().getUserMessage().getText();
        log.info("User message: {}", userText);
        String redacted = userText.replaceAll(CREDIT_CARD_REGEX, "CC_AVAILABLE");

        if (!redacted.equals(userText)) {
            log.info("Redacting credit card information from user message");
            var updatedPrompt = chatClientRequest.prompt().augmentUserMessage(redacted);
            ChatClientRequest updatedRequest = chatClientRequest.mutate().prompt(updatedPrompt).build();
            return callAdvisorChain.nextCall(updatedRequest);
        }

        return callAdvisorChain.nextCall(chatClientRequest);
    }

    @Override
    public String getName() {
        return CREDIT_CARD_GUARDRAIL;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
