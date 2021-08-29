package com.github.vincentfree.clients;

import com.github.vincentfree.model.Order;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OrderClientTest {
    @Test
    void wrapJson() {
        var order = new Order("id", "name", "orderId");
        String result = order.toJsonString();
        System.out.println(result);
        Assertions.assertEquals("{\"id\":\"id\",\"name\":\"name\",\"orderId\":\"orderId\"}", result);
    }
}
