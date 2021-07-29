package com.github.vincentfree.model

import io.vertx.core.json.JsonObject

data class OrderBackendOptions(
    val stateStore: String = "mongo-state-store",
    val daprActive: Boolean = true,
    val isInMemory: Boolean = true,
) {
    companion object {
        fun ofConfig(config: JsonObject): Result<OrderBackendOptions> = with(config) {
            runCatching {
                OrderBackendOptions(
                    stateStore = requireNotNull(getString("STATE_STORE")),
                    daprActive = requireNotNull(getString("DAPR_ACTIVE").toBoolean()),
                    isInMemory = requireNotNull(getString("IN_MEMORY_DB").toBoolean()),
                )
            }
        }
    }
}
