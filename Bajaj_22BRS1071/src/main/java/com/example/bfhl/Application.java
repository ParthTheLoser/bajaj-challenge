package com.example.bfhl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class Application {

    @Value("${bfhl.base-url}")
    private String baseUrl;

    @Value("${bfhl.name}")
    private String name;

    @Value("${bfhl.email}")
    private String email;

    @Value("${bfhl.reg-no}")
    private String regNo;

    @Value("${bfhl.final-query}")
    private String finalQuery;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    ApplicationRunner run(WebClient client, ObjectMapper mapper) {
        return args -> {
            String generateUrl = baseUrl + "/hiring/generateWebhook/JAVA";

            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("regNo", regNo);
            payload.put("email", email);

            System.out.println("[BFHL] Generating webhook...");

            JsonNode genResp = client.post()
                    .uri(generateUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(payload))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .onErrorResume(e -> {
                        e.printStackTrace();
                        return Mono.empty();
                    })
                    .block();

            if (genResp == null) {
                throw new IllegalStateException("Failed to generate webhook — response was null");
            }

            String accessToken = text(genResp, "accessToken", "token", "jwt", "access_token");
            String webhookUrl = text(genResp, "webhook", "webhookUrl", "url");

            if (accessToken == null || webhookUrl == null) {
                throw new IllegalStateException("Missing accessToken or webhook URL in response: " + genResp);
            }

            System.out.println("[BFHL] Got webhook: " + webhookUrl);

            Map<String, Object> answer = new HashMap<>();
            answer.put("finalQuery", finalQuery);

            System.out.println("[BFHL] Submitting finalQuery...\n" + finalQuery);

            String submitResp = client.post()
                    .uri(webhookUrl)
                    .header(HttpHeaders.AUTHORIZATION, accessToken) 
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(answer))
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> Mono.just("Submission failed: " + e.getMessage()))
                    .block();

            System.out.println("[BFHL] Submission response: " + submitResp);
        };
    }

    private static String text(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull()) return v.asText();
        }

        JsonNode data = node.get("data");
        if (data != null) {
            for (String k : keys) {
                JsonNode v = data.get(k);
                if (v != null && !v.isNull()) return v.asText();
            }
        }
        return null;
    }
}

