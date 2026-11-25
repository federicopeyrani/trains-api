import { describe, expect, test } from "bun:test"
import {
  addRecentRun,
  addRecentSearch,
  MAX_RECENT,
  parseRecentRuns,
  parseRecentSearches,
  removeRecentSearch,
  type RecentRun,
  type RecentSearch,
} from "./recent.ts"

const milano = { id: "S01700", name: "MILANO CENTRALE" }
const rogoredo = { id: "S01820", name: "MILANO ROGOREDO" }
const bologna = { id: "S05043", name: "BOLOGNA CENTRALE" }

const search = (overrides: Partial<RecentSearch> = {}): RecentSearch => ({
  from: [milano, rogoredo],
  to: [bologna],
  searchedAt: "2026-11-25T10:00:00Z",
  ...overrides,
})

const run = (id: string): RecentRun => ({
  id,
  train: "9545",
  origin: "MILANO CENTRALE",
  destination: "ROMA TERMINI",
  openedAt: "2026-11-25T10:00:00Z",
})

describe("recent searches", () => {
  test("puts the newest first", () => {
    const older = search({ to: [milano], from: [bologna] })
    const newer = search()
    expect(addRecentSearch([older], newer)).toEqual([newer, older])
  })

  test("replaces the same search, whatever the order of its stations", () => {
    const first = search()
    const again = search({ from: [rogoredo, milano], searchedAt: "2026-11-25T11:00:00Z" })
    expect(addRecentSearch([first], again)).toEqual([again])
  })

  test("tells searches at different dates apart", () => {
    const now = search()
    const later = search({ date: "2026-11-25T18:00:00Z" })
    expect(addRecentSearch([now], later)).toEqual([later, now])
  })

  test(`keeps at most ${MAX_RECENT}`, () => {
    let list: RecentSearch[] = []
    for (let i = 0; i < MAX_RECENT + 5; i++) list = addRecentSearch(list, search({ date: `2026-11-25T10:${i}:00Z` }))
    expect(list).toHaveLength(MAX_RECENT)
    expect(list[0]?.date).toBe(`2026-11-25T10:${MAX_RECENT + 4}:00Z`)
  })

  test("removes a search", () => {
    expect(removeRecentSearch([search(), search({ date: "2026-11-25T18:00:00Z" })], search())).toEqual([
      search({ date: "2026-11-25T18:00:00Z" }),
    ])
  })

  test("drops stored entries that are not searches", () => {
    expect(parseRecentSearches([search(), { from: "S01700" }, null])).toEqual([search()])
    expect(parseRecentSearches({ not: "a list" })).toBeUndefined()
  })
})

describe("recent runs", () => {
  test("puts a reopened run first, once", () => {
    expect(addRecentRun([run("a"), run("b")], run("b"))).toEqual([run("b"), run("a")])
  })

  test("drops stored entries that are not runs", () => {
    expect(parseRecentRuns([run("a"), { id: 1 }])).toEqual([run("a")])
  })
})
