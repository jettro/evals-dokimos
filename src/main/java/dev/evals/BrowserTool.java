package dev.evals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * A Spring-managed tool component that provides browser automation capabilities by wrapping the {@code agent-browser} command-line tool.
 * <p>
 * This class allows Spring AI agents to interact with a web browser through a standardized command interface,
 * including support for navigation, filling forms, clicking elements, and capturing snapshots.
 * It handles automatic retries when the underlying browser daemon is busy and provides timeout management.
 * </p>
 */
@Component
public class BrowserTool {

    private static final Logger log = LoggerFactory.getLogger(BrowserTool.class);

    /** Default maximum number of retry attempts when the browser daemon is busy. */
    private static final int DEFAULT_MAX_RETRIES = 3;
    
    /** Default delay between retry attempts in milliseconds. */
    private static final long DEFAULT_RETRY_DELAY_MS = 2000;
    
    /** Default timeout for command execution in seconds. */
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    /** Whether to run the browser in headed mode (visible window). */
    private final boolean headed;
    
    /** Maximum number of retry attempts when the browser daemon is busy. */
    private final int maxRetries;
    
    /** Delay between retry attempts in milliseconds. */
    private final long retryDelayMs;
    
    /** Timeout for command execution in seconds. */
    private final long timeoutSeconds;
    
    /** Executor for executing external processes. */
    private final ProcessExecutor processExecutor;

    /**
     * Constructs a new {@code BrowserTool} with values injected from Spring properties.
     *
     * @param headed whether to run the browser in headed mode (from {@code agent-browser.headed})
     * @param maxRetries maximum number of retries (from {@code agent-browser.max-retries})
     * @param retryDelayMs delay between retries (from {@code agent-browser.retry-delay-ms})
     * @param timeoutSeconds command timeout in seconds (from {@code agent-browser.timeout-seconds})
     */
    @Autowired
    public BrowserTool(
            @Value("${agent-browser.headed:false}") boolean headed,
            @Value("${agent-browser.max-retries:" + DEFAULT_MAX_RETRIES + "}") int maxRetries,
            @Value("${agent-browser.retry-delay-ms:" + DEFAULT_RETRY_DELAY_MS + "}") long retryDelayMs,
            @Value("${agent-browser.timeout-seconds:" + DEFAULT_TIMEOUT_SECONDS + "}") long timeoutSeconds) {
        this(headed, maxRetries, retryDelayMs, timeoutSeconds, null);
    }

    /**
     * Constructs a new {@code BrowserTool} with explicit parameters.
     * Primary use is for testing and manual instantiation.
     *
     * @param headed whether to run the browser in headed mode
     * @param maxRetries maximum number of retries
     * @param retryDelayMs delay between retries
     * @param timeoutSeconds command timeout in seconds
     * @param processExecutor the executor to use for process management (uses {@link DefaultProcessExecutor} if null)
     */
    BrowserTool(boolean headed, int maxRetries, long retryDelayMs, long timeoutSeconds, ProcessExecutor processExecutor) {
        this.headed = headed;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.timeoutSeconds = timeoutSeconds;
        this.processExecutor = (processExecutor != null) ? processExecutor : new DefaultProcessExecutor();
        log.info("BrowserTool initialized (headed={}, maxRetries={}, timeoutSeconds={}s)",
                headed, maxRetries, timeoutSeconds);
    }

    /**
     * Interface for executing external processes.
     * Allows for mocking in unit tests.
     */
    interface ProcessExecutor {
        /**
         * Executes the specified command as a separate process.
         *
         * @param command the command and its arguments
         * @return the started {@link Process}
         * @throws Exception if an error occurs while starting the process
         */
        Process execute(String... command) throws Exception;
    }

    /**
     * Default implementation of {@link ProcessExecutor} that uses {@link ProcessBuilder}.
     */
    static class DefaultProcessExecutor implements ProcessExecutor {
        @Override
        public Process execute(String... command) throws Exception {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            return pb.start();
        }
    }

    /**
     * Executes an {@code agent-browser} command for browser automation.
     * This method is exposed as a Spring AI tool.
     * <p>
     * Supported commands include:
     * <ul>
     *     <li>{@code open <url>} - Opens the specified URL</li>
     *     <li>{@code snapshot [-i]} - Captures the current page state</li>
     *     <li>{@code fill <selector> "<text>"} - Fills an input field</li>
     *     <li>{@code click <selector>} - Clicks an element</li>
     *     <li>{@code press <key>} - Presses a keyboard key</li>
     *     <li>{@code get text body} - Retrieves text content</li>
     *     <li>{@code close} - Closes the browser</li>
     * </ul>
     * </p>
     *
     * @param command the {@code agent-browser} arguments, e.g. 'open https://example.com'
     * @return the command output, or an error message if the command failed or timed out
     */
    @Tool(description = "Execute an agent-browser command for browser automation. " +
            "Pass the full command string, e.g. 'open https://example.com', 'snapshot -i', " +
            "'fill @e1 \"search text\"', 'click @e2', 'press Enter', 'get text body', 'close'.")
    public String agentBrowser(@ToolParam(description = "The agent-browser arguments, e.g. 'open https://example.com'") String command) {
        log.info(">>> agent-browser {}", command);
        long start = System.currentTimeMillis();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String result = executeCommand(command);

                if (result.contains("Resource temporarily unavailable") && attempt < maxRetries) {
                    log.warn("Daemon busy (attempt {}/{}), retrying in {}ms...", attempt, maxRetries, retryDelayMs);
                    Thread.sleep(retryDelayMs);
                    continue;
                }

                long elapsed = System.currentTimeMillis() - start;
                log.info("<<< agent-browser {} — OK ({}ms), output length: {} chars", command, elapsed,
                        result.length());
                log.debug("<<< agent-browser full output:\n{}", result);

                return result.isEmpty() ? "Command completed successfully (no output)" : result;
            } catch (TimeoutException e) {
                long elapsed = System.currentTimeMillis() - start;
                log.warn("<<< TIMEOUT after {}ms: agent-browser {}", elapsed, command);
                return "Command timed out after " + timeoutSeconds + " seconds. The page may be loading slowly. " +
                        "Try running 'wait --load networkidle' followed by 'snapshot -i' to check the current state.";
            } catch (Exception e) {
                log.error("<<< agent-browser {} — exception: {}", command, e.getMessage(), e);
                return "Error executing agent-browser command: " + e.getMessage();
            }
        }

        return "Error: daemon remained busy after " + maxRetries + " retries. Try again in a few seconds.";
    }

    /**
     * Executes the browser command through a shell process.
     *
     * @param command the {@code agent-browser} sub-command to run
     * @return the trimmed output of the command
     * @throws TimeoutException if the command takes longer than {@code timeoutSeconds}
     * @throws Exception if an I/O or other error occurs
     */
    private String executeCommand(String command) throws Exception {
        String headedFlag = headed ? "--headed " : "";
        // We use a list to avoid manual string concatenation where possible, 
        // although sh -lc still requires a single string for the command.
        String fullCommand = "agent-browser " + headedFlag + command;
        Process process = processExecutor.execute("sh", "-lc", fullCommand);

        StringBuilder output = new StringBuilder();
        // Use a thread to read the output to avoid blocking if the buffer fills up,
        // although for small outputs BufferedReader is usually fine.
        // Also ensure we handle both stdout and stderr (already done via redirectErrorStream).
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException("Command timed out after " + timeoutSeconds + " seconds");
        }

        int exitCode = process.exitValue();
        String result = output.toString().trim();

        if (exitCode != 0) {
            log.warn("agent-browser {} — exit code {}: {}", command, exitCode, result);
            if (result.isEmpty()) {
                return "Error: Command failed with exit code " + exitCode;
            }
        }

        return result;
    }

    /**
     * Custom exception thrown when a browser command execution exceeds the configured timeout.
     */
    private static class TimeoutException extends Exception {
        /**
         * Constructs a new {@code TimeoutException} with the specified message.
         *
         * @param message the detail message
         */
        TimeoutException(String message) {
            super(message);
        }
    }
}
