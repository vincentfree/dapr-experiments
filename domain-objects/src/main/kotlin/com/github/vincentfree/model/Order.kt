package com.github.vincentfree.model

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
//import com.github.vincentfree.model.proto.


fun toJsonString(): String = toJson().encode()

fun ofJson(json: JsonObject): Result<Order> {
    return with(json) {
        runCatching {
            order {

            }
            Order(
                id = getString("id"),
                name = getString("name"),
                orderId = getString("orderId"),
            )
        }
    }
}

