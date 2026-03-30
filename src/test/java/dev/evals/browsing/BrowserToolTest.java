package dev.evals.browsing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BrowserToolTest {

    private BrowserTool.ProcessExecutor processExecutor;
    private BrowserTool browserTool;
    private Process process;

    @BeforeEach
    void setUp() {
        processExecutor = mock(BrowserTool.ProcessExecutor.class);
        process = mock(Process.class);
        browserTool = new BrowserTool(false, 3, 10, 60, processExecutor);
    }

    @Test
    void testAgentBrowserSuccess() throws Exception {
        String command = "open https://example.com";
        String expectedOutput = "Page opened";
        
        when(processExecutor.execute(any(String[].class))).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(expectedOutput.getBytes()));
        when(process.waitFor(60, TimeUnit.SECONDS)).thenReturn(true);
        when(process.exitValue()).thenReturn(0);

        String result = browserTool.agentBrowser(command);

        assertEquals(expectedOutput, result);
        verify(processExecutor).execute(any(String[].class));
    }

    @Test
    void testAgentBrowserRetryOnDaemonBusy() throws Exception {
        String command = "snapshot";
        String busyOutput = "Resource temporarily unavailable";
        String successOutput = "Snapshot data";

        Process busyProcess = mock(Process.class);
        when(busyProcess.getInputStream()).thenReturn(new ByteArrayInputStream(busyOutput.getBytes()));
        when(busyProcess.waitFor(60, TimeUnit.SECONDS)).thenReturn(true);
        when(busyProcess.exitValue()).thenReturn(1);

        Process successProcess = mock(Process.class);
        when(successProcess.getInputStream()).thenReturn(new ByteArrayInputStream(successOutput.getBytes()));
        when(successProcess.waitFor(60, TimeUnit.SECONDS)).thenReturn(true);
        when(successProcess.exitValue()).thenReturn(0);

        // First attempt busy, second success
        when(processExecutor.execute(any(String[].class)))
                .thenReturn(busyProcess)
                .thenReturn(successProcess);

        // Use a smaller delay for testing if it were configurable, 
        // but here we just wait for the real delay or we'd need to mock Thread.sleep
        // For now, let's just test that it retries.
        
        String result = browserTool.agentBrowser(command);

        assertEquals(successOutput, result);
        verify(processExecutor, times(2)).execute(any(String[].class));
    }

    @Test
    void testAgentBrowserTimeout() throws Exception {
        String command = "wait";
        
        when(processExecutor.execute(any(String[].class))).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(60, TimeUnit.SECONDS)).thenReturn(false);

        String result = browserTool.agentBrowser(command);

        assertTrue(result.contains("Command timed out"), "Result should contain timeout message: " + result);
        verify(process).destroyForcibly();
    }

    @Test
    void testAgentBrowserEmptyErrorOutput() throws Exception {
        String command = "fail";

        when(processExecutor.execute(any(String[].class))).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(60, TimeUnit.SECONDS)).thenReturn(true);
        when(process.exitValue()).thenReturn(1);

        String result = browserTool.agentBrowser(command);

        assertEquals("Error: Command failed with exit code 1", result);
    }

    @Test
    void testAgentBrowserFailure() throws Exception {
        String command = "invalid";
        String errorOutput = "Command not found";

        when(processExecutor.execute(any(String[].class))).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(errorOutput.getBytes()));
        when(process.waitFor(60, TimeUnit.SECONDS)).thenReturn(true);
        when(process.exitValue()).thenReturn(127);

        String result = browserTool.agentBrowser(command);

        assertEquals(errorOutput, result);
    }
}
