package dev.evals.guardrail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;

import static org.mockito.Mockito.*;

class CreditCardGuardrailTest {

    private CreditCardGuardrail guardrail;
    private ChatClientRequest request;
    private Prompt prompt;
    private CallAdvisorChain chain;

    @BeforeEach
    void setUp() {
        guardrail = new CreditCardGuardrail();
        request = mock(ChatClientRequest.class);
        prompt = mock(Prompt.class);
        chain = mock(CallAdvisorChain.class);
        ChatClientRequest.Builder builder = mock(ChatClientRequest.Builder.class);
        ChatClientResponse response = mock(ChatClientResponse.class);

        when(request.prompt()).thenReturn(prompt);
        when(request.mutate()).thenReturn(builder);
        when(builder.prompt(any())).thenReturn(builder);
        when(builder.build()).thenReturn(request);

        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(response);

        when(prompt.augmentUserMessage(anyString())).thenReturn(prompt);
    }

    @Test
    void testCreditCardRedactionWithHyphens() {
        when(prompt.getUserMessage()).thenReturn(new UserMessage("My card is 1234-5678-9012-3456"));
        guardrail.adviseCall(request, chain);
        verify(prompt).augmentUserMessage("My card is CC_AVAILABLE");
        verify(chain).nextCall(request);
    }

    @Test
    void testCreditCardRedactionWithSpaces() {
        when(prompt.getUserMessage()).thenReturn(new UserMessage("My card is 1234 5678 9012 3456"));
        guardrail.adviseCall(request, chain);
        verify(prompt).augmentUserMessage("My card is CC_AVAILABLE");
        verify(chain).nextCall(request);
    }

    @Test
    void testCreditCardRedactionWithoutSpaces() {
        when(prompt.getUserMessage()).thenReturn(new UserMessage("My card is 1234567890123456"));
        guardrail.adviseCall(request, chain);
        verify(prompt).augmentUserMessage("My card is CC_AVAILABLE");
        verify(chain).nextCall(request);
    }

    @Test
    void testCreditCardRedactionVisa13Digits() {
        when(prompt.getUserMessage()).thenReturn(new UserMessage("Visa: 4111111111111"));
        guardrail.adviseCall(request, chain);
        verify(prompt).augmentUserMessage("Visa: CC_AVAILABLE");
        verify(chain).nextCall(request);
    }

    @Test
    void testNoRedactionForUnrelatedNumbers() {
        when(prompt.getUserMessage()).thenReturn(new UserMessage("My phone is 123-456-7890"));
        guardrail.adviseCall(request, chain);
        verify(prompt, never()).augmentUserMessage(anyString());
        verify(chain).nextCall(request);
    }
}
