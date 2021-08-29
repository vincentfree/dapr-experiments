package com.github.vincentfree.verticle

import com.github.vincentfree.model.*
import com.github.vincentfree.model.Addresses.GET_ORDER
import com.github.vincentfree.model.Addresses.SEND_ORDER
import io.dapr.client.DaprClientBuilder
import io.vertx.core.Future
import io.vertx.core.eventbus.Message
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.Shareable
import io.vertx.ext.auth.VertxContextPRNG
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.TimeoutHandler
import io.vertx.kotlin.core.http.httpServerOptionsOf
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import org.apache.logging.log4j.kotlin.Logging
import reactor.core.publisher.Mono
import java.util.*
import kotlin.random.Random

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
                daprClient.waitForSidecar(5000).awaitFirst()
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
            route().handler(BodyHandler.create())
            get("/orders/:key").produces(ResponseHeaders.json)
                .handler(TimeoutHandler.create(5000))
                .handler(::getOrder)
            get("/orders").produces(ResponseHeaders.json)
                .handler(TimeoutHandler.create(5000))
                .handler(::orders)
            put("/orders/:key/:name")
                .handler(TimeoutHandler.create(5000))
                .handler(::createOrder)
            post("/orders/events")
                .handler(TimeoutHandler.create(5000))
                .handler(::eventStream)
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
            put("/error/scale/:value")
                .handler(TimeoutHandler.create(5000))
                .handler(::updateScale)
            get("/readiness")
                .handler(TimeoutHandler.create(5000))
                .handler(::readiness)
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

    private fun orders(ctx: RoutingContext) {
        val reply: Future<JsonArray> = bus.request<JsonArray>(Addresses.ORDERS, "")
            .map(Message<JsonArray>::body)
        reply
            .onSuccess { array ->
                ctx.response().end(array.encode())
            }
            .onFailure(ctx::fail)
    }

    private fun eventStream(ctx: RoutingContext) {
        val json = kotlin.runCatching { ctx.bodyAsJson }.getOrElse { JsonObject() }
        runCatching {
            val order = json.getString("data")
            val orderJson = kotlin.runCatching {
                JsonObject(order.cleanupJson()).also {
                    if (VertxContextPRNG.current().nextInt(100) == 0) logger.info { "In catching" }
                }
            }.recover {
                JsonObject(order.cleanupJson()).also {
                    if (VertxContextPRNG.current().nextInt(100) == 0) logger.info { "Recovered" }
                }
            }.getOrThrow()
            bus.publish(SEND_ORDER, orderJson)
        }
            .onSuccess { ctx.response().end() }
            .onFailure(ctx::fail)
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
            bus.request<JsonObject>(GET_ORDER, key).onSuccess { msg ->
                val handleResponse: HttpServerResponse.() -> Unit = handleStatusHeader(msg)
                ctx.response().handleResponse()
            }.onFailure(ctx::fail)
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
            bus.send(SEND_ORDER, payload.toJson())
            ctx.response().apply {
                statusCode = 204
                end()
            }
        } else {
            ctx.response().badRequest("The key could not be extracted form the path. use PUT /orders/:key/")
        }
    }

    private fun readiness(ctx: RoutingContext) {
        if (daprActive) {
            daprClient.waitForSidecar(1500).subscribe(
                { ctx.end() },
                { ctx.fail(it) }
            )
        } else ctx.end()
    }

    private fun HttpServerResponse.badRequest(msg: String) {
        statusCode = 400
        end(msg)
    }

    private fun <T> Mono<out T>.asVertxFlow(): Flow<T> = this.asFlow()
        .flowOn(vertx.dispatcher())

    private fun String.cleanupJson(): String {
        return replace("""\\""", """\""")
            .replace(
                """
                \"
                """.trimIndent(),
                """
                "
                """.trimIndent()
            ).replace(
                """
                ""
                """.trimIndent(),
                """
                "
                """.trimIndent()
            )
    }

    private fun handleStatusHeader(msg: Message<JsonObject>): HttpServerResponse.() -> Unit =
        when (EventStatus.ofStatus(msg)) {
            is EventStatus.Successful -> {
                {
                    apply {
                        putHeader(HttpHeaders.CONTENT_TYPE, ResponseHeaders.json)
                        end(msg.body().encode())
                    }
                }
            }
            is EventStatus.Empty -> {
                {
                    apply {
                        statusCode = 204
                        end()
                    }
                }
            }
            is EventStatus.Failure -> {
                {
                    apply {
                        statusCode = 500
                        end()
                    }
                }
            }
        }
}
