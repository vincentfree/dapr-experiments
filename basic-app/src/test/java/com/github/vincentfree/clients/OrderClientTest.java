package com.github.vincentfree.clients;

import com.github.vincentfree.model.Order;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

public class OrderClientTest {
    @Test
    void wrapJson() {
        var order = new Order("id", "name", "orderId");
        var client = new OrderClient(HttpClient.newHttpClient());
        String result = client.wrapPayload(order.toJsonString());
        System.out.println(result);
        Assertions.assertEquals(
                "{ \"data\": {{\"id\":\"id\",\"name\":\"name\",\"orderId\":\"orderId\"}} }",
                result
        );
    }
}
