import { useState } from "react"
import { Alert, FlatList, StyleSheet, Text, TouchableOpacity, View } from "react-native"
import VolumeKeeper from "../../modules/volume-keeper"
import { Muted, Screen, Title } from "../../components/ui"
import { EventRow } from "../../components/EventRow"
import { useNative, useNow } from "../../hooks/useVolumeKeeper"
import { colors, font, radius, spacing } from "../../lib/theme"

type Filter = "lowerings" | "all"

export default function LogScreen() {
  const now = useNow()
  const [filter, setFilter] = useState<Filter>("lowerings")
  const { data, reload } = useNative(() => VolumeKeeper.getEvents(500), ["event"])
  const rows = (data ?? []).filter((e) => filter === "all" || e.kind === "csd" || e.kind === "near_miss" || e.kind === "test")

  const clear = () =>
    Alert.alert("Clear the log?", "Device levels are kept.", [
      { text: "Cancel", style: "cancel" },
      { text: "Clear", style: "destructive", onPress: () => VolumeKeeper.clearEvents().then(reload) },
    ])

  return (
    <Screen>
      <FlatList
        contentContainerStyle={styles.body}
        data={rows}
        keyExtractor={(e) => String(e.id)}
        ListHeaderComponent={
          <View style={styles.header}>
            <View style={styles.titleRow}>
              <Title>Log</Title>
              <TouchableOpacity onPress={clear} accessibilityRole="button" accessibilityLabel="Clear log">
                <Text style={styles.clear}>Clear</Text>
              </TouchableOpacity>
            </View>
            <View style={styles.chips}>
              {(["lowerings", "all"] as const).map((f) => (
                <TouchableOpacity
                  key={f}
                  onPress={() => setFilter(f)}
                  style={[styles.chip, filter === f && styles.chipOn]}
                  accessibilityRole="button"
                  accessibilityState={{ selected: filter === f }}
                >
                  <Text style={[styles.chipText, filter === f && styles.chipTextOn]}>{f === "lowerings" ? "Lowerings" : "Everything"}</Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        }
        ListEmptyComponent={<Muted>Nothing yet. Every lowering Android makes shows up here, restored or not, with the reason.</Muted>}
        renderItem={({ item }) => <EventRow event={item} now={now} />}
      />
    </Screen>
  )
}

const styles = StyleSheet.create({
  body: { padding: spacing.lg, paddingBottom: spacing.xxxl },
  header: { marginBottom: spacing.sm },
  titleRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  clear: { color: colors.danger, fontSize: font.sm, fontWeight: "600" },
  chips: { flexDirection: "row", marginTop: spacing.md, height: 34 },
  chip: { paddingHorizontal: spacing.md, paddingVertical: 6, borderRadius: radius.pill, borderWidth: 1, borderColor: colors.borderStrong, marginRight: spacing.sm },
  chipOn: { backgroundColor: colors.accent, borderColor: colors.accent },
  chipText: { color: colors.textSecondary, fontSize: font.sm, fontWeight: "600" },
  chipTextOn: { color: colors.text },
})
