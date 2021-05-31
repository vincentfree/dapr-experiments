package com.github.vincentfree

import com.github.vincentfree.verticle.HttpServiceClient
import io.vertx.config.ConfigRetriever
import io.vertx.core.Vertx
import io.vertx.kotlin.core.deploymentOptionsOf

fun main() {
    val vertx = Vertx.vertx()
    val retriever = ConfigRetriever.create(vertx)
    retriever.config.onSuccess { config ->
        vertx.deployVerticle(HttpServiceClient(), deploymentOptionsOf(config = config))
    }
}