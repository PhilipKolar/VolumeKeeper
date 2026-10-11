// Shared animated primitives (subset of PKTrainer's): RN `Animated` with the native driver,
// honouring the OS "remove animations" setting.
import { useEffect, useRef, useState } from "react"
import { AccessibilityInfo, Animated, TouchableOpacity, type TouchableOpacityProps } from "react-native"
import { spring, PRESS_SCALE } from "../lib/motion"

/** Android's "Remove animations" setting, kept live. */
export function useReduceMotion(): boolean {
  const [on, setOn] = useState(false)
  useEffect(() => {
    let alive = true
    AccessibilityInfo.isReduceMotionEnabled()
      .then((v) => { if (alive) setOn(v) })
      .catch(() => {})
    const sub = AccessibilityInfo.addEventListener("reduceMotionChanged", setOn)
    return () => {
      alive = false
      sub.remove()
    }
  }, [])
  return on
}

const AnimatedTouchable = Animated.createAnimatedComponent(TouchableOpacity)

/** `TouchableOpacity` that also shrinks slightly while held. */
export function PressableScale({ children, style, scaleTo = PRESS_SCALE, activeOpacity = 0.9, onPressIn, onPressOut, ...rest }: TouchableOpacityProps & { scaleTo?: number }) {
  const scale = useRef(new Animated.Value(1)).current
  const reduce = useReduceMotion()
  const to = (value: number) => {
    if (reduce) return
    Animated.spring(scale, { toValue: value, useNativeDriver: true, ...spring.press }).start()
  }
  return (
    <AnimatedTouchable
      {...rest}
      activeOpacity={activeOpacity}
      style={[style, { transform: [{ scale }] }]}
      onPressIn={(e) => { to(scaleTo); onPressIn?.(e) }}
      onPressOut={(e) => { to(1); onPressOut?.(e) }}
    >
      {children}
    </AnimatedTouchable>
  )
}
