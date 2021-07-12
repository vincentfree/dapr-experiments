package com.github.vincentfree.verticle

import com.github.vincentfree.model.Addresses
import com.github.vincentfree.model.Order
import io.dapr.client.DaprClientBuilder
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.toChannel
import kotlinx.coroutines.reactive.awaitFirst
import org.apache.logging.log4j.kotlin.Logging

class DataBaseVerticle : CoroutineVerticle(), Logging {
    private val mongoState = "mongo-state-store"
    private val daprClient = DaprClientBuilder().build()
    private val daprActive by lazy { config.getString("DAPR_ACTIVE", "true").toBoolean() }
    private val mapEntry: (Order) -> Pair<String, Order> = { it.orderId to it }
    private val toEntry: Order.() -> Pair<String, Order> = { orderId to this }
    private val orders = hashMapOf(
        mapEntry(Order("1", "toy", "01-John")),
        mapEntry(Order("2", "toy", "01-Alex")),
        mapEntry(Order("3", "book", "02-John")),
        mapEntry(Order("4", "toy", "01-Karen")),
        mapEntry(Order("5", "toy", "01-Phil")),
    )

    private val bus by lazy { vertx.eventBus() }

    override suspend fun start() {
        orders()
        sendOrder()
        getOrder()
        super.start()
    }

    private fun orders() {
        bus.consumer<String>("orders").handler { msg ->
            val result = if (daprActive) {

                daprClient.getState(
                    mongoState,
                    "key1",
                    Order::class.java
                )
                TODO("Implement dapr result")
            } else {
                orders.values
                    .map(Order::toJson)
                    .fold(JsonArray()) { array, json -> array.add(json) }
            }
            msg.reply(result)
        }
    }

    private fun sendOrder() {
        bus.consumer<JsonObject>(Addresses.SEND_ORDER).handler { msg ->
            val json = msg.body()
            kotlin.runCatching { json.mapTo(Order::class.java) }
                .onSuccess { order ->
                    if (daprActive) {
                        //TODO delete log line
                        logger.info { "Saving to state store with the current json: ${json.encodePrettily()}" }
                        daprClient.saveState(mongoState, order.orderId, order)
                    } else orders += order.toEntry()
                }
                .onFailure {
                    logger.info { "Failed to persist order, unable to map to Order class, msg: ${it.message}" }
                }
        }
    }

    private suspend fun getOrder() {
        val channel = bus.consumer<String>(Addresses.GET_ORDER).toChannel(vertx)
        for (msg in channel) {
            val key = msg.body()
            val order = if (daprActive) {
                daprClient.getState(mongoState, key, Order::class.java)
                    .awaitFirst().value
            } else orders[key]
            order?.let {
                msg.reply(it.toJson(), DeliveryOptions().addHeader("status", "success"))
            } ?: msg.reply(
                JsonObject(),
                DeliveryOptions().addHeader("status", "empty")
            )
        }

    }
}