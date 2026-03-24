# CLAUDE.md

## Project Overview

Spring AI evaluation experiments project exploring RAG, tool calling, and LLM evaluation using the Dokimos framework. The domain is whisky search and retrieval via multiple AI-powered chat modes.

## Build & Run

**Prerequisites**: Java 21, Maven, `OPENAI_API_KEY` env var, `agent-browser` CLI in PATH

```bash
# Build
mvn clean install

# Run
mvn spring-boot:run

# Test
mvn test
```

## Key Environment Variables

| Variable | Required | Description |
|---|---|---|
| `OPENAI_API_KEY` | Yes | OpenAI API key |
| `LANGFUSE_PUBLIC_KEY` | No | Langfuse observability |
| `LANGFUSE_SECRET_KEY` | No | Langfuse observability |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | No | OTLP trace exporter endpoint |
| `OTEL_EXPORTER_OTLP_HEADERS` | No | OTLP auth headers |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | No | Set to `http/protobuf` for Langfuse |

## Architecture

### Chat Modes (ChatService)

1. `chat(message)` — Basic LLM chat using internal knowledge
2. `chatWithRag(message)` — RAG via Apache Lucene index over `whisky.csv`
3. `chatRAGTools(message)` — Tool calling with Lucene search + HTML extraction

`BrowserAutomationChatService` — Separate service for web browser automation tasks.

### Tools (Spring AI `@Tool`)

- `ExtractFromPageTool.searchWhisky()` — Searches local Lucene index
- `ExtractFromPageTool.extractFromPage()` — Extracts HTML content via Jsoup
- `BrowserTool.agentBrowser()` — Executes `agent-browser` CLI commands

### RAG Pipeline

`IndexingPipeline` loads `whisky.csv` → `LuceneDatastore` (implements `DocumentWriter`) → `RewriteQueryTransformer` → `RetrievalAugmentationAdvisor` → `ContextualQueryAugmenter`

### Observability

- `ToolCallObservationAspect` — AOP wraps `@Tool` methods with OpenTelemetry spans
- `ChatModelCompletionContentObservationFilter` — Captures prompts/completions
- `MyLoggingAdvisor` — Logs chat requests and responses
- Traces exported to Langfuse via OTLP

## REST API

| Method | Endpoint | Request | Response |
|---|---|---|---|
| POST | `/chat` | `{"message": "..."}` | `{"content": "..."}` |
| POST | `/chatrag` | `{"message": "..."}` | `WhiskySearchResult` |

## Model (Java Records)

`WhiskySearchResult`, `SearchResponse`, `SearchRequest`, `RagResponse`, `WhiskyChatResponse`, `ToolCallInfo`, `ChatRequest`, `ChatResponse` — all immutable records used as DTOs.

## Testing

- `ChatServiceTests` — Integration tests for all chat modes (requires `OPENAI_API_KEY`)
- `BrowserToolTest` — Unit tests with mocked `ProcessExecutor`
- `ExtractFromPageToolTest` — Tests HTML extraction with live URL calls
- `LuceneDatastoreTests` — Search indexing unit tests
- `EvalApplicationTests` — Spring context load test

Tests use [Dokimos](https://github.com/dokimos) for LLM output evaluation with JUnit integration.

## Tech Stack

- Java 21, Spring Boot 3.5, Spring AI 1.1.2
- OpenAI (`gpt-5-mini` model, temperature 1.0)
- Apache Lucene 9.11.1 (local search, no vector DB)
- Jsoup 1.18.1 (HTML parsing)
- Jackson CSV (whisky data loading)
- OpenTelemetry + Micrometer + Langfuse (observability)
- Dokimos 0.14.1 (LLM evaluation)
