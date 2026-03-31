package dev.evals.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.content.Content;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Observation filter to add chat model completion content information to advisor observations. If the completion content
 * is present, it adds a high cardinality key-value pair to the observation context.
 * The main part is copied from the original implementation, with minor adjustments to fit the new class and context.
 * gen_ai.completion - Available as output in Langfuse
 * gen_ai.prompt - Available as input in Langfuse
 * gen_ai.complete_prompt - Extra information that shows the full conversation.
 */
@Component
public class ChatModelCompletionContentObservationFilter implements ObservationFilter {

    @Override
    @NonNull
    public Observation.Context map(@NonNull Observation.Context context) {
        if (!(context instanceof ChatModelObservationContext chatModelObservationContext)) {
            return context;
        }

        var prompts = processPrompts(chatModelObservationContext);
        var completions = processCompletion(chatModelObservationContext);

        chatModelObservationContext.addHighCardinalityKeyValue(new KeyValue() {
            @Override
            @NonNull
            public String getKey() {
                return "gen_ai.complete_prompt";
            }

            @Override
            @NonNull
            public String getValue() {
                return ObservabilityHelper.concatenateStrings(prompts);
            }
        });

        chatModelObservationContext.addHighCardinalityKeyValue(new KeyValue() {
            @Override
            @NonNull
            public String getKey() {
                return "gen_ai.completion";
            }

            @Override
            @NonNull
            public String getValue() {
                return ObservabilityHelper.concatenateStrings(completions);
            }
        });

        chatModelObservationContext.addHighCardinalityKeyValue(new KeyValue() {
            @Override
            @NonNull
            public String getKey() {
                return "gen_ai.prompt";
            }

            @Override
            @NonNull
            public String getValue() {
                return chatModelObservationContext.getRequest().getUserMessage().getText();
            }
        });

        return chatModelObservationContext;
    }

    private List<String> processPrompts(ChatModelObservationContext chatModelObservationContext) {
        return CollectionUtils.isEmpty((chatModelObservationContext.getRequest()).getInstructions()) ? List.of() : (chatModelObservationContext.getRequest()).getInstructions().stream().map(Content::getText).toList();
    }

    private List<String> processCompletion(ChatModelObservationContext context) {
        if (context.getResponse() == null
                || CollectionUtils.isEmpty(context.getResponse().getResults())) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        for (var generation : context.getResponse().getResults()) {
            // Capture text output
            if (StringUtils.hasText(generation.getOutput().getText())) {
                completions.add(generation.getOutput().getText());
            }
            // Capture tool calls from the assistant message
            if (!CollectionUtils.isEmpty(generation.getOutput().getToolCalls())) {
                for (var toolCall : generation.getOutput().getToolCalls()) {
                    completions.add("[tool_call] " + toolCall.name() + "(" + toolCall.arguments() + ")");
                }
            }
        }
        return completions;
    }
}