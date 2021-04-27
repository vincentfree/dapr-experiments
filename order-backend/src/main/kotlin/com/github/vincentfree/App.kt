package com.github.vincentfree

import com.github.vincentfree.verticle.HttpService
import io.vertx.core.Vertx

fun main() {
    val vertx = Vertx.vertx()
    vertx.deployVerticle(HttpService())
}
