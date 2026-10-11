// Motion tokens. `theme.ts` is to colour what this file is to movement: the one
// place durations and spring feels are defined, so a set tick, a sheet sliding
// up and a chart drawing itself all move like they belong to the same app.
//
// Pure (no RN imports) so it unit-tests from Node, same rule as the other
// lib/*.ts helpers. The RN `Easing` curves that go with these live in
// components/Motion.tsx.

/** Milliseconds. Four steps is deliberate — more and the scale stops meaning anything. */
export const duration = {
  /** Colour and opacity swaps that should read as instant without snapping. */
  instant: 120,
  /** The default: press feedback, fades, a few pixels of travel. */
  fast: 180,
  /** Something crosses a visible distance — sheets, expands, page fades. */
  normal: 260,
  /** A deliberate reveal: a chart drawing in, a trophy landing. */
  slow: 420,
} as const

/** `Animated.spring` configs (tension/friction form, matching the RN default API). */
export const spring = {
  /** Press feedback: quick, essentially no overshoot. */
  press: { tension: 300, friction: 20 },
  /** A tick landing or a number changing — the bounce is the point. */
  pop: { tension: 220, friction: 9 },
  /** A sheet arriving: settles without a visible wobble. */
  sheet: { tension: 90, friction: 14 },
} as const

/** How far a pressed control shrinks. Subtle: the finger covers it anyway. */
export const PRESS_SCALE = 0.96

/** How far a popping value overshoots before settling. */
export const POP_SCALE = 1.12

/** Pixels a fading-in element rises through. */
export const RISE_DISTANCE = 10

/**
 * Delay for item `index` of a staggered list, capped so a long list does not
 * leave the last row arriving a second after the first. Phil's history screens
 * can hold hundreds of rows; past the cap they all simply arrive together.
 */
export function staggerDelay(index: number, step = 45, max = 270): number {
  if (!Number.isFinite(index) || index <= 0) return 0
  return Math.min(Math.floor(index) * step, max)
}

/**
 * A duration honouring the OS "remove animations" setting: 0 collapses the
 * animation to a state change, which is what that setting asks for.
 */
export function motionDuration(ms: number, reduceMotion: boolean): number {
  return reduceMotion ? 0 : ms
}
