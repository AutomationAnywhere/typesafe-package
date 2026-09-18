package com.automationanywhere.botcommand.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

/**
 * Thin client for the TypeSafe AI "systemone" endpoint. Every action sends a single
 * question and reads back its single answer, so this class only needs to know how to
 * wrap one question and unwrap one answer.
 */
public class TypeSafeClient {

    private static final Logger logger = LogManager.getLogger(TypeSafeClient.class);
    private static final String ENDPOINT = "https://api.typesafe.ai/v1/systemone";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String QUESTION_KEY = "question";

    private TypeSafeClient() {
    }

    /**
     * Sends a single question and returns its answer object plus the response's
     * top-level "usage" object, keyed "answer" and "usage" respectively.
     */
    public static JSONObject evaluate(String apiKey, String text, String model, JSONObject question) {
        JSONObject body = new JSONObject()
                .put("state", text)
                .put("model", model)
                .put("questions", new JSONObject().put(QUESTION_KEY, question));

        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        long startNanos = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TypeSafeClientException("Unable to reach TypeSafe API: " + e.getMessage(), e);
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        if (response.statusCode() != 200) {
            throw new TypeSafeClientException(
                    "TypeSafe API returned HTTP " + response.statusCode() + ": " + response.body());
        }

        logger.debug("TypeSafe API response ({} ms): {}", elapsedMs, response.body());

        JSONObject responseJson = new JSONObject(response.body());
        JSONObject answer = responseJson.getJSONObject("answers").getJSONObject(QUESTION_KEY);
        JSONObject result = new JSONObject();
        result.put("answer", answer);
        result.put("usage", responseJson.optJSONObject("usage", new JSONObject()));
        result.put("elapsedMs", elapsedMs);
        return result;
    }

    public static class TypeSafeClientException extends RuntimeException {
        public TypeSafeClientException(String message) {
            super(message);
        }

        public TypeSafeClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
