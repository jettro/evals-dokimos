package dev.evals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.*;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.ContextualRelevanceEvaluator;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import dev.dokimos.core.evaluators.FaithfulnessEvaluator;
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
import dev.dokimos.core.evaluators.agents.TaskCompletionEvaluator;
import dev.dokimos.core.evaluators.agents.ToolArgumentHallucinationEvaluator;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator;
import dev.dokimos.springai.SpringAiSupport;
import dev.evals.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class EvalApplicationTests {

    @Autowired
    ChatService chatService;

    ChatModel chatModel;

    @BeforeEach
    void setUp() {
        OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
                .model(OpenAiApi.ChatModel.GPT_5_MINI)
                .build();

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        this.chatModel = OpenAiChatModel.builder()
                .defaultOptions(openAiChatOptions)
                .openAiApi(openAiApi)
                .build();

    }

    @Test
    void checkChat() {
        Dataset dataset = Dataset.builder()
                .name("test-dataset")
                .description("Test dataset for evaluation")
                .addExample(Example.of(
                        "Where is whisky originated from?",
                        "Whisky is originated from Scotland."
                ))
                .build();

        // Create evaluation task
        Task task = example -> {
            String query = example.input();

            String response = chatService.chat(query);

            return Map.of(
                    "output", response
            );
        };

        JudgeLM judge = SpringAiSupport.asJudge(chatModel);

        List<Evaluator> evaluators = List.of(
                LLMJudgeEvaluator.builder()
                        .name("Answer Quality")
                        .criteria("Is the answer helpful, clear, and accurate?")
                        .evaluationParams(List.of(
                                EvalTestCaseParam.INPUT,
                                EvalTestCaseParam.EXPECTED_OUTPUT,
                                EvalTestCaseParam.ACTUAL_OUTPUT
                        ))
                        .judge(judge)
                        .threshold(0.8)
                        .build()
        );

        ExperimentResult result = Experiment.builder()
                .name("Agent Evaluation")
                .dataset(dataset)
                .task(task)
                .evaluators(evaluators)
                .build()
                .run();

        printExperimentResult(result);

        // Assert each evaluator's average meets 0.8
        assertAll(
                () -> assertTrue(result.averageScore("Answer Quality") >= 0.8,
                        "Answer Quality: " + result.averageScore("Answer Quality"))
        );
    }

    @Test
    void checkChatRag() {
        Dataset dataset = Dataset.builder()
                .name("test-dataset")
                .description("Test dataset for evaluation")
                .addExample(Example.of(
                        "What is a good peated whisky?",
                        "Examples of good peated whisky are Benriach Peated Quarter Cask Whisky and Paul John Peated " +
                                "Malt"
                ))
                .build();

        Task task = example -> {
            String query = example.input();

            var response = chatService.chatRag(query);

            return Map.of(
                    "output", response.content(),
                    "retrievedContext", String.join(", ", response.foundDocuments()),
                    "retrievalContext", response.foundDocuments()
            );
        };

        JudgeLM judge = SpringAiSupport.asJudge(chatModel);

        List<Evaluator> evaluators = List.of(
                LLMJudgeEvaluator.builder()
                        .name("Answer Quality")
                        .criteria("Is the answer helpful, clear, and accurate?")
                        .evaluationParams(List.of(
                                EvalTestCaseParam.INPUT,
                                EvalTestCaseParam.EXPECTED_OUTPUT,
                                EvalTestCaseParam.ACTUAL_OUTPUT
                        ))
                        .judge(judge)
                        .threshold(0.8)
                        .build(),
                FaithfulnessEvaluator.builder()
                        .name("Context Faithfulness")
                        .threshold(0.8)
                        .judge(judge)
                        .contextKey("retrievedContext")  // Where to find the context in outputs
                        .includeReason(true)
                        .build(),
                ContextualRelevanceEvaluator.builder()
                        .name("Context Relevance")
                        .threshold(0.5)
                        .judge(judge)
                        .retrievalContextKey("retrievalContext")
                        .includeReason(true)
                        .strictMode(false)  // Set to true for threshold of 1.0
                        .build()
        );

        ExperimentResult result = Experiment.builder()
                .name("Agent Evaluation")
                .dataset(dataset)
                .task(task)
                .evaluators(evaluators)
                .build()
                .run();

        printExperimentResult(result);

        // Assert each evaluator's average meets 0.8
        assertAll(
                () -> assertTrue(result.averageScore("Answer Quality") >= 0.8,
                        "Answer Quality: " + result.averageScore("Answer Quality")),
                () -> assertTrue(result.averageScore("Context Faithfulness") >= 0.8,
                        "Context Faithfulness: " + result.averageScore("Context Faithfulness")),
                () -> assertTrue(result.averageScore("Context Relevance") >= 0.8,
                        "Context Relevance: " + result.averageScore("Context Relevance"))
                );
    }

    @Test
    void checkChatRagTools() {
        Dataset dataset = Dataset.builder()
                .name("test-dataset")
                .description("Test dataset for evaluation")
                .addExample(Example.of(
                        "Find alcohol percentage of the Togouchi Peated Cask",
                        "40%"
                ))
                .build();

        Task task = example -> {
            String query = example.input();

            var response = chatService.chatRAGTools(query);

            return Map.of(
                    "output", response.result().alcoholPercentage()
            );
        };

        List<Evaluator> evaluators = List.of(
                ExactMatchEvaluator.builder()
                        .name("Exact Match Alcohol Percentage")
                        .threshold(1.0)
                        .build()
        );

        ExperimentResult result = Experiment.builder()
                .name("Agent Evaluation")
                .dataset(dataset)
                .task(task)
                .evaluators(evaluators)
                .build()
                .run();

        printExperimentResult(result);

        assertAll(
                () -> assertTrue(result.averageScore("Exact Match Alcohol Percentage") >= 1,
                        "Exact Match Alcohol Percentage: " + result.averageScore("Exact Match Alcohol Percentage"))
        );

    }

    @Test
    void checkValidityToolCalls() {

        List<ToolDefinition> tools = List.of(
                ToolDefinition.of("searchWhisky", "Search whisky names", Map.of("query", "string")),
                ToolDefinition.of("extractFromPage", "Extract information from a product page", Map.of("url", "string"))
        );

        JudgeLM judge = SpringAiSupport.asJudge(chatModel);

        var response = chatService.chatRAGTools("Find information about a beer cask whisky");
        
        List<ToolCall> arguments = response.toolCalls().stream().map(tc -> {
            return ToolCall.of(tc.name(), parseArguments(tc.arguments()));
        }).toList();

        AgentTrace trace = AgentTrace.builder()
                .toolCalls(arguments)
                .addToolCall(ToolCall.of("searchWhisky", Map.of("query", "beer cask whisky")))
                .addToolCall(ToolCall.of("extractFromPage", Map.of("url", "test")))
                .finalResponse(response.result().toString())
                .build();

        var testCase = EvalTestCase.builder()
                .input("Find information about a beer cask whisky")
                .actualOutput("toolCalls", trace.toolCalls())
                .actualOutput("output", trace.finalResponse())
                .expectedOutput("toolCalls", List.of(
                        ToolCall.of("searchWhisky", Map.of()),
                        ToolCall.of("extractFromPage", Map.of())
                ))
                .metadata("tools", tools)
                .metadata("tasks", List.of("Search whisky names", "Extract information from a product page"))
                .build();

        var results = List.of(
                ToolCallValidityEvaluator.builder().build().evaluate(testCase),
                ToolCorrectnessEvaluator.builder().build().evaluate(testCase),
                TaskCompletionEvaluator.builder().judge(judge).build().evaluate(testCase),
                ToolArgumentHallucinationEvaluator.builder().judge(judge).build().evaluate(testCase)
        );

        printEvalResults(results);
    }

    private static void printEvalResults(List<EvalResult> results) {
        System.out.println("\n--- Evaluation Results ---");
        results.forEach(result -> {
            String status = result.success() ? "PASSED ✅" : "FAILED ❌";
            double threshold = result.threshold() != null ? result.threshold() : 0.0;
            System.out.printf("Evaluator: %-30s | Status: %s | Score: %.2f (Threshold: %.2f)%n",
                    result.name(), status, result.score(), threshold);
            System.out.println("Reason: " + result.reason());
            if (result.metadata() != null && !result.metadata().isEmpty()) {
                System.out.println("Metadata: " + result.metadata());
            }
            System.out.println("-------------------------");
        });
    }


    private static void printExperimentResult(ExperimentResult result) {
        // Overall metrics
        System.out.println("=== Experiment Results ===");
        System.out.println("Name: " + result.name());
        System.out.println("Total examples: " + result.totalCount());
        System.out.println("Passed: " + result.passCount());
        System.out.println("Failed: " + result.failCount());
        System.out.println("Pass rate: " + String.format("%.1f%%", result.passRate() * 100));

        result.itemResults().forEach(itemResult -> {
            System.out.println("Example input: " + itemResult.example().input());
            System.out.println("Expected output: " + itemResult.example().expectedOutput());
            System.out.println("Actual output: " + itemResult.actualOutputs().get("output"));
            printEvalResults(itemResult.evalResults());
        });

// Per evaluator metrics
        System.out.println("\n=== Average Scores by Evaluator ===");
        System.out.println("Answer Quality: " + String.format("%.2f", result.averageScore("Answer Quality")));
    }


    private Map<String, Object> parseArguments(String arguments) {
        try {
            return new ObjectMapper().readValue(arguments, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
