import { useCallback, useEffect, useRef } from "react"
import { Tabs, useRouter } from "expo-router"
import { Animated, BackHandler, StyleSheet, View } from "react-native"
import { useSafeAreaInsets } from "react-native-safe-area-context"
import { Ionicons } from "@expo/vector-icons"
import * as Haptics from "expo-haptics"
import { useBackHandler } from "../../hooks/useBackHandler"
import { useReduceMotion } from "../../components/Motion"
import { spring } from "../../lib/motion"
import { colors } from "../../lib/theme"

type IconName = keyof typeof Ionicons.glyphMap

function solid(name: IconName): IconName {
  return name.replace(/-outline$/, "") as IconName
}

/** PKTrainer's tab icon: pill springs in, glyph crossfades outline → solid. */
function TabIcon({ name, focused }: { name: IconName; focused: boolean }) {
  const v = useRef(new Animated.Value(focused ? 1 : 0)).current
  const reduce = useReduceMotion()
  useEffect(() => {
    if (reduce) {
      v.setValue(focused ? 1 : 0)
      return
    }
    Animated.spring(v, { toValue: focused ? 1 : 0, useNativeDriver: true, ...spring.pop }).start()
  }, [focused, reduce, v])

  return (
    <View style={styles.tab}>
      <Animated.View style={[styles.pill, { opacity: v, transform: [{ scale: v.interpolate({ inputRange: [0, 1], outputRange: [0.7, 1] }) }] }]} />
      <Animated.View style={{ opacity: v.interpolate({ inputRange: [0, 1], outputRange: [1, 0] }) }}>
        <Ionicons name={name} size={20} color={colors.textMuted} />
      </Animated.View>
      <Animated.View style={[styles.iconOverlay, { opacity: v }]} importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
        <Ionicons name={solid(name)} size={20} color={colors.text} />
      </Animated.View>
    </View>
  )
}

export default function TabsLayout() {
  const insets = useSafeAreaInsets()
  const router = useRouter()
  const bottomPad = Math.max(insets.bottom, 16)

  useBackHandler(
    useCallback(() => {
      if (router.canGoBack()) return false
      BackHandler.exitApp()
      return true
    }, [router]),
  )

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        sceneStyle: { backgroundColor: colors.bg },
        tabBarStyle: {
          backgroundColor: colors.card,
          borderTopColor: colors.border,
          borderTopWidth: 1,
          height: 60 + bottomPad,
          paddingBottom: bottomPad,
          paddingTop: 10,
        },
        tabBarShowLabel: false,
      }}
      screenListeners={{ tabPress: () => { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light) } }}
    >
      <Tabs.Screen name="index" options={{ tabBarAccessibilityLabel: "Status", tabBarIcon: ({ focused }) => <TabIcon name="pulse-outline" focused={focused} /> }} />
      <Tabs.Screen name="devices" options={{ tabBarAccessibilityLabel: "Devices", tabBarIcon: ({ focused }) => <TabIcon name="headset-outline" focused={focused} /> }} />
      <Tabs.Screen name="log" options={{ tabBarAccessibilityLabel: "Log", tabBarIcon: ({ focused }) => <TabIcon name="list-outline" focused={focused} /> }} />
    </Tabs>
  )
}

const styles = StyleSheet.create({
  tab: { alignItems: "center", justifyContent: "center", paddingHorizontal: 16, paddingVertical: 8, minWidth: 56, minHeight: 36 },
  pill: { ...StyleSheet.absoluteFillObject, borderRadius: 20, backgroundColor: colors.border },
  iconOverlay: { ...StyleSheet.absoluteFillObject, alignItems: "center", justifyContent: "center" },
})
