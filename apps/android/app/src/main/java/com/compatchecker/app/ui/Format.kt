package com.compatchecker.app.ui

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

fun Throwable.displayMessage(): String = when (this) {
    is UnknownHostException, is ConnectException, is SocketTimeoutException ->
        "Cannot reach the server. Check your connection and try again."
    is IOException -> "Network error. Cached content may be available."
    else -> message
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(280)
        ?.ifBlank { null }
        ?: "Something went wrong. Please try again."
}

fun formatDate(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }.getOrElse {
        value.take(10)
    }
}

fun relationshipLabel(value: String): String = when (value) {
    "fits" -> "Fits"
    "works_with" -> "Works with"
    "same_as" -> "Same as"
    "variant_of" -> "Variant of"
    "not_compatible_with" -> "Not compatible"
    else -> value.replace('_', ' ')
}

fun verificationLabel(value: String): String = when (value) {
    "official" -> "Official"
    "community_verified" -> "Community verified"
    "needs_verification" -> "Needs verification"
    "experimental" -> "Experimental"
    else -> value.replace('_', ' ')
}

fun difficultyLabel(value: String): String =
    value.replaceFirstChar { it.uppercase() }

fun statusLabel(value: String): String =
    value.replace('_', ' ').replaceFirstChar { it.uppercase() }
