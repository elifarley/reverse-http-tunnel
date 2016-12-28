package com.orgecc.tools.reversetunnel.http

import com.orgecc.util.toMap
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.UndertowOptions
import io.undertow.attribute.ExchangeAttributes
import io.undertow.io.IoCallback
import io.undertow.io.Sender
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.BlockingHandler
import io.undertow.util.HttpString
import io.undertow.util.StatusCodes
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

/**
 * Created by elifarley on 23/12/16.
 */
object HttpProducer {

    fun start() {

        val producerProxyHandler = object : HttpHandler {
            override fun handleRequest(exchange: HttpServerExchange) {

                if (exchange.isInIoThread) {
                    exchange.dispatch(this)
                    return
                }

                val headers = exchange.requestHeaders
                println("NEW PROXY REQUEST: ${exchange.requestURL}")
                println("remoteIp: ${ExchangeAttributes.remoteIp().readAttribute(exchange)}")
                println("${exchange.sourceAddress} -> ${exchange.destinationAddress}")
                println("headers: $headers")

                exchange.startBlocking()

                val queueItem = ProducerContext.nextTicket(exchange.requestURL, headers.toMap(), exchange.inputStream.readBytes())

                val bodyStr = String(queueItem.body, Charset.forName("UTF-8"))
                println("[producerProxyHandler] Waiting for reply to ticket ${queueItem.ticket}: $bodyStr")

                val remoteResponse: ProducerContext.ResponseQueueItem
                try {
                    remoteResponse = ProducerContext.waitReplyToTicket(queueItem.ticket)
                    println("[producerProxyHandler] Ticket reply received: ${remoteResponse.ticket}")


                } catch(e: TimeoutException) {
                    println("[producerProxyHandler] Timed out waiting for ticket ${queueItem.ticket}. Ending exchange.")
                    exchange.responseHeaders.put(HttpString(TICKET_HEADER), queueItem.ticket)
                    exchange.statusCode = StatusCodes.REQUEST_TIME_OUT
                    exchange.responseSender.send("Timeout while waiting for reply to ticket ${queueItem.ticket}.")
                    exchange.endExchange()
                    return
                }

                // TODO it.key sometimes is null - fix
                remoteResponse.headers.filter { it.key != null }.forEach {
                    exchange.responseHeaders.add(HttpString.tryFromString(it.key), it.value)
                }
                exchange.statusCode = remoteResponse.statusCode
                exchange.responseSender.send(ByteBuffer.wrap(remoteResponse.body), IoCallback.END_EXCHANGE)

            }
        }

        val producerProxy = Undertow.builder()
                .addHttpListener(1080, "localhost")
                .setHandler(producerProxyHandler).build()

        thread(name = "proxy", isDaemon = false) { producerProxy.start() }

        // ---

        val consumerServerHandler = Handlers.path().addPrefixPath("/pop") { exchange: HttpServerExchange ->

            println("NEW CONSUMER REQUEST: ${exchange.requestURL}")
            println("${exchange.sourceAddress} -> ${exchange.destinationAddress}")

            exchange.responseSender.send("", object : IoCallback {

                override fun onComplete(exchange: HttpServerExchange, sender: Sender) {

                    val queueItem: ProducerContext.QueueItem
                    try {
                        queueItem = ProducerContext.pop()

                    } catch(e: TimeoutException) {
                        println("[/pop] Timed out waiting for item. Ending exchange.")
                        exchange.statusCode = StatusCodes.REQUEST_TIME_OUT
                        exchange.endExchange()
                        return
                    }

                    println("[/pop] Sending ticket ${queueItem.ticket}")

                    val baos = ByteArrayOutputStream()
                    ObjectOutputStream(baos).use {
                        it.writeObject(queueItem)
                    }

                    sender.send(ByteBuffer.wrap(baos.toByteArray()), IoCallback.END_EXCHANGE)

                }

                override fun onException(exchange: HttpServerExchange, sender: Sender, exception: IOException) {
                    println("[/pop] $exception")
                    exchange.endExchange()
                }
            })

        }.addPrefixPath("/reply") { exchange: HttpServerExchange ->

            println("NEW CONSUMER REQUEST: ${exchange.requestURL}")

            exchange.startBlocking()
            val responseQueueItem = ObjectInputStream(exchange.inputStream).readObject() as ProducerContext.ResponseQueueItem
            println("[/reply] Got reply to ticket ${responseQueueItem.ticket}")

            ProducerContext.reply(responseQueueItem)
            exchange.responseSender.send("Reply posted.\n")

        }

        val consumerServer = Undertow.builder()
                .addHttpListener(1081, "localhost")
                .setServerOption(UndertowOptions.IDLE_TIMEOUT, 60000)
                .setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, 60000)
                .setHandler(BlockingHandler(consumerServerHandler)).build()

        thread(name = "consumer", isDaemon = true) { consumerServer.start() }

    }

}