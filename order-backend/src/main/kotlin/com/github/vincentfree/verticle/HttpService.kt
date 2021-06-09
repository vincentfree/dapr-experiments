package com.github.vincentfree.verticle

import com.github.vincentfree.model.Addresses.GET_ORDER
import com.github.vincentfree.model.Addresses.SEND_ORDER
import com.github.vincentfree.model.Order
import com.github.vincentfree.model.ResponseHeaders
import io.dapr.client.DaprClientBuilder
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.TimeoutHandler
import io.vertx.kotlin.core.http.httpServerOptionsOf
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import org.apache.logging.log4j.kotlin.Logging
import java.util.*
import kotlin.random.Random

private const val stateStore = "mongo-state-store"

class HttpService : CoroutineVerticle(), Logging {
    private val server by lazy {
        vertx.createHttpServer(
            httpServerOptionsOf(
                idleTimeout = 5000,
                logActivity = true,
            )
        )
    }
    private val router by lazy { Router.router(vertx) }
    private val bus by lazy { vertx.eventBus() }
    private val daprClient = DaprClientBuilder().build()
    private val daprActive by lazy { config.getString("DAPR_ACTIVE", "true").toBoolean() }
    private var errorProbability = 100

    override suspend fun start() {
        logger.info { "Starting..." }
        router.configureRouter()
        val server = server.requestHandler(router)
            .listen(config.getInteger("port", 8080)).await()
        logger.info { "server started on port: ${server.actualPort()}" }
        if (daprActive) {
            kotlin.runCatching {
                daprClient.waitForSidecar(1000).asFlow()
                    .flowOn(vertx.dispatcher())
                    .first()
            }.onFailure {
                logger.error { "Failed to connect to dapr, starting without verification " }
                logger.info { "Application started" }
            }.onSuccess {
                logger.info { "Application started" }
            }
        } else {
            logger.info { "Application started" }
        }
        logger.info { "Dapr active: $daprActive" }
    }

    override suspend fun stop() {
        super.stop()
    }

    private fun Router.configureRouter(): Router {
        return apply {
            get("/orders/:key").produces(ResponseHeaders.json)
                .handler(TimeoutHandler.create(5000))
                .handler(::getOrder)
            get("/hello")
                .handler(TimeoutHandler.create(5000))
                .handler {
                    if (Random.nextInt(0, 500) == 15) logger.info { "Hey how are you? long time no see" }
                    if (it.queryParam("failure").isNotEmpty()) {
                        if (Random.nextInt(0, errorProbability) == 0) it.response().apply {
                            statusCode = 500
                            end()
                        }
                        else {
                            it.response().end("Hello, ${it.request().host()}!")
                        }
                    } else {
                        it.response().end("Hello world!")
                    }
                }
            put("/orders/:key/:name")
                .handler(TimeoutHandler.create(5000))
                .handler(::createOrder)
            put("/error/scale/:value")
                .handler(TimeoutHandler.create(5000))
                .handler(::updateScale)
        }
    }

    private fun updateScale(ctx: RoutingContext) {
        ctx.pathParam("value")?.let { scale ->
            when (val s = scale.toIntOrNull()) {
                is Int -> {
                    errorProbability = s
                    val msg = "Updated error probability to 1 in $errorProbability"
                    logger.info { msg }
                    ctx.end(msg)
                }
                else -> ctx.end("Could not convert to integer value, original value: $scale")
            }
        }
    }

    private fun getOrder(ctx: RoutingContext) {
        if (daprActive) getOrderDapr(ctx)
        else getOrderVertx(ctx)
    }

    private fun getOrderVertx(ctx: RoutingContext) {
        ctx.pathParam("key")?.let { key ->
            logger.debug { "Getting data for key: $key" }
            val orderFuture = bus.request<JsonObject>(GET_ORDER, key)
            orderFuture
                .onFailure(ctx::fail)
                .onSuccess { msg ->
                    val order = msg.body() ?: JsonObject()
                    ctx.response().apply {
                        putHeader(HttpHeaders.CONTENT_TYPE, ResponseHeaders.json)
                        end(order.encode())
                    }
                }
        }
    }

    private fun getOrderDapr(ctx: RoutingContext) {
        ctx.pathParam("key")?.let { key ->
            logger.debug { "Getting data for key: $key" }
            val mono = daprClient.getState(stateStore, key, String::class.java)
            launch(vertx.dispatcher()) {
                mono.asFlow()
                    .catch {
                        logger.error { "Something failed while getting data from the state store. msg: ${it.message}" }
                    }
                    .collect { state ->
                        ctx.response().apply {
                            putHeader(HttpHeaders.CONTENT_TYPE, ResponseHeaders.json)
                            end(state.value)
                        }
                    }
            }
        } ?: ctx.response().badRequest("The key path param could not be resolved")
    }

    private fun createOrder(ctx: RoutingContext) {
        val key = requireNotNull(ctx.pathParam("key"))
        val name = requireNotNull(ctx.pathParam("name"))
        if (key.isNotBlank() && name.isNotBlank()) {
            val payload = Order(
                id = key,
                name = name,
                orderId = UUID.randomUUID().toString()
            )

            if (daprActive) daprClient.saveState(stateStore, key, payload.toJson())
            else bus.send(SEND_ORDER, payload.toJson())
            ctx.response().apply {
                statusCode = 204
                end()
            }
        } else {
            ctx.response().badRequest("The key could not be extracted form the path. use PUT /orders/:key/")
        }
    }

    private fun HttpServerResponse.badRequest(msg: String) {
        statusCode = 400
        end(msg)
    }
}
