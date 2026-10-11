import { useCallback, useEffect, useState } from "react"
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native"
import { useRouter } from "expo-router"
import { Ionicons } from "@expo/vector-icons"
import VolumeKeeper from "../modules/volume-keeper"
import { Screen, Title } from "../components/ui"
import { colors, font, spacing } from "../lib/theme"

/** The runtime's in-memory trace: every volume broadcast, output change, notice and decision. */
export default function DebugScreen() {
  const router = useRouter()
  const [lines, setLines] = useState<string[]>([])
  const load = useCallback(() => { VolumeKeeper.getTrace().then(setLines).catch(() => {}) }, [])
  useEffect(() => {
    load()
    const sub = VolumeKeeper.addListener("onChange", load)
    const id = setInterval(load, 2_000)
    return () => {
      sub.remove()
      clearInterval(id)
    }
  }, [load])
  return (
    <Screen>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} accessibilityRole="button" accessibilityLabel="Back" hitSlop={12}>
          <Ionicons name="chevron-back" size={24} color={colors.text} />
        </TouchableOpacity>
        <Title style={styles.title}>Trace</Title>
      </View>
      <ScrollView contentContainerStyle={styles.body}>
        {lines.map((l, i) => <Text key={i} style={styles.line} selectable>{l}</Text>)}
      </ScrollView>
    </Screen>
  )
}

const styles = StyleSheet.create({
  header: { flexDirection: "row", alignItems: "center", padding: spacing.lg },
  title: { marginLeft: spacing.sm },
  body: { paddingHorizontal: spacing.lg, paddingBottom: spacing.xxxl },
  line: { color: colors.textSecondary, fontSize: font.xs, fontFamily: "monospace", marginBottom: 2 },
})
