package com.github.vincentfree.verticle

import io.dapr.client.DaprClientBuilder
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpServerResponse
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

private const val stateStore = "mongo-state-store"

class HttpService : CoroutineVerticle(), Logging {
    private val server by lazy { 
    vertx.createHttpServer(httpServerOptionsOf(
            idleTimeout = 5000,
            logActivity = true,
        ))
    }
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
            get("/orders/:key").produces("application/json")
                .handler(TimeoutHandler.create(5000))
                .handler(::getOrder)
            get("/hello")
                .handler(TimeoutHandler.create(5000))
                .handler {
                it.response().end("Hello world!")
            }
            put("/orders/:key")
                .handler(TimeoutHandler.create(5000))
                .handler(::createOrder)
        }
    }

    private fun getOrder(ctx: RoutingContext) {
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
                        putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        end(state.value)
                    }
                }
            }
        } ?: ctx.response().badRequest("The key path param could not be resolved")
    }

    private fun createOrder(ctx: RoutingContext) {
        val key = ctx.pathParam("key")
        val payload = ctx.request().body()
            .map { it.toJsonObject() }
            .onFailure {
                logger.error { "Failed to extract Json payload" }
                ctx.response().badRequest("Failed to extract Json payload")
            }
        if (key.isNotBlank()) {
            payload.onSuccess { json ->
                daprClient.saveState(stateStore, key, json.encode())
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
