package com.github.vincentfree.verticle

import com.github.vincentfree.model.Order
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineVerticle

class DataBaseVerticle : CoroutineVerticle() {
    private val mapEntry: (Order) -> Pair<String, Order> = { it.orderId to it }
    private val orders = hashMapOf(
        mapEntry(Order(1, "toy", "01-John")),
        mapEntry(Order(2, "toy", "01-Alex")),
        mapEntry(Order(3, "book", "02-John")),
        mapEntry(Order(4, "toy", "01-Karen")),
        mapEntry(Order(5, "toy", "01-Phil")),
    )

    private val bus by lazy { vertx.eventBus() }

    override suspend fun start() {
        orders()
        addOrder()
        super.start()
    }

    private fun orders() {
        bus.consumer<String>("orders").handler { msg ->
            val array = orders.values
                .map(Order::toJson)
                .fold(JsonArray()) { array, json -> array.add(json) }
            msg.reply(array)
        }
    }

    private fun addOrder() {
        bus.consumer<JsonObject>("add.order").handler { msg ->
            val json = msg.body()
            val order = kotlin.runCatching { json.mapTo(Order::class.java) }
                .map { mapEntry(it) }
                .onSuccess { (k, v) -> orders[k] = v }
        }
    }
}