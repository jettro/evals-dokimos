package dev.evals.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.lang.NonNull;

public class MyLoggingAdvisor implements BaseAdvisor {
    private static final Logger log = LoggerFactory.getLogger(MyLoggingAdvisor.class);

    @Override
    @NonNull
    public ChatClientRequest before(@NonNull ChatClientRequest chatClientRequest, @NonNull AdvisorChain advisorChain) {
        log.info("The user message for this request: {}", chatClientRequest.prompt().getUserMessage().getText());
        return chatClientRequest;
    }

    @Override
    @NonNull
    public ChatClientResponse after(@NonNull ChatClientResponse chatClientResponse, @NonNull AdvisorChain advisorChain) {
        if (chatClientResponse.chatResponse() != null) {
            var chatResponse = chatClientResponse.chatResponse();
            chatResponse.getResults().forEach(result -> {
                log.info("Finish reason for result: {}", result.getMetadata().getFinishReason());
            });
            log.info("The assistant message for this response: {}", chatResponse.getResult().getOutput().getText());
        }
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
