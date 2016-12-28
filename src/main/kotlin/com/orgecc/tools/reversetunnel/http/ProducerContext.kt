package com.orgecc.tools.reversetunnel.http

import com.orgecc.util.concurrent.WaitForTrueConditionFuture
import java.io.Serializable
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by elifarley on 23/12/16.
 */
object ProducerContext {

    open class QueueItem(val ticket: Long, val line: String, val headers: Map<String, String>, val body: ByteArray) : Serializable {
        fun newResponse(statusCode: Int, responseMessage: String, headers: Map<String, String>, body: ByteArray?)
                = ResponseQueueItem(ticket, statusCode, responseMessage, headers, body ?: ByteArray(0))
    }

    class ResponseQueueItem(ticket: Long, val statusCode: Int, responseMessage: String, headers: Map<String, String>, body: ByteArray)
        : QueueItem(ticket, responseMessage, headers, body)

    val NULL_RESPONSE = ResponseQueueItem(-1, 0, "", mapOf(), ByteArray(0))

    private val requestQ = ConcurrentSkipListMap<Long, QueueItem>()
    private val responseQ = ConcurrentSkipListMap<Long, ResponseQueueItem>()

    fun nextTicket(url: String, headers: Map<String, String>, body: ByteArray) =
            QueueItem(counter.incrementAndGet(), url, headers, body).apply {
                requestQ[this.ticket] = this
            }

    private val counter = AtomicLong()

    fun pop(): QueueItem {

        WaitForTrueConditionFuture(2) {
            requestQ.isNotEmpty()
        }.get(3 * 60000, TimeUnit.MILLISECONDS)

        val ticket = requestQ.firstKey()
        if (responseQ.put(ticket, NULL_RESPONSE) != null) {
            throw IllegalArgumentException("responseQ already had a value for ticket $ticket!")
        }

        println("[ProducerContext.pop] responseQ.size: ${responseQ.size}")

        return requestQ.remove(ticket)!!

    }

    fun reply(responseQueueItem: ResponseQueueItem) {

        println("[ProducerContext.reply] Consumer reply to ticket ${responseQueueItem.ticket}: ${responseQueueItem.statusCode} ${responseQueueItem.line}")

        if (!responseQ.containsKey(responseQueueItem.ticket)) {
            println("[ProducerContext.reply] responseQ.size: ${responseQ.size}")
            responseQ.keys.forEach { println("[ProducerContext.reply] responseQ ticket: $it") }
            throw IllegalArgumentException("Invalid ticket: ${responseQueueItem.ticket}")
        }
        responseQ[responseQueueItem.ticket] = responseQueueItem

    }

    fun waitReplyToTicket(ticket: Long): ResponseQueueItem {

        println("[ProducerContext.waitReplyToTicket] Ticket $ticket...")

        try {
            WaitForTrueConditionFuture(4) {
                responseQ.containsKey(ticket)
            }.get(15000, TimeUnit.MILLISECONDS)

            WaitForTrueConditionFuture(4) {
                responseQ[ticket] !== NULL_RESPONSE
            }.get(15000, TimeUnit.MILLISECONDS)

        } catch(e: TimeoutException) {
            if (requestQ.remove(ticket) == null) {
                println("[ProducerContext.waitReplyToTicket] Unable to remove ticket $ticket from requestQ")
            }
            println("[ProducerContext.waitReplyToTicket] TimeoutException!!! responseQ.size before remove: ${responseQ.size}")
            val removed = responseQ.remove(ticket)
            if (removed != null && removed !== NULL_RESPONSE) {
                println("[ProducerContext.waitReplyToTicket] ERROR: Unexpected item removed from responseQ: ${removed.ticket}")
            }
            throw e
        }

        println("[ProducerContext.waitReplyToTicket] requestQ.size: ${requestQ.size}")
        println("[ProducerContext.waitReplyToTicket] responseQ.size before remove: ${responseQ.size}")

        val result = responseQ.remove(ticket)!!
        if (result.ticket != ticket) {
            throw IllegalArgumentException("Expected ticket: $ticket; Ticket from responseQ: ${result.ticket}")
        }
        return result

    }

}
