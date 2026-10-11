package com.philkolar.volumekeeper.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Random phones, random Phils, random Androids. Whatever happens, every restore must satisfy
 * I1 (target previously observed for that device), I2 (RS1 < target <= max), I3 (a notice came
 * first) and raise only from RS1.
 */
class InvariantPropertyTest {
    private val devices = listOf(SONY, SHOKZ, WIRED, null)

    @Test fun `invariants hold over random event sequences`() {
        var restoresSeen = 0
        repeat(3_000) { seed ->
            val rnd = Random(seed)
            val h = Harness()
            val levels = HashMap<String?, Int>()
            val observed = HashMap<String, MutableSet<Int>>()
            var noticeSeen = false
            fun observe(d: Device?, vararg v: Int) { d?.let { observed.getOrPut(it.key) { mutableSetOf() } += v.toList() } }

            fun checkNew(before: Int) {
                for (r in h.restores().drop(before)) {
                    restoresSeen++
                    val rs1 = Rs1.of(r.device.type)
                    val obs = observed[r.device.key] ?: emptySet()
                    if (r.target !in obs) fail("seed $seed: I1 target ${r.target} never observed for ${r.device.key} ($obs)")
                    if (r.target <= rs1 || r.target > h.max) fail("seed $seed: I2 target ${r.target}")
                    if (!noticeSeen) fail("seed $seed: I3 restore without a notice")
                    if (h.volume != rs1) fail("seed $seed: raised from ${h.volume}, not RS1 $rs1")
                }
            }

            repeat(150) {
                val before = h.restores().size
                when (rnd.nextInt(10)) {
                    0, 1 -> { // Phil moves the volume
                        val prev = h.volume
                        val to = rnd.nextInt(0, h.max + 1)
                        if (rnd.nextDouble() < 0.9) { h.change(to); observe(h.device, prev, to) } else h.silentChange(to)
                    }
                    2, 3 -> { // Android lowers to RS1, notice before or after, sometimes lost
                        val d = h.device
                        val rs1 = d?.let { Rs1.of(it.type) } ?: 6
                        val noticeFirst = rnd.nextBoolean()
                        val gap = rnd.nextLong(0, 3_000)
                        if (noticeFirst && rnd.nextDouble() < 0.9) { h.notice(); noticeSeen = true }
                        h.advance(if (noticeFirst) gap else 0)
                        if (h.volume > rs1) {
                            val prev = h.volume
                            if (rnd.nextDouble() < 0.9) { h.change(rs1); observe(d, prev, rs1) } else h.silentChange(rs1)
                        }
                        if (!noticeFirst) { h.advance(gap); if (rnd.nextDouble() < 0.9) { h.notice(); noticeSeen = true } }
                    }
                    4 -> { h.notice(); noticeSeen = true } // notice with nothing to lower
                    5 -> { // output change; sometimes the settle read happens
                        levels[h.device?.key] = h.volume
                        val d = devices[rnd.nextInt(devices.size)]
                        val lvl = levels[d?.key] ?: rnd.nextInt(0, h.max + 1)
                        h.switchTo(d, lvl)
                        if (rnd.nextDouble() < 0.8) { h.advance(Windows.DEVICE_READ_DELAY); h.read(); observe(d, lvl) }
                    }
                    6 -> h.excluded.apply { if (rnd.nextBoolean()) add(SONY.key) else clear() }
                    else -> h.advance(rnd.nextLong(0, 20_000))
                }
                checkNew(before)
                val b2 = h.restores().size
                h.timers()
                checkNew(b2)
                h.actions.removeAll { it is Action.Schedule && it.at <= h.now }
                val b3 = h.restores().size
                h.applyRestores(blocked = rnd.nextDouble() < 0.05)
                observe(h.device, h.volume)
                checkNew(b3)
            }
        }
        assertTrue(restoresSeen > 100, "the generator should exercise restores ($restoresSeen)")
    }
}
