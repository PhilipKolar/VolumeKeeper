import { useCallback } from "react"
import { BackHandler } from "react-native"
import { useFocusEffect } from "expo-router"

/**
 * Hardware back button for ONE screen.
 *
 * The listener is added on focus and removed on blur, so a screen pushed on top
 * of this one gets the back press instead. A plain `useEffect` registration in a
 * layout survives navigation and hijacks back on every pushed screen — that is
 * what once made the exercise editor close the app.
 *
 * Return `true` from `onBack` to consume the press, `false` to let navigation
 * handle it (pop the screen, switch to the first tab, or leave the app).
 * Memoise `onBack` with `useCallback` so the listener is not re-added every render.
 */
export function useBackHandler(onBack: () => boolean): void {
  useFocusEffect(
    useCallback(() => {
      const sub = BackHandler.addEventListener("hardwareBackPress", onBack)
      return () => sub.remove()
    }, [onBack]),
  )
}
