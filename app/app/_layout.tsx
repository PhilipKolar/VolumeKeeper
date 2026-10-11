import { Alert } from "react-native"
import { Stack } from "expo-router"
import { StatusBar } from "expo-status-bar"
import { SafeAreaProvider } from "react-native-safe-area-context"
import { colors } from "../lib/theme"

// Release builds crash silently on uncaught JS errors; surface them as an Alert so a sideloaded
// APK can be diagnosed without adb (from PKTrainer).
;(function installCrashDiagnostics() {
  const GErrorUtils = (global as { ErrorUtils?: { setGlobalHandler: (h: (e: Error, fatal: boolean) => void) => void; getGlobalHandler: () => ((e: Error, fatal: boolean) => void) | null } }).ErrorUtils
  if (!GErrorUtils) return
  const prev = GErrorUtils.getGlobalHandler()
  GErrorUtils.setGlobalHandler((error: Error, isFatal: boolean) => {
    Alert.alert(
      isFatal ? "Fatal JS error" : "JS error",
      `${error?.message ?? String(error)}\n\n${(error?.stack ?? "").split("\n").slice(0, 6).join("\n")}`,
      [{ text: "OK" }],
    )
    prev?.(error, isFatal)
  })
})()

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <StatusBar style="light" />
      <Stack screenOptions={{ headerShown: false, animation: "slide_from_right", contentStyle: { backgroundColor: colors.bg } }} />
    </SafeAreaProvider>
  )
}
