package com.github.vincentfree.model

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf

data class Order(val id: String, val name: String, val orderId: String) {
    fun toJson(): JsonObject = jsonObjectOf(
        "id" to id,
        "name" to name,
        "orderId" to orderId,
    )
}
