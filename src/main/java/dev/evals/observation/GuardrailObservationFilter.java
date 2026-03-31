package dev.evals.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import static dev.evals.guardrail.GuardrailsKeys.REDACTED_USER_MSG_KEY;

/**
 * Observation filter to add credit card guardrail information to advisor observations. If the redacted user message
 * is present, it adds a high cardinality key-value pair to the observation context.
 */
@Component
public class GuardrailObservationFilter implements ObservationFilter {
    @Override
    @NonNull
    public Observation.Context map(@NonNull Observation.Context context) {
        if (!(context instanceof AdvisorObservationContext advisorObservationContext)) {
            return context;
        }

        String redactedUserMessage = advisorObservationContext.get(REDACTED_USER_MSG_KEY);

        if (redactedUserMessage != null) {
            advisorObservationContext.addHighCardinalityKeyValue(new KeyValue() {
                @Override
                @NonNull
                public String getKey() {
                    return "gen_ai.guardrail.credit_card_guardrail";
                }

                @Override
                @NonNull
                public String getValue() {
                    return String.format("Credit Card Guardrail: true with '%s'", redactedUserMessage);
                }
            });

        }

        return advisorObservationContext;
    }
}
