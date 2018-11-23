package com.github.prologdb.connector

import java.util.concurrent.*

/**
 * Like [Thread.sleep] **but cannot be interrupted**.
 */
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

/**
 * Like calling [Future.get] without using the return value **but
 * cannot be interrupted**.
 */
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

/**
 * Like [BlockingQueue.take] **but cannot be interrupted.**
 */
fun <T> BlockingQueue<T>.takeUninterruptibly(): T {
    while (true) {
        try {
            return take()
        }
        catch (ex: InterruptedException) {}
    }
}