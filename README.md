# Spring AI Evaluation Experiments (evals)

This project explores various capabilities of Spring AI, focusing on Retrieval-Augmented Generation (RAG), tool calling, guardrails, observability, and integration with the Dokimos evaluation framework. The primary use case centers around searching, retrieving, and ordering whiskies.

## 🚀 Features

- **RAG with Lucene**: Implements a custom `LuceneDatastore` to provide local search capabilities without a dedicated vector database.
- **Automated Indexing**: An `IndexingPipeline` processes whisky data from a CSV file (`whisky.csv`) and indexes it into Lucene.
- **Browser Automation Tool**: A Spring AI `@Tool` (`BrowserTool`) that wraps the `agent-browser` CLI, allowing the AI agent to navigate websites, click elements, and extract content dynamically.
- **Whisky Chat with Memory & Ordering**: A `WhiskyService` using conversation memory (`MessageChatMemoryAdvisor`) that allows searching, getting details, and placing orders across multiple turns.
- **Credit Card Guardrail**: A `CallAdvisor` (`CreditCardGuardrail`) that detects and redacts credit card numbers from user messages before they reach the LLM.
- **Guardrail Observability**: A `GuardrailObservationFilter` that records guardrail activations as high-cardinality trace attributes.
- **Multiple Chat Modes**:
  - `chat` — Basic conversational AI about whisky.
  - `chatWithRag` — Uses query rewriting and Lucene retrieval to answer questions based on indexed data.
  - `chatRAGTools` — Manual tool-calling loop with Lucene search + Jsoup page extraction, returning structured `WhiskySearchResult`.
  - `talkAboutWhisky` — Conversation-based whisky assistant with memory, guardrail, and ordering support.
- **AI Evaluation**: Integrated with **Dokimos** for systematic testing and evaluation of AI model responses.
- **Observability**: Full tracing via OpenTelemetry exported to Langfuse, with custom observation filters for prompts, completions, and guardrail events.
- **OpenAPI / Swagger UI**: API documentation available via Springdoc OpenAPI.

## 🛠 Tech Stack

- **Java 21**
- **Spring Boot 3.5.12**
- **Spring AI 1.1.4** (OpenAI integration, `gpt-5-mini`)
- **Apache Lucene 9.11.1** (Search Engine)
- **Jsoup 1.18.1** (HTML Parsing)
- **Dokimos 0.14.2** (AI Evaluation)
- **Jackson CSV** (Data Parsing)
- **OpenTelemetry + Micrometer** (Tracing & Observability)
- **Springdoc OpenAPI 2.8.16** (Swagger UI)

## 📋 Prerequisites

1. **Java 21** installed.
2. **Maven** installed.
3. **OpenAI API Key**: Set the `OPENAI_API_KEY` environment variable.
4. **agent-browser CLI** (optional): The `BrowserTool` requires the [`agent-browser`](https://github.com/agent-browser/agent-browser) CLI tool to be installed and available in your system's `PATH`.

## ⚙️ Configuration

Configuration is in `src/main/resources/application.yml`.

Key settings:

- `spring.ai.openai.api-key`: Your OpenAI API key (defaults to `${OPENAI_API_KEY}`).
- `spring.ai.openai.chat.options.model`: The OpenAI model to use (default: `gpt-5-mini`).
- `spring.ai.openai.chat.options.temperature`: LLM temperature (default: `1.0`).
- `agent-browser.headed`: Set to `true` to see the browser window during automation.

## 🚀 Getting Started

### 1. Build the project
```bash
mvn clean install
```

### 2. Run the application
```bash
mvn spring-boot:run
```

### 3. Explore the API
Swagger UI is available at: http://localhost:8080/swagger-ui.html

## 🔍 Project Components

### REST API

- `POST /chat` — Simple chat message, returns `{"content": "..."}`.
- `POST /chatrag` — Chat with RAG tools, returns a `WhiskySearchResult` with structured product info.
- `POST /whiskychat` — Conversational whisky assistant with memory, guardrail, and ordering. Accepts `message`, `conversationId`, and optional `ccNumber`.

See `test-chats.http` for example requests, or `examples.md` for conversation flow examples including ordering.

### RAG & Indexing
The project includes a `whisky.csv` file in `src/main/resources/data/`. The `IndexingPipeline` reads this CSV and uses `LuceneDatastore` to create a searchable index. The `ChatService` uses `RetrievalAugmentationAdvisor` with `RewriteQueryTransformer` and `ContextualQueryAugmenter` to enrich prompts with context from this index.

### Tools (Spring AI `@Tool`)

- `searchWhisky(query)` — Searches the local Lucene index for whiskies by keyword.
- `extractFromPage(url)` — Fetches a product page URL and extracts the description via Jsoup.
- `orderWhisky(name, toolContext)` — Places an order for a whisky, optionally using a credit card from the tool context.
- `agentBrowser(command)` — Executes `agent-browser` CLI commands for full browser automation.

### Credit Card Guardrail
The `CreditCardGuardrail` is a `CallAdvisor` that intercepts user messages, detects credit card numbers via regex, and replaces them with `CC_AVAILABLE` before passing the message to the LLM. Guardrail activations are recorded in traces via the `GuardrailObservationFilter`.

### Browser Automation (`BrowserTool`)
The `BrowserTool` allows the LLM to perform web-based tasks. It supports commands like:
- `open <url>`
- `click <selector>`
- `fill <selector> "text"`
- `snapshot -i` (captures interactive elements)
- `wait --load networkidle`

### Evaluations with Dokimos
The project uses Dokimos for evaluating AI response quality. Tests are in `src/test/java/dev/evals/`.

## 🧪 Running Tests
```bash
mvn test
```

Tests include:
- `ChatServiceTests` — Integration tests for chat modes (requires `OPENAI_API_KEY`).
- `BrowserToolTest` — Unit tests with mocked process execution.
- `ExtractFromPageToolTest` — Tests HTML extraction.
- `CreditCardGuardrailTest` — Tests credit card detection and redaction.
- `LuceneDatastoreTests` — Search indexing unit tests.
- `EvalApplicationTests` — Spring context load test.

## Observability

Traces are exported via OpenTelemetry to Langfuse. Custom observation filters capture:
- Full prompts and completions (`ChatModelCompletionContentObservationFilter`)
- Guardrail activations (`GuardrailObservationFilter`)

### Running Langfuse Locally

```bash
git clone https://github.com/langfuse/langfuse.git
cd langfuse

docker compose up
```

Open the URL: http://localhost:3000

### Connecting to Langfuse

Set the following environment variables:
- `OTEL_EXPORTER_OTLP_ENDPOINT` — e.g. `http://localhost:3000/api/public/otel` (local) or `https://cloud.langfuse.com/api/public/otel` (cloud).
- `OTEL_EXPORTER_OTLP_HEADERS` — `Authorization=Basic <base64(publicKey:secretKey)>`
- `OTEL_EXPORTER_OTLP_PROTOCOL` — `http/protobuf`

Traces for Spring AI chat calls are automatically sent when the application is running.

## 📝 License
This project is for experimental and evaluation purposes.
