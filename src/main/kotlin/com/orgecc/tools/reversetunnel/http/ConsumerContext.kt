package com.orgecc.tools.reversetunnel.http

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.HttpException
import com.orgecc.util.withFirstValues
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.concurrent.thread

/**
 * Created by elifarley on 27/12/16.
 */
object ConsumerContext {

    private val requestQ = ConcurrentSkipListMap<Long, Pair<String, ProducerContext.QueueItem>>()

    fun push(hostAndQueueItem: Pair<String, ProducerContext.QueueItem>) = requestQ.put(hostAndQueueItem.second.ticket, hostAndQueueItem)

    fun startAsync() {
        thread(name = "consumer") { consume() }
    }

    private fun consume() {

        println("[ConsumerContext.consume] Main loop started.")

        while (true) {

            while (requestQ.isEmpty()) {
                Thread.sleep(500)
            }

            val ticket = requestQ.firstKey()

            val queueItem = requestQ.remove(ticket)!!
            println("[ConsumerContext.consume] Will consume ticket $ticket")
            remoteConnect(queueItem)

        }

    }

    private fun remoteConnect(hostAndQueueItem: Pair<String, ProducerContext.QueueItem>) {

        val headers = hostAndQueueItem.second.headers

        Fuel.post(hostAndQueueItem.second.line)
                .header(headers)
                .body(hostAndQueueItem.second.body)
                .response { request, response, result ->

                    println("[ConsumerContext.remoteConnect] Response received: ${response.httpStatusCode} ${response.httpResponseMessage}")
                    println(request)
                    println(response)
                    val (remoteBody, error) = result

                    val body = if (error == null) remoteBody else response.data

                    reply(hostAndQueueItem.first to
                            hostAndQueueItem.second.newResponse(response.httpStatusCode,
                            response.httpResponseMessage, response.httpResponseHeaders.withFirstValues, body))

                    if (error != null) {

                        val exception = error.exception
                        if (exception !is HttpException) {
                            println("[ConsumerContext.remoteConnect] Exception: $exception")

                        }

                        println("[ConsumerContext.remoteConnect] ERROR: $error")
                    }

                }

    }

    private fun reply(producerAndreply: Pair<String, ProducerContext.ResponseQueueItem>) {

        val baos = ByteArrayOutputStream()
        ObjectOutputStream(baos).use {
            it.writeObject(producerAndreply.second)
        }

        val replyUrl = "${producerAndreply.first}/reply/${producerAndreply.second.ticket}"

        println("[ConsumerContext.reply] Posting reply to '$replyUrl'...")

        val (request, response, result) = Fuel.post(replyUrl)
                .header(TICKET_HEADER to producerAndreply.second.ticket)
                .body(baos.toByteArray())
                .response()

        println(request)
        println(response)


        val (body, error) = result
        if (body != null) {
            println("[ConsumerContext.reply] ${producerAndreply.second.ticket}: ${String(body)}")
        } else {
            println("[ConsumerContext.reply] ERROR: $error - ${String(error!!.response.data)}")
        }

    }

}