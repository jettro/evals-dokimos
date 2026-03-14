package dev.evals;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that automatically creates OpenTelemetry spans for all Spring AI
 * {@link org.springframework.ai.tool.annotation.Tool @Tool} annotated methods,
 * capturing input parameters and return values for Langfuse observability.
 */
@Aspect
@Component
public class ToolCallObservationAspect {

    private static final int MAX_ATTRIBUTE_LENGTH = 4096;
    private final Tracer tracer;

    public ToolCallObservationAspect(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("tool-observations");
    }

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object observeToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = joinPoint.getSignature().getName();
        Span span = tracer.spanBuilder("tool:" + toolName).startSpan();

        try (Scope scope = span.makeCurrent()) {
            recordInputParameters(span, joinPoint);

            Object result = joinPoint.proceed();

            if (result != null) {
                span.setAttribute("tool.response", truncate(String.valueOf(result)));
            }
            return result;
        } catch (Throwable t) {
            span.setStatus(StatusCode.ERROR, t.getMessage());
            span.recordException(t);
            throw t;
        } finally {
            span.end();
        }
    }

    private void recordInputParameters(Span span, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            if (args[i] != null) {
                span.setAttribute("tool.param." + paramNames[i],
                        truncate(String.valueOf(args[i])));
            }
        }
    }

    private String truncate(String value) {
        return value.length() > MAX_ATTRIBUTE_LENGTH
                ? value.substring(0, MAX_ATTRIBUTE_LENGTH) + "...[truncated]"
                : value;
    }
}
