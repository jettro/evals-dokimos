package dev.evals.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GuardrailObservationFilter implements ObservationFilter {
    @Override
    @NonNull
    public Observation.Context map(@NonNull Observation.Context context) {
        if (!(context instanceof AdvisorObservationContext advisorObservationContext)) {
            return context;
        }

        String userMsg = advisorObservationContext.get("usrMsg");

        if (userMsg != null) {
            advisorObservationContext.addHighCardinalityKeyValue(new KeyValue() {
                @Override
                @NonNull
                public String getKey() {
                    return "gen_ai.guardrail.credit_card_guardrail";
                }

                @Override
                @NonNull
                public String getValue() {
                    return "Credit Card Guardrail: true with '" + userMsg + "'";
                }
            });

        }

        return advisorObservationContext;
    }
}
