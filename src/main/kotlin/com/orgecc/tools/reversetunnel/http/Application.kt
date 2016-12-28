package com.orgecc.tools.reversetunnel.http

/**
 * Created by elifarley on 23/12/16.
 *
 * See also:
 * https://github.com/ivanilves/CloudPort
 * https://en.wikipedia.org/wiki/BOSH
 * https://en.wikipedia.org/wiki/WebSocket
 *
 */

const val DEFAULT_PRODUCER_PORT = "1081"
const val TICKET_HEADER = "reverse-http-tunnel-ticket"

fun main(vararg args: String) {

    if (args.isEmpty()) {
        println("Use '--producer' for proxy or '--consumer-of producer-1.localdomain producer-2.localdomain:${DEFAULT_PRODUCER_PORT} [...]'")
        return
    }

    if (args[0] == "--producer") {
        HttpProducer.start()

    } else if (args[0] == "--consumer-of") {
        HttpConsumer.start(args.drop(1))
    }

}

/*

Response : Not Found
Length : 0
Body : (empty)
Headers : (5)
null : [HTTP/1.1 404 Not Found]
Helios-Server-Version : [0.9.0-SNAPSHOT]
Content-Length : [0]
Helios-Version-Status : [MISSING]
Date : [Tue, 27 Dec 2016 14:23:29 GMT]


curl -v -xlocalhost -X POST \
-H "Content-type:application/json" \
-SL http://cielo-helios.prd.dcd.m4u:8080/deployment-group/BLAGROUP/rolling-update \
-d@- <<-EOF
{
    "job": "my-job-id",
    "rolloutOptions": {
      "parallelism": 1,
      "timeout": 300,
      "overlap": false,
      "token": ""
    }
}
EOF


curl -vX POST \
-H "Content-type:application/json" \
-SL http://localhost:1081/reply/1 \
-d@- <<-EOF
{"test": 45}
EOF


*/