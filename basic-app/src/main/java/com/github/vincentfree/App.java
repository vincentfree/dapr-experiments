package com.github.vincentfree;

import com.github.vincentfree.clients.HelloClient;

import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Timer;

public class App {

    public static void main(String[] args) {
        System.out.println("Starting application...");
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var helloClient = new HelloClient(client);
        var request = helloClient.helloRequest();
        var handler = BodyHandlers.ofString();
        var timer = new Timer();
        timer.schedule(helloClient.helloTimerTask(request, handler), 100, 5000);
    }
}
