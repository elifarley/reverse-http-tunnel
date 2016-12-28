package com.orgecc.util.concurrent

import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Created by elifarley on 27/12/16.
 */
class WaitForTrueConditionFuture(checksPerSecond: Int = 4, val condition: () -> Boolean) : Future<Boolean> {

    private val intervalInMillis = (1e3 / checksPerSecond).toLong()
    private var cancelRequested = false
    private var resultComputed = false

    override fun isDone() = resultComputed

    override fun get(): Boolean {
        if (resultComputed) return true
        while (!condition()) {
            Thread.sleep(intervalInMillis)
        }
        resultComputed = true
        return true
    }

    override fun get(timeout: Long, unit: TimeUnit): Boolean {
        val startNanos = System.nanoTime()
        if (resultComputed) return true
        val deltaNanos = TimeUnit.NANOSECONDS.convert(timeout, unit)

        while (!condition()) {
            if (cancelRequested) return false
            if (System.nanoTime() - startNanos > deltaNanos) throw TimeoutException()
            Thread.sleep(intervalInMillis)
        }
        resultComputed = true
        return true

    }

    override fun isCancelled() = cancelRequested

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        if (resultComputed) return false
        cancelRequested = true
        return true
    }
}