package com.github.vincentfree

import com.github.vincentfree.verticle.HttpServiceClient
import io.vertx.core.Vertx

fun main() {
    val vertx = Vertx.vertx()
    vertx.deployVerticle(HttpServiceClient())
}