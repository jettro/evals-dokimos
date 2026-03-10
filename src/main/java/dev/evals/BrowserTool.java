package dev.evals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Component
public class BrowserTool {

    private static final Logger log = LoggerFactory.getLogger(BrowserTool.class);

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final boolean headed;

    public BrowserTool(@Value("${agent-browser.headed:false}") boolean headed) {
        this.headed = headed;
        log.info("BrowserTool initialized (headed={})", headed);
    }

    @Tool(description = "Execute an agent-browser command for browser automation. " +
            "Pass the full command string, e.g. 'open https://example.com', 'snapshot -i', " +
            "'fill @e1 \"search text\"', 'click @e2', 'press Enter', 'get text body', 'close'.")
    public String agentBrowser(@ToolParam(description = "The agent-browser arguments, e.g. 'open https://example.com'") String command) {
        log.info(">>> agent-browser {}", command);
        long start = System.currentTimeMillis();

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String result = executeCommand(command);

                if (result != null && result.contains("Resource temporarily unavailable") && attempt < MAX_RETRIES) {
                    log.warn("Daemon busy (attempt {}/{}), retrying in {}ms...", attempt, MAX_RETRIES, RETRY_DELAY_MS);
                    Thread.sleep(RETRY_DELAY_MS);
                    continue;
                }

                long elapsed = System.currentTimeMillis() - start;
                log.info("<<< agent-browser {} — OK ({}ms), output length: {} chars", command, elapsed,
                        result != null ? result.length() : 0);
                log.debug("<<< agent-browser full output:\n{}", result);

                return (result == null || result.isEmpty()) ? "Command completed successfully (no output)" : result;
            } catch (TimeoutException e) {
                long elapsed = System.currentTimeMillis() - start;
                log.warn("<<< TIMEOUT after {}ms: agent-browser {}", elapsed, command);
                return "Command timed out after 60 seconds. The page may be loading slowly. " +
                        "Try running 'wait --load networkidle' followed by 'snapshot -i' to check the current state.";
            } catch (Exception e) {
                log.error("<<< agent-browser {} — exception: {}", command, e.getMessage(), e);
                return "Error executing agent-browser command: " + e.getMessage();
            }
        }

        return "Error: daemon remained busy after " + MAX_RETRIES + " retries. Try again in a few seconds.";
    }

    private String executeCommand(String command) throws Exception {
        String headedFlag = headed ? "--headed " : "";
        ProcessBuilder pb = new ProcessBuilder("sh", "-lc", "agent-browser " + headedFlag + command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException("Command timed out");
        }

        int exitCode = process.exitValue();
        String result = output.toString().trim();

        if (exitCode != 0) {
            log.warn("agent-browser {} — exit code {}: {}", command, exitCode, result);
        }

        return result;
    }

    private static class TimeoutException extends Exception {
        TimeoutException(String message) {
            super(message);
        }
    }
}
