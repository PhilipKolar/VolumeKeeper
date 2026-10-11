import { Alert, FlatList, StyleSheet, Text, TouchableOpacity, View } from "react-native"
import VolumeKeeper, { type DeviceRow } from "../../modules/volume-keeper"
import { Card, Muted, Screen, Title } from "../../components/ui"
import { LevelBar } from "../../components/LevelBar"
import { ToggleRow } from "../../components/ToggleRow"
import { useNative, useNow } from "../../hooks/useVolumeKeeper"
import { ago, clock, level } from "../../lib/format"
import { colors, font, spacing } from "../../lib/theme"

const TYPE_LABEL: Record<string, string> = {
  BT_A2DP: "Bluetooth",
  BLE_HEADSET: "LE Audio",
  BLE_BROADCAST: "LE Audio broadcast",
  USB_HEADSET: "USB headset",
  USB_DEVICE: "USB audio",
  WIRED_HEADSET: "Wired headset",
  WIRED_HEADPHONES: "Wired headphones",
  SPEAKER_TEST: "Speaker (test)",
}

function DeviceCard({ d, now, onChange }: { d: DeviceRow; now: number; onChange: () => void }) {
  const restored = d.restore_count > 0
    ? `Restored ${d.restore_count}× · last ${clock(d.last_restore_at ?? 0, now)} (${d.last_restore_from} → ${d.last_restore_to})`
    : "Never restored"
  const forget = () =>
    Alert.alert("Forget this device?", `${d.name} disappears from the list until it plays again.`, [
      { text: "Cancel", style: "cancel" },
      { text: "Forget", style: "destructive", onPress: () => VolumeKeeper.forgetDevice(d.key).then(onChange) },
    ])
  return (
    <Card style={styles.card}>
      <View style={styles.top}>
        <View style={styles.flex}>
          <Text style={styles.name}>{d.name}</Text>
          <Muted>{TYPE_LABEL[d.type] ?? d.type} · seen {ago(d.last_seen_at, now)}</Muted>
        </View>
        <Text style={styles.level}>{level(d.last_level, d.max_level)}</Text>
      </View>
      <LevelBar value={d.last_level} max={d.max_level} tone={d.excluded ? "muted" : "accent"} />
      <Muted style={styles.meta}>{restored}</Muted>
      <ToggleRow
        label="Restore this device"
        value={!d.excluded}
        onChange={(v) => VolumeKeeper.setDeviceExcluded(d.key, !v).then(onChange)}
      />
      <TouchableOpacity onPress={forget} accessibilityRole="button" accessibilityLabel={`Forget ${d.name}`}>
        <Text style={styles.forget}>Forget</Text>
      </TouchableOpacity>
    </Card>
  )
}

export default function DevicesScreen() {
  const now = useNow()
  const { data, reload } = useNative(() => VolumeKeeper.getDevices(), ["device"])
  return (
    <Screen>
      <FlatList
        contentContainerStyle={styles.body}
        data={data ?? []}
        keyExtractor={(d) => d.key}
        ListHeaderComponent={
          <View style={styles.header}>
            <Title>Devices</Title>
            <Muted style={styles.sub}>The last level each output played at. Android keeps one level for all Bluetooth headsets; VolumeKeeper tells them apart.</Muted>
          </View>
        }
        ListEmptyComponent={<Muted>No headphones seen yet. Play something on a headset.</Muted>}
        renderItem={({ item }) => <DeviceCard d={item} now={now} onChange={reload} />}
      />
    </Screen>
  )
}

const styles = StyleSheet.create({
  body: { padding: spacing.lg, paddingBottom: spacing.xxxl },
  header: { marginBottom: spacing.md },
  sub: { marginTop: spacing.xs },
  card: { marginBottom: spacing.md },
  top: { flexDirection: "row", alignItems: "center", marginBottom: spacing.sm },
  flex: { flex: 1 },
  name: { color: colors.text, fontSize: font.lg, fontWeight: "700" },
  level: { color: colors.accent, fontSize: font.xl, fontWeight: "800", marginLeft: spacing.md },
  meta: { marginTop: spacing.sm },
  forget: { color: colors.danger, fontSize: font.sm, fontWeight: "600", marginTop: spacing.xs },
})
