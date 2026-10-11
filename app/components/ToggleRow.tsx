import { StyleSheet, Switch, Text, View } from "react-native"
import { colors, font, spacing } from "../lib/theme"

export function ToggleRow({ label, detail, value, onChange, disabled }: {
  label: string
  detail?: string
  value: boolean
  onChange: (v: boolean) => void
  disabled?: boolean
}) {
  return (
    <View style={styles.row}>
      <View style={styles.text}>
        <Text style={styles.label}>{label}</Text>
        {detail ? <Text style={styles.detail}>{detail}</Text> : null}
      </View>
      <Switch
        value={value}
        onValueChange={onChange}
        disabled={disabled}
        accessibilityLabel={label}
        trackColor={{ false: colors.borderStrong, true: colors.accentDim }}
        thumbColor={value ? colors.accent : colors.textSecondary}
      />
    </View>
  )
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", paddingVertical: spacing.sm },
  text: { flex: 1, marginRight: spacing.md },
  label: { color: colors.text, fontSize: font.md, fontWeight: "600" },
  detail: { color: colors.textSecondary, fontSize: font.sm, marginTop: 2 },
})
