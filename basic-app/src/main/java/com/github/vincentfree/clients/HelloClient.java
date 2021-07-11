package com.github.vincentfree.clients;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.TimerTask;

public class HelloClient {
    private final HttpClient client;

    public HelloClient(HttpClient client) {
        this.client = client;
    }

    public HttpRequest helloRequest() {
        return HttpRequest.newBuilder().GET().version(HttpClient.Version.HTTP_1_1)
                .uri(URI.create("http://localhost:3500/v1.0/invoke/order-backend/method/hello?failure=true"))
                .timeout(Duration.ofSeconds(5)).build();
    }


    public HttpResponse<String> callHello(HttpRequest request, HttpResponse.BodyHandler<String> handler)
            throws IOException, InterruptedException {
        return client.send(request, handler);
    }

    public TimerTask helloTimerTask(HttpRequest request, HttpResponse.BodyHandler<String> handler) {
        return new TimerTask() {
            public void run() {
                try {
                    System.out.println("calling order-backend");
                    var result = callHello(request, handler);
                    System.out.printf("The status is: %d and the result is : %s%n", result.statusCode(), result.body());
                } catch (IOException | InterruptedException err) {
                    System.out.println("Failed to call order-backend!");
                    throw new RuntimeException(err);
                }
            }
        };
    }
}
