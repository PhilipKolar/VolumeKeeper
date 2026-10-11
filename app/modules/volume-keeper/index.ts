import { NativeModule, requireNativeModule } from "expo"
import type {
  ChangeEvent, DeviceRow, EventRow, SettingName, SimulateOrder, Status,
} from "./src/VolumeKeeper.types"

export * from "./src/VolumeKeeper.types"

type Events = { onChange: (e: ChangeEvent) => void }

declare class VolumeKeeperNative extends NativeModule<Events> {
  getStatus(): Promise<Status>
  getDevices(): Promise<DeviceRow[]>
  getEvents(limit: number): Promise<EventRow[]>
  getTrace(): Promise<string[]>
  clearEvents(): Promise<void>
  setSetting(name: SettingName, value: boolean): Promise<boolean>
  setDeviceExcluded(key: string, excluded: boolean): Promise<void>
  forgetDevice(key: string): Promise<void>
  requestRebind(): Promise<void>
  openListenerSettings(): Promise<void>
  openBatterySettings(): Promise<void>
  openAppSettings(): Promise<void>
  simulate(order: SimulateOrder, gapMs: number, from: number | null, fiveX: boolean): Promise<void>
}

export default requireNativeModule<VolumeKeeperNative>("VolumeKeeper")
