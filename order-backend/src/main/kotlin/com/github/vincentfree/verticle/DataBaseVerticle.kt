package com.github.vincentfree.verticle

import com.github.vincentfree.model.Addresses
import com.github.vincentfree.model.Order
import com.github.vincentfree.model.OrderBackendOptions
import io.dapr.client.DaprClientBuilder
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.toReceiveChannel
import kotlinx.coroutines.reactive.awaitFirst
import org.apache.logging.log4j.kotlin.Logging

class DataBaseVerticle : CoroutineVerticle(), Logging {
    private val daprClient = DaprClientBuilder().build()
    private val orderOptions by lazy {
        OrderBackendOptions.ofConfig(config).getOrElse { OrderBackendOptions() }
    }
    private val mapEntry: (Order) -> Pair<String, Order> = { it.orderId to it }
    private val toEntry: Order.() -> Pair<String, Order> = { orderId to this }
    private val orders = mutableMapOf(
        mapEntry(Order("1", "toy", "01-John")),
        mapEntry(Order("2", "toy", "01-Alex")),
        mapEntry(Order("3", "book", "02-John")),
        mapEntry(Order("4", "toy", "01-Karen")),
        mapEntry(Order("5", "toy", "01-Phil")),
    )

    private val bus by lazy { vertx.eventBus() }

    override suspend fun start() {
        logger.info { "Started DataBaseVerticle..." }
        orders()
        sendOrder()
        getOrder()
        super.start()
    }

    private fun orders() {
        bus.consumer<String>(Addresses.ORDERS).handler { msg ->
            val result = if (orderOptions.isInMemory) {
                orders.values
                    .map(Order::toJson)
                    .fold(JsonArray()) { array, json -> array.add(json) }
            } else {
                TODO("Implement DB storage backend")
            }
            msg.reply(result)
        }
    }

    private fun sendOrder() {
        bus.consumer<JsonObject>(Addresses.SEND_ORDER).handler { msg ->
            val json = msg.body()
            kotlin.runCatching { json.mapTo(Order::class.java) }
                .recoverCatching { Order.ofJson(json).getOrThrow() }
                .onSuccess { order ->
                    val (key,value) =  if (orderOptions.isInMemory) {
                        order.toEntry()
                    } else {
                        if (orderOptions.daprActive) {
                            daprClient.saveState(orderOptions.stateStore, order.orderId, order)
                        }
                        //TODO("Implement DB storage backend")
                        order.toEntry()
                    }
                    orders[key] = value
                }
                .onFailure {
                    logger.info { "Failed to persist order, unable to map to Order class, msg: ${it.message}" }
                }
        }
    }

    private suspend fun getOrder() {
        val channel = bus.consumer<String>(Addresses.GET_ORDER).toReceiveChannel(vertx)
        for (msg in channel) {
            val key = msg.body()

            val order = if (orderOptions.isInMemory) {
                orders[key]
            } else {
                if (orderOptions.daprActive) {
                    daprClient.getState(orderOptions.stateStore, key, Order::class.java)
                        .awaitFirst().value
                } else orders[key]
            }
            order?.let {
                msg.reply(it.toJson(), DeliveryOptions().addHeader("status", "success"))
            } ?: msg.reply(JsonObject(), DeliveryOptions().addHeader("status", "empty"))
        }
    }
}
