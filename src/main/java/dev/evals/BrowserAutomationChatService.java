package dev.evals;

import dev.evals.logging.MyLoggingAdvisor;
import dev.evals.model.WhiskySearchResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;

public class BrowserAutomationChatService {

    private final ChatClient chatClient;
    private final BrowserTool browserTool;

    public BrowserAutomationChatService(ChatClient.Builder chatClientBuilder, BrowserTool browserTool) {
        this.chatClient = chatClientBuilder.build();
        this.browserTool = browserTool;
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
