import { describe, expect, test } from "bun:test"
import { bestTime, delayMinutes, formatDelay, formatRelative, formatTime } from "./time.ts"

const scheduled = "2026-11-25T13:15:00Z"

describe("timing", () => {
  test("uses the actual time once known, else the expected one", () => {
    expect(bestTime({ scheduled })).toBe(scheduled)
    expect(bestTime({ scheduled, expected: "2026-11-25T13:20:00Z" })).toBe("2026-11-25T13:20:00Z")
    expect(bestTime({ scheduled, expected: "2026-11-25T13:20:00Z", actual: "2026-11-25T13:18:00Z" })).toBe(
      "2026-11-25T13:18:00Z",
    )
  })

  test("computes the delay against the schedule", () => {
    expect(delayMinutes({ scheduled })).toBeUndefined()
    expect(delayMinutes({ scheduled, expected: "2026-11-25T13:20:00Z" })).toBe(5)
    expect(delayMinutes({ scheduled, actual: "2026-11-25T13:14:00Z" })).toBe(-1)
  })

  test("formats delays", () => {
    expect(formatDelay(undefined)).toBe("–")
    expect(formatDelay(0)).toBe("on time")
    expect(formatDelay(5)).toBe("+5′")
    expect(formatDelay(-1)).toBe("-1′")
  })

  test("formats times in a time zone", () => {
    expect(formatTime(scheduled, "UTC")).toBe("13:15")
    expect(formatTime(scheduled, "Europe/Rome")).toBe("14:15")
  })

  test("formats relative times", () => {
    const now = Date.parse(scheduled)
    expect(formatRelative("2026-11-25T13:12:00Z", now)).toBe("3 min. ago")
    expect(formatRelative("2026-11-25T15:15:00Z", now)).toBe("in 2 hr.")
  })
})
