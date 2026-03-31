package dev.evals.guardrail;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext;
import org.springframework.lang.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.evals.guardrail.GuardrailsKeys.REDACTED_USER_MSG_KEY;

/**
 * A guardrail that redacts credit card information from user messages. Implements the {@link CallAdvisor} interface.
 */
public class CreditCardGuardrail implements CallAdvisor {
    private static final Logger log = LoggerFactory.getLogger(CreditCardGuardrail.class);
    private static final String CREDIT_CARD_GUARDRAIL = "credit_card_guardrail";
    
    private static final String CREDIT_CARD_REGEX = "\\b(?:\\d[ -]?){13,16}\\b";
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(CREDIT_CARD_REGEX);


    @Override
    @NonNull
    public ChatClientResponse adviseCall(@NonNull ChatClientRequest chatClientRequest, @NonNull CallAdvisorChain callAdvisorChain) {
        String userText = chatClientRequest.prompt().getUserMessage().getText();
        Matcher matcher = CREDIT_CARD_PATTERN.matcher(userText);

        if (!matcher.find()) {
            log.info("No credit card information found in user message");
            return requireResponse(callAdvisorChain.nextCall(chatClientRequest));
        }

        log.info("Redacting credit card information from user message");

        String redacted = matcher.replaceAll("CC_AVAILABLE");

        ObservationRegistry observationRegistry = callAdvisorChain.getObservationRegistry();

        if (observationRegistry.getCurrentObservation() != null) {
            var context = observationRegistry.getCurrentObservation().getContext();
            if (context instanceof AdvisorObservationContext advisorObservationContext) {
                advisorObservationContext.put(REDACTED_USER_MSG_KEY, redacted);
            }
        }

        ChatClientRequest nextRequest = chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().augmentUserMessage(redacted))
                .build();

        return requireResponse(callAdvisorChain.nextCall(nextRequest));
    }

    @Override
    @NonNull
    public String getName() {
        return CREDIT_CARD_GUARDRAIL;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private static ChatClientResponse requireResponse(ChatClientResponse response) {
        if (response == null) {
            throw new IllegalStateException("Next advisor in chain returned null");
        }
        return response;
    }
}
