package com.philkolar.volumekeeper.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatcherTest {
    private val own = "com.philkolar.volumekeeper"

    private fun n(
        pkg: String = "com.android.systemui", id: Int = 1007, tag: String? = null, channel: String? = "ALR",
        title: String? = "Volume lowered to safer level",
        text: String? = "Headphone volume has been high for longer than recommended",
    ) = NotificationInfo(pkg, id, tag, "k", 1L, channel, title, text)

    @Test fun `real 1x notification matches`() {
        val r = CsdNotificationMatcher.match(n(), own, simulationEnabled = false)
        assertEquals(MatchKind.CSD, r.kind)
        assertEquals(CsdKind.DOSE_1X, r.csdKind)
    }

    @Test fun `5x text is classified`() {
        val r = CsdNotificationMatcher.match(n(text = "Headphone volume has exceeded the safe limit for this week"), own, false)
        assertEquals(CsdKind.DOSE_5X, r.csdKind)
    }

    @Test fun `id alone matches when the title changes`() {
        assertEquals(MatchKind.CSD, CsdNotificationMatcher.match(n(title = "Lautstärke verringert"), own, false).kind)
    }

    @Test fun `channel plus title matches when the id changes`() {
        assertEquals(MatchKind.CSD, CsdNotificationMatcher.match(n(id = 2001), own, false).kind)
    }

    @Test fun `title alone on another channel is a near miss, not a match`() {
        assertEquals(MatchKind.NEAR_MISS, CsdNotificationMatcher.match(n(id = 2001, channel = "XYZ"), own, false).kind)
    }

    @Test fun `other SystemUI notifications are ignored`() {
        val r = CsdNotificationMatcher.match(n(id = 5, channel = "BAT", title = "Battery saver", text = "On"), own, false)
        assertEquals(MatchKind.OTHER, r.kind)
    }

    @Test fun `same title from another package never matches`() {
        assertEquals(MatchKind.OTHER, CsdNotificationMatcher.match(n(pkg = "com.android.shell"), own, false).kind)
    }

    @Test fun `simulation only matches in the dev variant`() {
        val sim = n(pkg = own, tag = CsdNotificationMatcher.SIM_TAG, channel = "vk_sim")
        val on = CsdNotificationMatcher.match(sim, own, simulationEnabled = true)
        assertTrue(on.simulated)
        assertEquals(MatchKind.CSD, on.kind)
        assertEquals(MatchKind.OTHER, CsdNotificationMatcher.match(sim, own, simulationEnabled = false).kind)
    }
}
