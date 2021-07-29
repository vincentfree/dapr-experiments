package com.github.vincentfree.clients;

import com.github.vincentfree.model.Order;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class OrderClient {
    private final HttpClient client;
    private String[] words;

    public OrderClient(HttpClient client) {
        this.client = client;
        String wordAPIUrl = "https://random-word-api.herokuapp.com/word?number=150";
        var request = HttpRequest.newBuilder(URI.create(wordAPIUrl)).GET()
                .timeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            words = response.body()
                    .replace("[", "")
                    .replace("]", "")
                    .split(",");
        } catch (IOException | InterruptedException err) {
            System.err.printf("failed to get array of words..%n err: %s", err.getMessage());
            words = new String[]{
                    "uppercutting",
                    "appreciation",
                    "cyclothymias",
                    "mismanagements",
                    "parthenogenetic",
                    "pottle",
                    "maras"
            };
        }
    }

    private HttpRequest generateOrderRequest() {
        var word = words[ThreadLocalRandom.current().nextInt(words.length)];
        var order = new Order(word, word, UUID.randomUUID().toString());
        var payload = wrapPayload(order.toJsonString());
        return HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .uri(URI.create("http://localhost:3500/v1.0/publish/kafka/order.events"))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    private HttpResponse<String> sendEvent(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public TimerTask orderTimerTask() {
        return new TimerTask() {
            public void run() {
                try {
                    System.out.println("Sending event to kafka..");
                    var result = sendEvent(generateOrderRequest());
                    System.out.printf("The status is: %d%n", result.statusCode());
                } catch (IOException | InterruptedException err) {
                    System.err.println("Failed to send event to kafka!");
                    throw new RuntimeException(err);
                }
            }
        };
    }

    protected String wrapPayload(String payload) {
        return "{ \"data\": {" + payload + "} }";
    }
}
