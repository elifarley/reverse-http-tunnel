package com.orgecc.util

import io.undertow.util.HeaderMap

/**
 * Created by elifarley on 27/12/16.
 */

val Map<String, List<*>>.withFirstValues: Map<String, String> get() = this.mapValues { it.value.first().toString() }

fun HeaderMap.toMap() = this.map { it.headerName.toString() to it.first }.toMap()

