// Design tokens. This is the ONLY file allowed to contain colour hex literals
// (scripts/smoke-test.sh greps for stray hex elsewhere). Dark theme only.
// Same palette as PKTrainer, so Phil's apps look like one family.

export const colors = {
  bg: "#0a0a0a",
  card: "#111111",
  surface: "#1a1a1a",
  surfaceRaised: "#222222",
  border: "#2a2a2a",
  borderStrong: "#3a3a3a",

  text: "#ffffff",
  textSecondary: "#9a9a9a",
  textMuted: "#666666",
  textDim: "#444444",

  accent: "#5b8def", // primary actions, links, the current level
  accentDim: "#2f4a86",
  success: "#22e39a", // restored
  successDim: "#0f5c40",
  warning: "#eab308", // near misses, attention
  warningDim: "#5c4a0a",
  danger: "#ef4444", // failed restores, missing permissions
  dangerDim: "#5c1d1d",
} as const

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 24,
  xxxl: 32,
} as const

export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  pill: 999,
} as const

export const font = {
  xs: 11,
  sm: 13,
  md: 15,
  lg: 17,
  xl: 20,
  xxl: 26,
  display: 34,
} as const

export type ColorToken = keyof typeof colors
