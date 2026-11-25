import type { Timing } from "../api/client.ts"

const MINUTE = 60_000

/** The best known time of an event: actual if it happened, else expected, else scheduled. */
export function bestTime(timing: Timing): string {
  return timing.actual ?? timing.expected ?? timing.scheduled
}

/** The delay in minutes of the best known time against the scheduled one; undefined when only the schedule is known. */
export function delayMinutes(timing: Timing): number | undefined {
  const known = timing.actual ?? timing.expected
  if (known === undefined) return undefined
  return Math.round((Date.parse(known) - Date.parse(timing.scheduled)) / MINUTE)
}

export function formatDelay(minutes: number | undefined): string {
  if (minutes === undefined) return "–"
  if (minutes === 0) return "on time"
  return minutes > 0 ? `+${minutes}′` : `${minutes}′`
}

/** `HH:mm` in [timeZone] (default: the browser's). */
export function formatTime(iso: string, timeZone?: string): string {
  return new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit", hourCycle: "h23", timeZone }).format(
    new Date(iso),
  )
}

/** `25 Nov 2026, 14:03:22` in [timeZone] (default: the browser's). */
export function formatDateTime(iso: string, timeZone?: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
    timeZone,
  }).format(new Date(iso))
}

/** `3 min ago`, `in 2 h`, ... relative to [now]. */
export function formatRelative(iso: string, now: number = Date.now()): string {
  const minutes = Math.round((Date.parse(iso) - now) / MINUTE)
  const format = new Intl.RelativeTimeFormat("en", { numeric: "auto", style: "short" })
  if (Math.abs(minutes) < 60) return format.format(minutes, "minute")
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 48) return format.format(hours, "hour")
  return format.format(Math.round(hours / 24), "day")
}
