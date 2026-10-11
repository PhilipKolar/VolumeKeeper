// Shapes returned by the native module (android/.../VolumeKeeperModule.kt). Numbers arrive as JS
// numbers; SQLite integers are sent as doubles. Times are wall-clock epoch ms.

export type SettingName =
  | "enabled"
  | "keepAlive"
  | "notifyOnRestore"
  | "notifyOnFailure"
  | "dismissSystemNotice"
  | "treatSpeakerAsHeadphones"

export type Settings = Record<SettingName, boolean>

export type OutputDevice = { key: string; name: string; type: string; rs1: number }

export type Status = {
  packageName: string
  isDevVariant: boolean
  simulationEnabled: boolean
  listenerGranted: boolean
  listenerConnected: boolean
  lastListenerConnect: number | null
  lastListenerDisconnect: number | null
  btPermission: boolean
  notificationsAllowed: boolean
  batteryUnrestricted: boolean
  keepAliveRunning: boolean
  settings: Settings
  device: OutputDevice | null
  volume: number
  maxVolume: number
  /** ActivityManager.RunningAppProcessInfo importance of this process. */
  importance: number
}

export type DeviceRow = {
  key: string
  type: string
  name: string
  last_level: number | null
  max_level: number | null
  last_level_at: number | null
  last_level_source: string | null
  excluded: number
  restore_count: number
  last_restore_at: number | null
  last_restore_from: number | null
  last_restore_to: number | null
  first_seen_at: number
  last_seen_at: number
}

export type EventKind = "csd" | "near_miss" | "service" | "warning" | "test"
export type Outcome = "PENDING" | "RESTORED" | "FAILED" | "SKIPPED"

export type EventRow = {
  id: number
  at: number
  kind: EventKind
  outcome: Outcome | null
  reason: string | null
  csd_kind: "DOSE_1X" | "DOSE_5X" | "UNKNOWN" | null
  device_key: string | null
  device_name: string | null
  before_level: number | null
  lowered: number | null
  restored: number | null
  signal: string | null
  latency_ms: number | null
  notice_lag_ms: number | null
  simulated: number
  detail: string | null
}

export type ChangeEvent = { what: "status" | "device" | "event" }

export type SimulateOrder = "drop-first" | "notif-first" | "drop-only" | "notif-only"
