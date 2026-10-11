// PURE (no RN imports): how events, levels and times read on screen. Unit-tested from Node.
import type { EventRow, Status } from "../modules/volume-keeper/src/VolumeKeeper.types"

export type Tone = "success" | "danger" | "warning" | "muted" | "accent"

const REASONS: Record<string, string> = {
  disabled: "Master switch was off",
  stale_notice: "Notification arrived too late to act on",
  superseded: "Replaced by a newer notification",
  no_headphone_output: "Not playing on headphones",
  device_changed: "Output changed around the drop",
  volume_changed_after_drop: "You changed the volume after the drop",
  excluded: "Device is excluded",
  invalid_target: "Pre-drop level out of range",
  circuit_breaker: "Paused: too many restores in 10 minutes",
  below_rs1: "Volume was already below the safe level",
  no_drop_seen: "No lowering seen (already at or below the safe level?)",
  unknown_pre_drop: "Didn't know the level before the drop",
  not_applied: "Android ignored the restore",
  changed_again_before_verify: "Restored, then changed again",
  device_changed_before_verify: "Output changed before the check",
  listener_connected: "Notification listener connected",
  listener_disconnected: "Notification listener disconnected",
  engine_started: "VolumeKeeper started",
  notice_delivery_lag: "Notification arrived late",
  keep_alive_start_refused: "Android refused to start the keep-alive service",
  applied: "Applied",
}

export function reasonLabel(reason: string | null | undefined): string {
  if (!reason) return ""
  if (REASONS[reason]) return REASONS[reason]
  if (reason.startsWith("setting ")) return reason.replace(/^setting /, "Setting ")
  return reason.replace(/_/g, " ")
}

export function level(value: number | null | undefined, max: number | null | undefined): string {
  if (value == null) return "–"
  return max ? `${value}/${max}` : String(value)
}

/** 0–1 fill for a level bar, clamped. */
export function fraction(value: number | null | undefined, max: number | null | undefined): number {
  if (value == null || !max || max <= 0) return 0
  return Math.max(0, Math.min(1, value / max))
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n)
}

const DAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]

/** "14:03:21" today, "Mon 14:03" this week, "12/10 14:03" otherwise (local time). */
export function clock(at: number, now: number): string {
  const d = new Date(at)
  const n = new Date(now)
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const sameDay = d.getFullYear() === n.getFullYear() && d.getMonth() === n.getMonth() && d.getDate() === n.getDate()
  if (sameDay) return `${hm}:${pad(d.getSeconds())}`
  if (now - at < 6 * 86_400_000 && now >= at) return `${DAYS[d.getDay()]} ${hm}`
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)} ${hm}`
}

export function ago(at: number | null | undefined, now: number): string {
  if (at == null) return "never"
  const s = Math.max(0, Math.round((now - at) / 1000))
  if (s < 60) return "just now"
  const m = Math.round(s / 60)
  if (m < 60) return `${m} min ago`
  const h = Math.round(m / 60)
  if (h < 48) return `${h} h ago`
  return `${Math.round(h / 24)} days ago`
}

export function csdKindLabel(kind: EventRow["csd_kind"]): string | null {
  if (kind === "DOSE_1X") return "1× dose"
  if (kind === "DOSE_5X") return "5× dose"
  if (kind === "UNKNOWN") return "CSD"
  return null
}

export type EventView = { title: string; subtitle: string; tone: Tone; icon: string }

export function describeEvent(e: EventRow): EventView {
  const device = e.device_name ?? "Headphones"
  const sim = e.simulated ? "simulated · " : ""
  switch (e.kind) {
    case "csd": {
      const parts = [csdKindLabel(e.csd_kind), e.signal, e.latency_ms != null ? `${Math.round(e.latency_ms)} ms` : null]
        .filter((p): p is string => !!p)
      const meta = sim + parts.join(" · ")
      switch (e.outcome) {
        case "RESTORED":
          return {
            title: `${device}  ${e.lowered ?? "?"} → ${e.restored ?? "?"}`,
            subtitle: e.reason ? `${meta} · ${reasonLabel(e.reason)}` : meta,
            tone: "success",
            icon: "checkmark-circle",
          }
        case "FAILED":
          return { title: `${device} not restored`, subtitle: `${reasonLabel(e.reason)} · ${meta}`, tone: "danger", icon: "close-circle" }
        case "PENDING":
          return { title: `${device} restoring to ${e.restored ?? "?"}…`, subtitle: meta, tone: "accent", icon: "time" }
        default:
          return {
            title: `Left alone: ${reasonLabel(e.reason)}`,
            subtitle: [sim + (csdKindLabel(e.csd_kind) ?? "CSD"), e.device_name, e.lowered != null ? `at ${e.lowered}` : null]
              .filter(Boolean).join(" · "),
            tone: "muted",
            icon: "remove-circle",
          }
      }
    }
    case "near_miss":
      return { title: "Unrecognised volume notification", subtitle: e.reason ?? "", tone: "warning", icon: "help-circle" }
    case "warning":
      return { title: reasonLabel(e.reason), subtitle: e.detail ?? "", tone: "warning", icon: "warning" }
    case "test":
      return {
        title: `Background volume test: ${reasonLabel(e.reason)}`,
        subtitle: e.detail ?? "",
        tone: e.reason === "applied" ? "success" : "danger",
        icon: "flask",
      }
    default:
      return { title: reasonLabel(e.reason), subtitle: e.device_name ?? "", tone: "muted", icon: "information-circle" }
  }
}

export type CheckItem = {
  key: "listener" | "connected" | "bluetooth" | "battery" | "notifications"
  label: string
  ok: boolean
  detail: string
  /** Shown only when not ok. */
  fix: string | null
}

export function checklist(s: Status): CheckItem[] {
  return [
    {
      key: "listener",
      label: "Notification access",
      ok: s.listenerGranted,
      detail: s.listenerGranted ? "Granted" : "Needed to see Android's \"Volume lowered\" notification",
      fix: "Grant",
    },
    {
      key: "connected",
      label: "Listener running",
      ok: s.listenerConnected,
      detail: s.listenerConnected ? "Connected" : s.listenerGranted ? "Granted but not connected" : "Waiting for access",
      fix: s.listenerGranted ? "Reconnect" : null,
    },
    {
      key: "bluetooth",
      label: "Nearby devices",
      ok: s.btPermission,
      detail: s.btPermission ? "Headsets are told apart by name" : "Needed to name your headsets",
      fix: "Allow",
    },
    {
      key: "battery",
      label: "Battery unrestricted",
      ok: s.batteryUnrestricted,
      detail: s.batteryUnrestricted ? "Android won't put VolumeKeeper to sleep" : "Keeps restores working in the background",
      fix: "Allow",
    },
    {
      key: "notifications",
      label: "Notifications",
      ok: s.notificationsAllowed,
      detail: s.notificationsAllowed ? "Allowed (for failure alerts)" : "Needed to tell you when a restore fails",
      fix: "Allow",
    },
  ]
}

export type Health = { tone: Tone; headline: string }

export function health(s: Status): Health {
  if (!s.settings.enabled) return { tone: "muted", headline: "Paused" }
  if (!s.listenerGranted) return { tone: "danger", headline: "Needs notification access" }
  if (!s.listenerConnected) return { tone: "warning", headline: "Listener not connected" }
  if (!s.device) return { tone: "accent", headline: "Active · not on headphones" }
  return { tone: "success", headline: `Active · ${s.device.name}` }
}
