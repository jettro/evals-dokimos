package dev.evals;

import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.ContextualRelevanceEvaluator;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import dev.dokimos.core.evaluators.FaithfulnessEvaluator;
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
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
    void checkChatTools() {
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

            var response = chatService.chatTools(query);

            return Map.of(
                    "output", response.alcoholPercentage()
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
            itemResult.evalResults().forEach(evalResult -> {

                System.out.println("Reason: " + evalResult.reason());
            });
        });

// Per evaluator metrics
        System.out.println("\n=== Average Scores by Evaluator ===");
        System.out.println("Answer Quality: " + String.format("%.2f", result.averageScore("Answer Quality")));
    }
}
