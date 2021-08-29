package com.github.vincentfree.model

import io.vertx.core.eventbus.Message

sealed interface Status

fun type(): String = "test"
sealed class EventStatus : Status {
    object Successful : Status
    object Empty : Status
    object Failure : Status

    companion object {
        private fun <T> Message<in T>.hasStatus(): Boolean = headers()["status"] != null
        fun <T> ofStatus(msg: Message<in T>): Status {
            return if (msg.hasStatus()) {
                when (msg.headers()["status"]) {
                    "success" -> Successful
                    "empty" -> Empty
                    else -> Failure
                }
            } else Failure
        }
    }
}