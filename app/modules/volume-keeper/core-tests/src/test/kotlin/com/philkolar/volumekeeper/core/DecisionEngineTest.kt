package com.philkolar.volumekeeper.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Scenario names mirror docs/detection-rule.md. */
class DecisionEngineTest {

    private fun settled(level: Int = 18) = Harness().apply { change(level); advance(30_000) }

    @Test fun `R4 drop then notice restores the broadcast's previous level`() {
        val h = settled(18)
        h.change(6)
        h.advance(200)
        h.notice()
        assertEquals(listOf(Action.Restore(1, SONY, 18)), h.restores())
        h.applyRestores()
        val d = h.lastRecord()
        assertEquals(Outcome.RESTORED, d.outcome)
        assertEquals("notif+broadcast", d.signal)
        assertEquals(18, d.restored)
        assertEquals(6, d.lowered)
        assertEquals(500L, d.latencyMs)
    }

    @Test fun `R1 notice then drop waits for the drop and restores`() {
        val h = settled(18)
        val first = h.notice()
        assertTrue(first.single() is Action.Schedule)
        h.advance(150)
        h.change(6)
        assertEquals(18, h.restores().single().target)
    }

    @Test fun `R2 drop up to W_BEFORE before the notice still counts`() {
        val h = settled(20)
        h.change(6)
        h.advance(Windows.W_BEFORE - 100)
        h.notice()
        assertEquals(20, h.restores().single().target)
    }

    @Test fun `R2 drop older than W_BEFORE does not count, and the fallback refuses a tracker that saw the drop`() {
        val h = settled(20)
        h.change(6)
        h.advance(Windows.W_BEFORE + 100)
        h.notice()
        h.advance(Windows.W_AFTER)
        h.timers()
        assertTrue(h.restores().isEmpty())
        assertEquals("unknown_pre_drop", h.lastRecord().reason)
    }

    @Test fun `R2 drop later than W_AFTER is not restored`() {
        val h = settled(18)
        h.notice()
        h.advance(Windows.W_AFTER)
        h.timers()
        assertEquals("no_drop_seen", h.lastRecord().reason)
        h.advance(100)
        h.change(6)
        assertTrue(h.restores().isEmpty())
    }

    @Test fun `I3 a drop to RS1 with no notice is Phil's own and never restored`() {
        val h = settled(18)
        h.change(6)
        h.advance(10_000)
        assertTrue(h.actions.none { it is Action.Restore })
    }

    @Test fun `I4 a change after the drop means the user wins`() {
        val h = settled(18)
        h.change(6)
        h.advance(500)
        h.change(9)
        h.advance(200)
        h.notice()
        h.advance(Windows.W_AFTER)
        h.timers()
        assertTrue(h.restores().isEmpty())
        assertEquals("volume_changed_after_drop", h.lastRecord().reason)
    }

    @Test fun `R3a output switched after the drop is ambiguous`() {
        val h = settled(18)
        h.change(6)
        h.advance(300)
        h.switchTo(SHOKZ, 12)
        h.notice()
        assertTrue(h.restores().isEmpty())
        assertEquals("device_changed", h.lastRecord().reason)
    }

    @Test fun `R3a drop within SETTLE of an output change is ambiguous`() {
        val h = settled(18)
        h.switchTo(SHOKZ, 18)
        h.advance(Windows.SETTLE - 500)
        h.change(6)
        h.notice()
        assertEquals("device_changed", h.lastRecord().reason)
    }

    @Test fun `R3c excluded device is skipped`() {
        val h = settled(18)
        h.excluded += SONY.key
        h.change(6)
        h.notice()
        assertEquals("excluded", h.lastRecord().reason)
        assertTrue(h.restores().isEmpty())
    }

    @Test fun `R0 master switch off does nothing`() {
        val h = settled(18)
        h.enabled = false
        h.change(6)
        h.notice()
        assertEquals("disabled", h.lastRecord().reason)
        assertTrue(h.restores().isEmpty())
    }

    @Test fun `R0 stale notice is skipped`() {
        val h = settled(18)
        h.change(6)
        h.notice(postedAgo = Windows.STALE + 1)
        assertEquals("stale_notice", h.lastRecord().reason)
    }

    @Test fun `R0 duplicate notice is ignored`() {
        val h = settled(18)
        h.change(6)
        val n = CsdNotice("k", 42L, h.now, h.now, CsdKind.DOSE_1X, 1007, "ALR", "Volume lowered", null, emptyList(), false)
        assertTrue(h.engine.onCsdNotice(n, h.probe()).isNotEmpty())
        assertTrue(h.engine.onCsdNotice(n, h.probe()).isEmpty())
    }

    @Test fun `R0 no headphone output`() {
        val h = settled(18)
        h.switchTo(null, 7)
        h.advance(5_000)
        h.notice()
        h.advance(Windows.W_AFTER)
        h.timers()
        assertEquals("no_headphone_output", h.lastRecord().reason)
    }

    @Test fun `R5 missed drop broadcast falls back to the tracker`() {
        val h = settled(18)
        h.advance(60_000)
        h.silentChange(6)
        h.notice()
        h.advance(Windows.W_AFTER)
        h.timers()
        val r = h.restores().single()
        assertEquals(18, r.target)
        assertEquals("notif+tracker", h.records().last().signal)
    }

    @Test fun `R5 fallback uses a level read after an output change`() {
        val h = settled(10)
        h.switchTo(SHOKZ, 25)
        h.advance(Windows.DEVICE_READ_DELAY)
        h.read()
        h.advance(20_000)
        h.silentChange(6)
        h.notice()
        h.advance(Windows.W_AFTER)
        h.timers()
        assertEquals(Action.Restore(1, SHOKZ, 25), h.restores().single())
    }

    @Test fun `R5 fallback refuses a level observed before the device changed`() {
        val h = settled(18)
        h.switchTo(SHOKZ, 12)
        h.advance(5_000)
        h.switchTo(SONY, 14) // BT's remembered level, never read
        h.advance(5_000)
        h.silentChange(6)
        h.notice()
        h.advance(Windows.W_AFTER)
        h.timers()
        assertTrue(h.restores().isEmpty())
        assertEquals("device_changed", h.lastRecord().reason)
    }

    @Test fun `R5 fallback does nothing when the volume is not at RS1`() {
        val h = settled(18)
        h.silentChange(5)
        h.notice()
        h.advance(Windows.W_AFTER)
        h.timers()
        assertEquals("below_rs1", h.lastRecord().reason)
    }

    @Test fun `R6 our own restore broadcast is tagged SELF and not treated as a change`() {
        val h = settled(18)
        h.change(6)
        h.notice()
        h.applyRestores()
        assertEquals(Source.SELF, h.engine.tracker[SONY.key]?.source)
        assertEquals(18, h.engine.tracker[SONY.key]?.level)
    }

    @Test fun `R6 a restore Android ignores is recorded as FAILED`() {
        val h = settled(18)
        h.change(6)
        h.notice()
        h.applyRestores(blocked = true)
        val d = h.lastRecord()
        assertEquals(Outcome.FAILED, d.outcome)
        assertEquals("not_applied", d.reason)
    }

    @Test fun `R7 the 5x second lowering is re-restored exactly once`() {
        val h = settled(20)
        h.change(6)
        h.advance(100)
        h.notice(kind = CsdKind.DOSE_5X)
        h.applyRestores()
        h.advance(200)
        h.change(6) // SystemUI's own lowerVolumeToRs1() landing after our restore
        assertEquals(listOf(20, 20), h.restores().map { it.target })
        assertEquals("echo", h.records().last().signal)
        h.applyRestores()
        h.advance(200)
        h.change(6)
        assertEquals(2, h.restores().size)
    }

    @Test fun `R7 a user change after a restore ends the echo watch`() {
        val h = settled(20)
        h.change(6)
        h.notice()
        h.applyRestores()
        h.advance(200)
        h.change(15)
        h.advance(200)
        h.change(6)
        assertEquals(1, h.restores().size)
    }

    @Test fun `R7 no echo after the window`() {
        val h = settled(20)
        h.change(6)
        h.notice()
        h.applyRestores()
        h.advance(Windows.ECHO + 1)
        h.change(6)
        assertEquals(1, h.restores().size)
    }

    @Test fun `R8 circuit breaker pauses after three restores in ten minutes`() {
        val h = settled(18)
        repeat(4) {
            h.change(18)
            h.advance(10_000)
            h.change(6)
            h.notice()
            h.applyRestores()
            h.advance(60_000)
        }
        assertEquals(3, h.restores().size)
        assertEquals("circuit_breaker", h.records().last { it.outcome == Outcome.SKIPPED }.reason)
        h.advance(Windows.BREAKER_PAUSE)
        h.change(18)
        h.advance(10_000)
        h.change(6)
        h.notice()
        assertEquals(4, h.restores().size)
    }

    @Test fun `RS1 is 10 on wired outputs`() {
        val h = Harness().apply { device = WIRED; volume = 12; change(18); advance(30_000) }
        h.change(10)
        h.notice()
        assertEquals(18, h.restores().single().target)
        val h2 = Harness().apply { device = WIRED; change(18); advance(30_000) }
        h2.change(6)
        h2.notice()
        h2.advance(Windows.W_AFTER)
        h2.timers()
        assertTrue(h2.restores().isEmpty())
    }

    @Test fun `a second notice supersedes an undecided first one`() {
        val h = settled(18)
        h.notice()
        h.advance(100)
        h.notice()
        assertEquals("superseded", h.records().first().reason)
        h.change(6)
        assertEquals(18, h.restores().single().target)
    }

    @Test fun `skipped decisions carry no restore target`() {
        val h = settled(18)
        h.change(6)
        h.advance(300)
        h.switchTo(SHOKZ, 12)
        h.notice()
        assertNull(h.lastRecord().restored)
    }
}
