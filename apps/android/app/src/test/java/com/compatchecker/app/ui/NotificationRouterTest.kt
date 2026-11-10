package com.compatchecker.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationRouterTest {
    private val id = "60000000-0000-4000-8000-000000000001"

    @Test
    fun `routes the supported notification destinations`() {
        assertEquals(
            NotificationDestination.Product(id),
            parseNotificationLink("/product/$id"),
        )
        assertEquals(
            NotificationDestination.Guide(id),
            parseNotificationLink("/guide/$id?from=notification"),
        )
        assertEquals(
            NotificationDestination.Rule(id),
            parseNotificationLink("https://compatchecker.test/rule/$id"),
        )
        assertEquals(
            NotificationDestination.Profile(id),
            parseNotificationLink("/profile/$id/"),
        )
        assertEquals(
            NotificationDestination.Collection(id),
            parseNotificationLink("/collection/$id#items"),
        )
    }

    @Test
    fun `treats a missing link as no navigation`() {
        assertEquals(NotificationDestination.None, parseNotificationLink(null))
        assertEquals(NotificationDestination.None, parseNotificationLink("   "))
    }

    @Test
    fun `rejects unknown malformed and unsafe links`() {
        assertEquals(NotificationDestination.Unsupported, parseNotificationLink("/profile"))
        assertEquals(NotificationDestination.Unsupported, parseNotificationLink("/product/not-a-uuid"))
        assertEquals(
            NotificationDestination.Unsupported,
            parseNotificationLink("javascript:alert('/product/$id')"),
        )
    }
}
