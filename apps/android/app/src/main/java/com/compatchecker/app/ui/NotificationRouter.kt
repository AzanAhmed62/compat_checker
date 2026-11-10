package com.compatchecker.app.ui

import java.net.URI

/**
 * Server-generated notification links are relative paths. They are parsed
 * here instead of being passed to an arbitrary external browser, so unsupported
 * or malformed links stay inside the app and produce a visible message.
 */
sealed interface NotificationDestination {
    data class Product(val id: String) : NotificationDestination
    data class Guide(val id: String) : NotificationDestination
    data class Rule(val id: String) : NotificationDestination
    data class Profile(val id: String) : NotificationDestination
    data class Collection(val id: String) : NotificationDestination
    data object None : NotificationDestination
    data object Unsupported : NotificationDestination
}

private val notificationPathPattern = Regex(
    """^/(product|guide|rule|profile|collection)/""" +
        """([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/?$""",
)

fun parseNotificationLink(link: String?): NotificationDestination {
    val trimmed = link?.trim().orEmpty()
    if (trimmed.isEmpty()) return NotificationDestination.None

    val path = when {
        trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) -> {
            runCatching { URI(trimmed).path }.getOrNull()
                ?: return NotificationDestination.Unsupported
        }
        trimmed.startsWith("/") -> trimmed.substringBefore('?').substringBefore('#')
        else -> return NotificationDestination.Unsupported
    }

    val match = notificationPathPattern.matchEntire(path)
        ?: return NotificationDestination.Unsupported
    val id = match.groupValues[2]
    return when (match.groupValues[1].lowercase()) {
        "product" -> NotificationDestination.Product(id)
        "guide" -> NotificationDestination.Guide(id)
        "rule" -> NotificationDestination.Rule(id)
        "profile" -> NotificationDestination.Profile(id)
        "collection" -> NotificationDestination.Collection(id)
        else -> NotificationDestination.Unsupported
    }
}
