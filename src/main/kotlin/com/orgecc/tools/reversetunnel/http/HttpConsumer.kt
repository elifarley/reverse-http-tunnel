package com.orgecc.tools.reversetunnel.http

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.HttpException
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import kotlin.concurrent.thread

/**
 * Created by elifarley on 23/12/16.
 */
class HttpConsumer() {

    companion object {
        fun start(producers: Collection<String>) {
            HttpConsumer().consumeAll(producers)
        }
    }

    init {
        ConsumerContext.startAsync()
    }

    fun asProducerSpec(producer: String): String {
        var producerSpec = producer.toLowerCase()
        if (!producer.contains("://")) producerSpec = "http://$producerSpec"
        if (!producer.matches(""":[0-9]+$""".toRegex())) producerSpec += ":${DEFAULT_PRODUCER_PORT}"
        return producerSpec
    }

    private fun consumeAll(producers: Collection<String>) = producers.forEach { producer ->
        thread(name = producer) { consume(asProducerSpec(producer)) }
    }

    fun consume(producer: String) {

        while (true) {

            println("[HttpConsumer.pop] Trying to connect...")
            val (request, response, result) = Fuel.get("$producer/pop/").timeoutRead(5 * 60000).response()

            val (body, error) = result

            if (body != null) {
                ObjectInputStream(ByteArrayInputStream(body)).use {
                    val queueItem = it.readObject() as ProducerContext.QueueItem
                    println("[HttpConsumer.pop] Got ticket ${queueItem.ticket}")
                    ConsumerContext.push(producer to queueItem)
                }
            } else {
                val exception = error!!.exception
                if (exception is HttpException && exception.httpCode == 408) {
                    println("[HttpConsumer.pop] Nothing to pop.")

                } else {
                    println("[pop] $error")

                }

                Thread.sleep(2000)
            }

        }
    }

}