import { PermissionsAndroid, ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native"
import { useRouter } from "expo-router"
import { Ionicons } from "@expo/vector-icons"
import VolumeKeeper from "../../modules/volume-keeper"
import { Card, Label, Muted, Screen, Title } from "../../components/ui"
import { LevelBar } from "../../components/LevelBar"
import { EventRow, toneColor } from "../../components/EventRow"
import { ToggleRow } from "../../components/ToggleRow"
import { useNative, useNow } from "../../hooks/useVolumeKeeper"
import { checklist, health, level, type CheckItem } from "../../lib/format"
import { colors, font, radius, spacing } from "../../lib/theme"

async function requestOrOpen(permission: (typeof PermissionsAndroid.PERMISSIONS)[keyof typeof PermissionsAndroid.PERMISSIONS]) {
  const r = await PermissionsAndroid.request(permission)
  if (r === PermissionsAndroid.RESULTS.NEVER_ASK_AGAIN) await VolumeKeeper.openAppSettings()
}

async function fix(item: CheckItem) {
  switch (item.key) {
    case "listener": return VolumeKeeper.openListenerSettings()
    case "connected": return VolumeKeeper.requestRebind()
    case "bluetooth": return requestOrOpen(PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT)
    case "battery": return VolumeKeeper.openBatterySettings()
    case "notifications": return requestOrOpen(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS)
  }
}

export default function StatusScreen() {
  const router = useRouter()
  const now = useNow()
  const { data: status, reload } = useNative(() => VolumeKeeper.getStatus(), ["status", "device"])
  const { data: events } = useNative(() => VolumeKeeper.getEvents(30), ["event"])
  const lastCsd = events?.find((e) => e.kind === "csd") ?? null

  const h = status ? health(status) : null
  const items = status ? checklist(status) : []

  return (
    <Screen>
      <ScrollView contentContainerStyle={styles.body}>
        <View style={styles.header}>
          <View style={styles.titleRow}>
            <Title>VolumeKeeper</Title>
            {status?.isDevVariant ? <Text style={styles.dev}>DEV</Text> : null}
          </View>
          <TouchableOpacity onPress={() => router.push("/settings")} accessibilityRole="button" accessibilityLabel="Settings" hitSlop={12}>
            <Ionicons name="settings-outline" size={22} color={colors.textSecondary} />
          </TouchableOpacity>
        </View>

        <Card>
          {h ? (
            <View style={styles.healthRow}>
              <View style={[styles.dot, { backgroundColor: toneColor[h.tone] }]} />
              <Text style={styles.health}>{h.headline}</Text>
            </View>
          ) : null}
          <ToggleRow
            label="Undo Android's volume lowering"
            detail="Puts each headset back to exactly the level it was at — never higher, never your own changes."
            value={status?.settings.enabled ?? false}
            onChange={async (v) => { await VolumeKeeper.setSetting("enabled", v); reload() }}
            disabled={!status}
          />
        </Card>

        <Card style={styles.card}>
          <Label>Now playing on</Label>
          {status?.device ? (
            <>
              <View style={styles.deviceRow}>
                <Text style={styles.deviceName}>{status.device.name}</Text>
                <Text style={styles.level}>{level(status.volume, status.maxVolume)}</Text>
              </View>
              <LevelBar value={status.volume} max={status.maxVolume} rs1={status.device.rs1} />
              <Muted style={styles.hint}>The yellow mark is where Android lowers it to ({status.device.rs1}).</Muted>
            </>
          ) : (
            <Muted style={styles.hint}>Not headphones — VolumeKeeper ignores the speaker and earpiece.</Muted>
          )}
        </Card>

        <Card style={styles.card}>
          <Label>Setup</Label>
          {items.map((item) => (
            <View key={item.key} style={styles.checkRow}>
              <Ionicons
                name={item.ok ? "checkmark-circle" : "alert-circle"}
                size={20}
                color={item.ok ? colors.success : colors.danger}
                style={styles.checkIcon}
              />
              <View style={styles.checkText}>
                <Text style={styles.checkLabel}>{item.label}</Text>
                <Muted>{item.detail}</Muted>
              </View>
              {!item.ok && item.fix ? (
                <TouchableOpacity style={styles.fix} onPress={() => fix(item).then(reload)} accessibilityRole="button" accessibilityLabel={`${item.fix} ${item.label}`}>
                  <Text style={styles.fixText}>{item.fix}</Text>
                </TouchableOpacity>
              ) : null}
            </View>
          ))}
        </Card>

        <Card style={styles.card}>
          <Label>Last lowering</Label>
          {lastCsd ? <EventRow event={lastCsd} now={now} /> : <Muted style={styles.hint}>None yet.</Muted>}
        </Card>

        <Muted style={styles.footer}>
          Keep MacroDroid's "volume lowered" macro off while VolumeKeeper is on — both reacting at once can jump straight to 100%.
        </Muted>
      </ScrollView>
    </Screen>
  )
}

const styles = StyleSheet.create({
  body: { padding: spacing.lg, paddingBottom: spacing.xxxl },
  header: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: spacing.lg },
  titleRow: { flexDirection: "row", alignItems: "center" },
  dev: { color: colors.bg, backgroundColor: colors.warning, fontSize: font.xs, fontWeight: "800", paddingHorizontal: 6, paddingVertical: 2, borderRadius: radius.sm, marginLeft: spacing.sm, overflow: "hidden" },
  healthRow: { flexDirection: "row", alignItems: "center", marginBottom: spacing.sm },
  dot: { width: 10, height: 10, borderRadius: 5, marginRight: spacing.sm },
  health: { color: colors.text, fontSize: font.lg, fontWeight: "700" },
  card: { marginTop: spacing.md },
  deviceRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "baseline", marginTop: spacing.sm, marginBottom: spacing.sm },
  deviceName: { color: colors.text, fontSize: font.lg, fontWeight: "700", flex: 1 },
  level: { color: colors.accent, fontSize: font.xl, fontWeight: "800" },
  hint: { marginTop: spacing.sm },
  checkRow: { flexDirection: "row", alignItems: "center", paddingVertical: spacing.sm },
  checkIcon: { marginRight: spacing.md },
  checkText: { flex: 1 },
  checkLabel: { color: colors.text, fontSize: font.md, fontWeight: "600" },
  fix: { backgroundColor: colors.accent, borderRadius: radius.md, paddingHorizontal: spacing.md, paddingVertical: spacing.sm, marginLeft: spacing.sm },
  fixText: { color: colors.text, fontWeight: "700", fontSize: font.sm },
  footer: { marginTop: spacing.lg, textAlign: "center" },
})
