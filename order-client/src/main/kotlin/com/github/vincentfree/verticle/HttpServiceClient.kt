package com.github.vincentfree.verticle

import io.dapr.client.DaprClientBuilder
import io.dapr.client.domain.HttpExtension
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.HttpRequest
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.ext.web.client.webClientOptionsOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import org.apache.logging.log4j.kotlin.Logging
import reactor.core.publisher.Flux
import kotlin.random.Random

class HttpServiceClient : CoroutineVerticle(), Logging {
    private val options = webClientOptionsOf(
        defaultPort = 3500,
        connectTimeout = 5000,
        defaultHost = "localhost",
        keepAlive = true,
        keepAliveTimeout = 2500,
        maxPoolSize = 1000,
    )

    // values
    private val client by lazy { WebClient.create(vertx, options) }
    private val server by lazy { vertx.createHttpServer() }
    private val router by lazy { Router.router(vertx) }
    private val daprClient = DaprClientBuilder().build()
    private val daprActive by lazy { config.getString("DAPR_ACTIVE", "true").toBoolean() }
    private val invokeService by lazy { config.getString("invocationService", "order-backend") }
    private val backend by lazy { config.getString("ORDER_BACKEND_SERVICE_HOST", "order-backend") }
    private val backendPort by lazy { config.getInteger("ORDER_BACKEND_SERVICE_PORT", 8080) }
    private var addFailure = true

    override suspend fun start() {
        setupServer(initRouter())
            .onSuccess {
                logger.info { "Server started..." }
                logger.info { "Dapr active: $daprActive" }
            }
            .await()
        continuousTraffic()
        if (daprActive) daprClient.waitForSidecar(10000).awaitFirst()
        super.start()
    }

    private fun CoroutineScope.continuousTraffic() {
        vertx.setPeriodic(config.getLong("interval", 1000)) {
            if (daprActive) {
                launch {
                    runCatching {
                        daprClient
                            .invokeMethod(invokeService, "hello", HttpExtension.GET, mapOf())
                            .awaitFirst()
                    }.onFailure { logger.error { "Failed to invoke! msg: ${it.message}" } }
                }
//                daprClient.invokeMethod(invokeService, "hello", HttpExtension.GET, mapOf())
//                    .subscribe(
//                    { logger.debug { "invoked" } },
//                    { logger.error { "Failed to invoke! msg: ${it.message}" } },
//                )
            } else {
                client.get(backendPort, backend, "/hello")
                    .handleFailure()
                    .send()
            }
        }
    }

    private fun HttpRequest<Buffer>.handleFailure() = if (addFailure) {
        addQueryParam(
            "failure",
            "true"
        )
    } else this

    private fun setupServer(router: Router): Future<HttpServer> {
        return server
            .requestHandler(router)
            .listen(config.getInteger("port", 8080))
    }

    private fun initRouter(): Router {
        return router.apply {
            if (daprActive) concurrentHelloDapr()
            concurrentHelloVertx()
            toggleFailure()
        }
    }

    private fun Router.toggleFailure() {
        get("/failure/:switch").handler { ctx ->
            ctx.pathParam("switch")?.let { switch ->
                kotlin.runCatching { switch.toBoolean() }
                    .onSuccess {
                        addFailure = it
                        ctx.response().end("Successfully set failure option to: $addFailure")
                    }
                    .onFailure { ctx.end("Unable to parse content to boolean") }
            } ?: ctx.end("Nothing changed")
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
                        .send()
                    else client
                        .get(backendPort, backend, "/hello")
                        .handleFailure()
                        .`as`(BodyCodec.string())
                        .send()
                        .onFailure { logger.error { "The request was not successful.." } }
                        .onSuccess {
                            if (Random.nextInt(0, 50) == 1 && it.statusCode() <= 399) {
                                logger.info { "Message: ${it.body()}" }
                            } else {
                                logger.error { "The request was not successful.. status code: ${it.statusCode()}" }
                            }
                        }
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
            ctx.response().apply {
                statusCode = 204
                end()
            }
        }
    }
}