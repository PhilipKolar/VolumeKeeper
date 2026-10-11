import { useCallback, useEffect, useRef, useState } from "react"
import { AppState } from "react-native"
import { useFocusEffect } from "expo-router"
import VolumeKeeper, { type ChangeEvent } from "../modules/volume-keeper"

/**
 * Loads something from the native module and reloads it when the runtime reports a change
 * (onChange), when the screen regains focus, and when the app returns to the foreground (a
 * permission granted in Settings shows up immediately).
 */
export function useNative<T>(load: () => Promise<T>, whats: ChangeEvent["what"][]): { data: T | null; reload: () => void } {
  const [data, setData] = useState<T | null>(null)
  const loadRef = useRef(load)
  loadRef.current = load
  const reload = useCallback(() => {
    loadRef.current().then(setData).catch(() => {})
  }, [])
  const key = whats.join(",")

  useEffect(() => {
    const sub = VolumeKeeper.addListener("onChange", (e) => {
      if (key.split(",").includes(e.what)) reload()
    })
    const app = AppState.addEventListener("change", (s) => { if (s === "active") reload() })
    return () => {
      sub.remove()
      app.remove()
    }
  }, [key, reload])

  useFocusEffect(useCallback(() => { reload() }, [reload]))
  return { data, reload }
}

/** A ticking clock for "2 min ago" labels. */
export function useNow(ms = 15_000): number {
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), ms)
    return () => clearInterval(id)
  }, [ms])
  return now
}
