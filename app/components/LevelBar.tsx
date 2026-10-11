import { StyleSheet, View } from "react-native"
import { fraction } from "../lib/format"
import { colors, radius } from "../lib/theme"

/** A horizontal level bar with an optional tick at the RS1 index Android lowers to. */
export function LevelBar({ value, max, rs1, tone = "accent" }: { value: number | null; max: number | null; rs1?: number; tone?: "accent" | "muted" }) {
  const f = fraction(value, max)
  return (
    <View style={styles.track} accessibilityRole="progressbar" accessibilityValue={{ min: 0, max: max ?? 0, now: value ?? 0 }}>
      <View style={[styles.fill, { width: `${f * 100}%`, backgroundColor: tone === "accent" ? colors.accent : colors.textMuted }]} />
      {rs1 != null && max ? <View style={[styles.tick, { left: `${fraction(rs1, max) * 100}%` }]} /> : null}
    </View>
  )
}

const styles = StyleSheet.create({
  track: { height: 8, borderRadius: radius.pill, backgroundColor: colors.surfaceRaised, overflow: "hidden" },
  fill: { height: 8, borderRadius: radius.pill },
  tick: { position: "absolute", top: 0, bottom: 0, width: 2, backgroundColor: colors.warning },
})
