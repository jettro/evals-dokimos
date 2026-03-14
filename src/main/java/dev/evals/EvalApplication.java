package dev.evals;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class EvalApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvalApplication.class, args);

        // Extract knowledge from the RAG based chat
//        ChatService bean = context.getBean(ChatService.class);
//        System.out.println("Extracting knowledge from the RAG based chat...");
//        RagResponse responseChatRag = bean.chatRag("What is a good peated whisky?");
//        System.out.println(responseChatRag.content());

        // Use the internal knowledge of the LLM
//        String responseChat = bean.chat("Where is whisky originated from?");
//        System.out.println(responseChat);
//        var result = bean.chatTools("Find information about Togouchi Beer Cask");
//        System.out.println(result);
    }
}
