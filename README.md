# Spring AI Evaluation Experiments (evals)

This project explores various capabilities of Spring AI, focusing on Retrieval-Augmented Generation (RAG), tool calling with browser automation, and integration with the Dokimos evaluation framework. The primary use case centers around searching and retrieving information about whiskies.

## 🚀 Features

- **RAG with Lucene**: Implements a custom `LuceneDatastore` to provide local vector-like search capabilities without a dedicated vector database.
- **Automated Indexing**: Includes an `IndexingPipeline` that processes whisky data from a CSV file (`whisky.csv`) and indexes it into Lucene.
- **Browser Automation Tool**: A Spring AI `@Tool` (`BrowserTool`) that wraps the `agent-browser` CLI, allowing the AI agent to navigate websites, click elements, and extract content dynamically.
- **Multi-modal Chat Service**:
  - `chat`: Basic conversational AI.
  - `chatRag`: Uses query rewriting and Lucene retrieval to answer whisky-related questions based on indexed data.
  - `chatTools`: Uses the `BrowserTool` to find real-time product information from external websites.
- **AI Evaluation**: Integrated with **Dokimos** for systematic testing and evaluation of AI model responses.

## 🛠 Tech Stack

- **Java 21**
- **Spring Boot 3.5.x**
- **Spring AI 1.1.x** (OpenAI integration)
- **Apache Lucene 9.11** (Search Engine)
- **Dokimos 0.14.x** (AI Evaluation)
- **Jackson CSV** (Data Parsing)

## 📋 Prerequisites

1. **Java 21** installed.
2. **Maven** installed.
3. **OpenAI API Key**: Set the `OPENAI_API_KEY` environment variable.
4. **agent-browser CLI**: The `BrowserTool` requires the [`agent-browser`](https://github.com/agent-browser/agent-browser) CLI tool to be installed and available in your system's `PATH`.

## ⚙️ Configuration

Key properties in `src/main/resources/application.properties`:

- `spring.ai.openai.api-key`: Your OpenAI API key (defaults to `${OPENAI_API_KEY}`).
- `agent-browser.headed`: Set to `true` to see the browser window during automation (default: `false`).
- `agent-browser.max-retries`: Maximum number of retries if the browser daemon is busy (default: `3`).
- `agent-browser.retry-delay-ms`: Delay between retries in milliseconds (default: `2000`).
- `agent-browser.timeout-seconds`: Command execution timeout in seconds (default: `60`).
- `lucene.index.path`: The directory where the Lucene index is stored (default: `lucene-index`).

## 🚀 Getting Started

### 1. Build the project
```bash
mvn clean install
```

### 2. Run the application
```bash
mvn spring-boot:run
```

## 🔍 Project Components

### RAG & Indexing
The project includes a `whisky.csv` file in `src/main/resources/data/`. On startup (or via manual trigger), the `IndexingPipeline` reads this CSV and uses `LuceneDatastore` to create a searchable index. The `ChatService` uses `RetrievalAugmentationAdvisor` to enrich prompts with context from this index.

### Browser Automation (`BrowserTool`)
The `BrowserTool` allows the LLM to perform web-based tasks. It supports commands like:
- `open <url>`
- `click <selector>`
- `fill <selector> "text"`
- `snapshot -i` (captures interactive elements)
- `wait --load networkidle`

### Evaluations with Dokimos
The project is set up to use Dokimos for evaluating the quality of AI responses. Test cases can be found in `src/test/java/dev/evals/`.

## 🧪 Running Tests
```bash
mvn test
```

## 📝 License
This project is for experimental and evaluation purposes.
