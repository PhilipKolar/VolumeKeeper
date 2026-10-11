import { test } from "node:test"
import assert from "node:assert/strict"
import { ago, checklist, clock, describeEvent, fraction, health, level, reasonLabel } from "../format"
import type { EventRow, Status } from "../../modules/volume-keeper/src/VolumeKeeper.types"

const base: EventRow = {
  id: 1, at: 0, kind: "csd", outcome: "RESTORED", reason: null, csd_kind: "DOSE_1X",
  device_key: "bt:C1:2E", device_name: "WH-1000XM5", before_level: 18, lowered: 6, restored: 18,
  signal: "notif+broadcast", latency_ms: 312.4, notice_lag_ms: 40, simulated: 0, detail: null,
}

const status: Status = {
  packageName: "com.philkolar.volumekeeper", isDevVariant: false, simulationEnabled: false,
  listenerGranted: true, listenerConnected: true, lastListenerConnect: 0, lastListenerDisconnect: null,
  btPermission: true, notificationsAllowed: true, batteryUnrestricted: true, keepAliveRunning: false,
  settings: { enabled: true, keepAlive: false, notifyOnRestore: false, notifyOnFailure: true, dismissSystemNotice: false, treatSpeakerAsHeadphones: false },
  device: { key: "bt:C1:2E", name: "WH-1000XM5", type: "BT_A2DP", rs1: 6 }, volume: 18, maxVolume: 25, importance: 125,
}

test("restored event reads before → after with signal and latency", () => {
  const v = describeEvent(base)
  assert.equal(v.title, "WH-1000XM5  6 → 18")
  assert.equal(v.subtitle, "1× dose · notif+broadcast · 312 ms")
  assert.equal(v.tone, "success")
})

test("skipped event leads with the reason", () => {
  const v = describeEvent({ ...base, outcome: "SKIPPED", reason: "volume_changed_after_drop", restored: null })
  assert.equal(v.title, "Left alone: You changed the volume after the drop")
  assert.equal(v.tone, "muted")
})

test("failed event is red", () => {
  assert.equal(describeEvent({ ...base, outcome: "FAILED", reason: "not_applied" }).tone, "danger")
})

test("simulated events say so", () => {
  assert.match(describeEvent({ ...base, simulated: 1 }).subtitle, /^simulated · /)
})

test("unknown reasons fall back to words", () => {
  assert.equal(reasonLabel("some_new_reason"), "some new reason")
  assert.equal(reasonLabel(null), "")
})

test("levels and fractions", () => {
  assert.equal(level(18, 25), "18/25")
  assert.equal(level(null, 25), "–")
  assert.equal(fraction(30, 25), 1)
  assert.equal(fraction(5, 0), 0)
})

test("clock and ago", () => {
  const now = new Date(2026, 9, 12, 15, 0, 0).getTime()
  assert.equal(clock(new Date(2026, 9, 12, 14, 3, 21).getTime(), now), "14:03:21")
  assert.equal(clock(new Date(2026, 9, 10, 9, 5).getTime(), now), "Sat 09:05")
  assert.equal(clock(new Date(2026, 8, 1, 9, 5).getTime(), now), "01/09 09:05")
  assert.equal(ago(now - 30_000, now), "just now")
  assert.equal(ago(now - 5 * 60_000, now), "5 min ago")
  assert.equal(ago(null, now), "never")
})

test("checklist offers fixes only where they make sense", () => {
  const items = checklist({ ...status, listenerGranted: false, listenerConnected: false })
  assert.equal(items.find((i) => i.key === "listener")?.ok, false)
  assert.equal(items.find((i) => i.key === "connected")?.fix, null)
  assert.ok(items.filter((i) => i.key !== "listener" && i.key !== "connected").every((i) => i.ok))
})

test("health headline", () => {
  assert.equal(health(status).headline, "Active · WH-1000XM5")
  assert.equal(health({ ...status, device: null }).headline, "Active · not on headphones")
  assert.equal(health({ ...status, settings: { ...status.settings, enabled: false } }).tone, "muted")
  assert.equal(health({ ...status, listenerGranted: false }).tone, "danger")
})
