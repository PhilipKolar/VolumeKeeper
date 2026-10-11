// Small shared primitives so screens stay free of raw colours/sizes.
import type { ReactNode } from "react"
import { View, Text, StyleSheet, ActivityIndicator, type ViewStyle, type TextStyle } from "react-native"
import { SafeAreaView } from "react-native-safe-area-context"
import { PressableScale } from "./Motion"
import { colors, spacing, radius, font } from "../lib/theme"

export function Screen({ children, style, edges }: { children: ReactNode; style?: ViewStyle; edges?: ("top" | "bottom" | "left" | "right")[] }) {
  return (
    <SafeAreaView style={[styles.screen, style]} edges={edges ?? ["top", "left", "right"]}>
      {children}
    </SafeAreaView>
  )
}

export function Title({ children, style }: { children: ReactNode; style?: TextStyle }) {
  return <Text style={[styles.title, style]}>{children}</Text>
}

export function Subtitle({ children, style }: { children: ReactNode; style?: TextStyle }) {
  return <Text style={[styles.subtitle, style]}>{children}</Text>
}

export function Muted({ children, style }: { children: ReactNode; style?: TextStyle }) {
  return <Text style={[styles.muted, style]}>{children}</Text>
}

export function Label({ children, style }: { children: ReactNode; style?: TextStyle }) {
  return <Text style={[styles.label, style]}>{children}</Text>
}

export function Card({ children, style }: { children: ReactNode; style?: ViewStyle }) {
  return <View style={[styles.card, style]}>{children}</View>
}

export function Row({ children, style }: { children: ReactNode; style?: ViewStyle }) {
  return <View style={[styles.row, style]}>{children}</View>
}

type ButtonVariant = "primary" | "outline" | "danger" | "ghost" | "success"

export function Button({
  title, onPress, variant = "primary", disabled, loading, style, textStyle,
}: {
  title: string
  onPress: () => void
  variant?: ButtonVariant
  disabled?: boolean
  loading?: boolean
  style?: ViewStyle
  textStyle?: TextStyle
}) {
  const bg: Record<ButtonVariant, string> = {
    primary: colors.accent,
    outline: "transparent",
    danger: colors.dangerDim,
    ghost: "transparent",
    success: colors.success,
  }
  const fg: Record<ButtonVariant, string> = {
    primary: colors.text,
    outline: colors.text,
    danger: colors.danger,
    ghost: colors.accent,
    success: colors.bg,
  }
  return (
    <PressableScale
      onPress={onPress}
      disabled={disabled || loading}
      activeOpacity={0.85}
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled: !!disabled, busy: !!loading }}
      style={[
        styles.button,
        { backgroundColor: bg[variant] },
        variant === "outline" && styles.buttonOutline,
        (disabled || loading) && styles.buttonDisabled,
        style,
      ]}
    >
      {loading ? <ActivityIndicator color={fg[variant]} /> : <Text style={[styles.buttonText, { color: fg[variant] }, textStyle]}>{title}</Text>}
    </PressableScale>
  )
}

export function Spinner() {
  return (
    <View style={styles.spinner}>
      <ActivityIndicator color={colors.text} size="large" />
    </View>
  )
}

export function Divider() {
  return <View style={styles.divider} />
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  title: { color: colors.text, fontSize: font.xxl, fontWeight: "800", fontStyle: "italic", letterSpacing: -0.5 },
  subtitle: { color: colors.text, fontSize: font.lg, fontWeight: "700" },
  muted: { color: colors.textMuted, fontSize: font.sm },
  label: { color: colors.textSecondary, fontSize: font.xs, fontWeight: "700", letterSpacing: 1, textTransform: "uppercase" },
  card: { backgroundColor: colors.card, borderRadius: radius.lg, padding: spacing.lg, borderWidth: 1, borderColor: colors.border },
  row: { flexDirection: "row", alignItems: "center" },
  button: { borderRadius: radius.md, paddingVertical: 14, paddingHorizontal: spacing.lg, alignItems: "center", justifyContent: "center" },
  buttonOutline: { borderWidth: 1, borderColor: colors.borderStrong },
  buttonDisabled: { opacity: 0.4 },
  buttonText: { fontSize: font.md, fontWeight: "700" },
  spinner: { flex: 1, backgroundColor: colors.bg, alignItems: "center", justifyContent: "center" },
  divider: { height: 1, backgroundColor: colors.border, marginVertical: spacing.sm },
})
