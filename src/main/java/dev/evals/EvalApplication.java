package dev.evals;

import dev.evals.indexing.IndexingPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

@SpringBootApplication
public class EvalApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvalApplication.class);

    public static void main(String[] args) {
        var context = SpringApplication.run(EvalApplication.class, args);

        IndexingPipeline indexingPipeline = context.getBean(IndexingPipeline.class);
        try {
            indexingPipeline.runIndexing(true);
        } catch (IOException e) {
            LOGGER.error("Failed to index documents", e);
            throw new RuntimeException(e);
        }
    }
}
