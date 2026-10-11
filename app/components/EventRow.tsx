import { useState } from "react"
import { StyleSheet, Text, TouchableOpacity, View } from "react-native"
import { Ionicons } from "@expo/vector-icons"
import type { EventRow as Row } from "../modules/volume-keeper"
import { clock, describeEvent, type Tone } from "../lib/format"
import { colors, font, spacing } from "../lib/theme"

export const toneColor: Record<Tone, string> = {
  success: colors.success,
  danger: colors.danger,
  warning: colors.warning,
  muted: colors.textMuted,
  accent: colors.accent,
}

function prettyDetail(detail: string | null): string | null {
  if (!detail) return null
  try {
    return JSON.stringify(JSON.parse(detail), null, 2)
  } catch {
    return detail
  }
}

/** One log line; tap to show the raw notification fields. */
export function EventRow({ event, now }: { event: Row; now: number }) {
  const [open, setOpen] = useState(false)
  const v = describeEvent(event)
  const detail = prettyDetail(event.detail)
  return (
    <TouchableOpacity activeOpacity={0.8} onPress={() => setOpen((o) => !o)} disabled={!detail} style={styles.row}>
      <Ionicons name={v.icon as keyof typeof Ionicons.glyphMap} size={20} color={toneColor[v.tone]} style={styles.icon} />
      <View style={styles.body}>
        <View style={styles.top}>
          <Text style={styles.title} numberOfLines={2}>{v.title}</Text>
          <Text style={styles.time}>{clock(event.at, now)}</Text>
        </View>
        {v.subtitle ? <Text style={styles.sub}>{v.subtitle}</Text> : null}
        {open && detail ? <Text style={styles.detail} selectable>{detail}</Text> : null}
      </View>
    </TouchableOpacity>
  )
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", paddingVertical: spacing.md, borderBottomWidth: 1, borderBottomColor: colors.border },
  icon: { marginRight: spacing.md, marginTop: 1 },
  body: { flex: 1 },
  top: { flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start" },
  title: { color: colors.text, fontSize: font.md, fontWeight: "600", flex: 1, marginRight: spacing.sm },
  time: { color: colors.textMuted, fontSize: font.xs, marginTop: 2 },
  sub: { color: colors.textSecondary, fontSize: font.sm, marginTop: 2 },
  detail: { color: colors.textSecondary, fontSize: font.xs, marginTop: spacing.sm, fontFamily: "monospace" },
})
