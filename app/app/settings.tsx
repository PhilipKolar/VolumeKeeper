import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native"
import { useRouter } from "expo-router"
import { Ionicons } from "@expo/vector-icons"
import Constants from "expo-constants"
import VolumeKeeper, { type SettingName, type SimulateOrder } from "../modules/volume-keeper"
import { Button, Card, Label, Muted, Screen, Title } from "../components/ui"
import { ToggleRow } from "../components/ToggleRow"
import { useNative } from "../hooks/useVolumeKeeper"
import { colors, font, spacing } from "../lib/theme"

const SIMS: { label: string; order: SimulateOrder; gap: number }[] = [
  { label: "Drop, then notice (300 ms)", order: "drop-first", gap: 300 },
  { label: "Notice, then drop (300 ms)", order: "notif-first", gap: 300 },
  { label: "Drop only (must NOT restore)", order: "drop-only", gap: 0 },
]

export default function SettingsScreen() {
  const router = useRouter()
  const { data: status, reload } = useNative(() => VolumeKeeper.getStatus(), ["status"])
  const s = status?.settings
  const set = (name: SettingName) => async (v: boolean) => { await VolumeKeeper.setSetting(name, v); reload() }

  return (
    <Screen>
      <ScrollView contentContainerStyle={styles.body}>
        <View style={styles.header}>
          <TouchableOpacity onPress={() => router.back()} accessibilityRole="button" accessibilityLabel="Back" hitSlop={12}>
            <Ionicons name="chevron-back" size={24} color={colors.text} />
          </TouchableOpacity>
          <Title style={styles.title}>Settings</Title>
        </View>

        <Card>
          <ToggleRow
            label="Keep-alive service"
            detail={`A silent ongoing notification that keeps VolumeKeeper from ever being frozen. Only needed if a restore fails.${status?.keepAliveRunning ? " Running." : ""}`}
            value={s?.keepAlive ?? false}
            onChange={set("keepAlive")}
            disabled={!s}
          />
          <ToggleRow label="Tell me when it restores" detail="A quiet notification each time." value={s?.notifyOnRestore ?? false} onChange={set("notifyOnRestore")} disabled={!s} />
          <ToggleRow label="Tell me when it can't" detail="Android ignored the restore, or the pre-drop level was unknown." value={s?.notifyOnFailure ?? true} onChange={set("notifyOnFailure")} disabled={!s} />
          <ToggleRow label="Dismiss Android's notice" detail={"Clear \"Volume lowered to safer level\" once the volume is back."} value={s?.dismissSystemNotice ?? false} onChange={set("dismissSystemNotice")} disabled={!s} />
        </Card>

        {status?.simulationEnabled ? (
          <Card style={styles.card}>
            <Label>Developer (dev build only)</Label>
            <ToggleRow
              label="Treat the speaker as headphones"
              detail="Lets simulations run without a headset (RS1 6)."
              value={s?.treatSpeakerAsHeadphones ?? false}
              onChange={set("treatSpeakerAsHeadphones")}
            />
            {SIMS.map((sim) => (
              <Button
                key={sim.label}
                title={sim.label}
                variant="outline"
                style={styles.simButton}
                onPress={() => VolumeKeeper.simulate(sim.order, sim.gap, 18, false)}
              />
            ))}
            <Muted style={styles.note}>Each simulation first sets 18, waits 1.5 s, then lowers to RS1 and/or posts a look-alike notice.</Muted>
            <Button title="Live trace" variant="ghost" style={styles.simButton} onPress={() => router.push("/debug")} />
          </Card>
        ) : null}

        <View style={styles.about}>
          <Text style={styles.version}>VolumeKeeper {Constants.expoConfig?.version ?? ""}</Text>
          <Muted>{status?.packageName}</Muted>
        </View>
      </ScrollView>
    </Screen>
  )
}

const styles = StyleSheet.create({
  body: { padding: spacing.lg, paddingBottom: spacing.xxxl },
  header: { flexDirection: "row", alignItems: "center", marginBottom: spacing.lg },
  title: { marginLeft: spacing.sm },
  card: { marginTop: spacing.md },
  simButton: { marginTop: spacing.sm },
  note: { marginTop: spacing.sm },
  about: { alignItems: "center", marginTop: spacing.xl },
  version: { color: colors.textSecondary, fontSize: font.sm, fontWeight: "600" },
})
