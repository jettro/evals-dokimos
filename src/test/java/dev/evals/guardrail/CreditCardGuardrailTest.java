package dev.evals.guardrail;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CreditCardGuardrailTest {

    @Test
    void testCreditCardRedaction() {
        CreditCardGuardrail guardrail = new CreditCardGuardrail();
        ChatClientRequest request = mock(ChatClientRequest.class);
        Prompt prompt = mock(Prompt.class);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);

        when(request.prompt()).thenReturn(prompt);

        // Test with hyphens
        when(prompt.getUserMessage()).thenReturn(new UserMessage("My card is 1234-5678-9012-3456"));
        guardrail.adviseCall(request, chain);
        verify(prompt).augmentUserMessage("My card is CC_AVAILABLE");
        reset(prompt);
        when(request.prompt()).thenReturn(prompt);

        // Test with spaces
        when(prompt.getUserMessage()).thenReturn(new UserMessage("My card is 1234 5678 9012 3456"));
        guardrail.adviseCall(request, chain);
        verify(prompt).augmentUserMessage("My card is CC_AVAILABLE");
        reset(prompt);
        when(request.prompt()).thenReturn(prompt);

        // Test without spaces
        when(prompt.getUserMessage()).thenReturn(new UserMessage("My card is 1234567890123456"));
        guardrail.adviseCall(request, chain);
        verify(prompt).augmentUserMessage("My card is CC_AVAILABLE");
        reset(prompt);
        when(request.prompt()).thenReturn(prompt);

        // Test with Visa (13 digits)
        when(prompt.getUserMessage()).thenReturn(new UserMessage("Visa: 4111111111111"));
        guardrail.adviseCall(request, chain);
        verify(prompt).augmentUserMessage("Visa: CC_AVAILABLE");
        reset(prompt);
        when(request.prompt()).thenReturn(prompt);

        // Test no redaction for unrelated numbers
        when(prompt.getUserMessage()).thenReturn(new UserMessage("My phone is 123-456-7890"));
        guardrail.adviseCall(request, chain);
        verify(prompt, never()).augmentUserMessage(anyString());

        verify(chain, times(5)).nextCall(request);
    }
}
