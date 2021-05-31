package com.github.vincentfree.verticle

import io.dapr.client.DaprClientBuilder
import io.dapr.client.domain.HttpExtension
import io.vertx.core.Future
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.ext.web.client.webClientOptionsOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import org.apache.logging.log4j.kotlin.Logging
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class HttpServiceClient : CoroutineVerticle(), Logging {
    private val options = webClientOptionsOf(
        defaultPort = 3500,
        connectTimeout = 5000,
        defaultHost = "localhost",
        keepAlive = true,
        keepAliveTimeout = 2500,
        maxPoolSize = 1000,
    )
    private val client by lazy { WebClient.create(vertx, options) }
    private val server by lazy { vertx.createHttpServer() }
    private val router by lazy { Router.router(vertx) }
    private val daprClient = DaprClientBuilder().build()
    private val daprActive by lazy { config.getString("DAPR_ACTIVE", "true").toBoolean() }
    private val invokeService by lazy { config.getString("invocationService", "order-backend") }
    private val backend by lazy { config.getString("ORDER_BACKEND_SERVICE_HOST", "order-backend") }
    private val backendPort by lazy { config.getInteger("ORDER_BACKEND_SERVICE_PORT", 8080) }

    override suspend fun start() {
        setupServer(initRouter())
            .onSuccess {
                logger.info { "Server started..." }
                logger.info { "Dapr active: $daprActive" }
            }
            .await()
        continuousTraffic()
        super.start()
    }

    private fun continuousTraffic() {
        vertx.setPeriodic(config.getLong("interval", 1000)) {
            if (daprActive) {
                daprClient.invokeMethod(
                    invokeService,
                    "hello",
                    HttpExtension.GET,
                    mapOf()
                ).subscribe(
                    { logger.debug { "invoked" } },
                    { logger.error { "Failed to invoke! msg: ${it.message}" } },
                )
            } else {
                client.get(backendPort, backend, "/hello")
                    .addQueryParam("failure","true")
                    .send()
            }
        }
    }

    private fun setupServer(router: Router): Future<HttpServer> {
        return server
            .requestHandler(router)
            .listen(config.getInteger("port", 8080))
    }

    private fun initRouter(): Router {
        return router.apply {
            if (daprActive) concurrentHelloDapr()
            concurrentHelloVertx()
        }
    }

    private fun Router.concurrentHelloVertx() {
        get("/vertx/hello/:number").handler { ctx ->
            val times = ctx.pathParam("number").toIntOrNull() ?: 5
            logger.info { "Calling the hello service $times times" }
            repeat(times) {
                launch(vertx.dispatcher()) {
                    if (daprActive) client
                        .get("/v1.0/invoke/$invokeService/method/hello")
                        .addQueryParam("failure","true")
                        .send()
                    else client
                        .get(backendPort, backend, "/hello")
                        .addQueryParam("failure","true")
                        .send()
                }
            }
            ctx.response().apply {
                statusCode = 204
                end()
            }
        }
    }

    private fun Router.concurrentHelloDapr() {
        get("/call/hello/:number").handler { ctx ->
            val times = ctx.pathParam("number").toIntOrNull() ?: 5
            logger.info { "Calling the hello service $times times" }
            launch(vertx.dispatcher()) {
                Flux.range(0, times).flatMap {
                    daprClient.invokeMethod(
                        invokeService,
                        "hello",
                        HttpExtension.GET,
                        mapOf()
                    )
                }
                    .asFlow().flowOn(vertx.dispatcher()).collect()
            }
//                .subscribe(
//                { logger.debug { "invoked: $times" } },
//                { logger.error(it) { "Failed to invoke! $times" } },
//            )

            ctx.response().apply {
                statusCode = 204
                end()
            }
        }
    }
}