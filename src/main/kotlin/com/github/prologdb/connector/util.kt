package com.github.prologdb.connector

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

fun sleepUninterruptibly(millis: Long) {
    val endAt = System.currentTimeMillis() + millis

    var remaining = millis
    do {
        try {
            Thread.sleep(remaining)
        }
        catch (ex: InterruptedException) {}

        remaining = endAt - System.currentTimeMillis()
    } while (remaining > 0)
}

fun <T> Future<T>.joinUninterruptibly() {
    if (this is CompletableFuture) this.join() else {
        while (!this.isDone) {
            try {
                try {
                    this.get()
                }
                catch (ignore: ExecutionException) {}
                catch (ignore: CancellationException) {}
            } catch(ignore: InterruptedException) {}
        }
    }
}