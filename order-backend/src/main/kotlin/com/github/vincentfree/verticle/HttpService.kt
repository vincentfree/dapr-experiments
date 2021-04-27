package com.github.vincentfree.verticle

import io.dapr.client.DaprClientBuilder
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonArray
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.reactive.asFlow
import org.apache.logging.log4j.kotlin.Logging

class HttpService : CoroutineVerticle(), Logging {
    private val server by lazy { vertx.createHttpServer() }
    private val router by lazy { Router.router(vertx) }
    private val bus by lazy { vertx.eventBus() }
    private val daprClient = DaprClientBuilder().build()

    override suspend fun start() {
        logger.info { "Starting..." }
        router.configureRouter()
        val server = server.requestHandler(router)
            .listen(config.getInteger("port", 8080)).await()
        logger.info { "server started on port: ${server.actualPort()}" }
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
    }

    override suspend fun stop() {
        super.stop()
    }

    private fun Router.configureRouter(): Router {
        return apply {
            get("/orders").handler(::orders)
            get("/hello").handler {
                it.response().end("Hello world!")
            }
        }
    }

    private fun orders(ctx: RoutingContext) {
        bus.request<JsonArray>("orders", "")
            .onSuccess { msg ->
                ctx.response().apply {
                    putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    when (msg.body()) {
                        is JsonArray -> end(msg.body().encode())
                        else -> {
                            statusCode = 204
                            end("[]")
                        }
                    }
                }
            }
            .onFailure { ctx.fail(500) }
    }

    private fun createOrder(ctx: RoutingContext) {
        TODO("add a endpoint to add orders, order can be a json object")
        //ctx.request().
    }
}
