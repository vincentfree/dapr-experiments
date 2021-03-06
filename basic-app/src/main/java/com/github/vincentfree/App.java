package com.github.vincentfree;

import com.github.vincentfree.clients.HelloClient;
import com.github.vincentfree.clients.OrderClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Timer;

public class App {

    public static void main(String[] args) {
        System.out.println("Starting application...");
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var helloClient = new HelloClient(client);
        var orderClient = new OrderClient(client);

        var timer = new Timer();
        timer.schedule(helloClient.helloTimerTask(), 3000, 5000);
        timer.schedule(orderClient.orderTimerTask(), 3000, 8000);
    }
}
