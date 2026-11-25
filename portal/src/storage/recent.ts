/** A station as shown to the user: its id and, when known, its name. */
export interface StationRef {
  id: string
  name: string
}

export interface SearchQuery {
  from: StationRef[]
  to: StationRef[]
  /** RFC 3339 date-time; absent means "now". */
  date?: string
}

export interface RecentSearch extends SearchQuery {
  searchedAt: string
}

export interface RecentRun {
  id: string
  train: string
  origin: string
  destination: string
  openedAt: string
}

export const RECENT_SEARCHES_KEY = "portal.recentSearches"
export const RECENT_RUNS_KEY = "portal.recentRuns"
export const MAX_RECENT = 20

const ids = (stations: StationRef[]) => stations.map((s) => s.id).toSorted()

/** Two searches are the same when they have the same origins, destinations and date, in any order. */
export function searchKey(search: SearchQuery): string {
  return JSON.stringify([ids(search.from), ids(search.to), search.date ?? null])
}

/** Puts [entry] first, dropping an older copy of it and the entries beyond [MAX_RECENT]. */
export function addRecentSearch(list: readonly RecentSearch[], entry: RecentSearch): RecentSearch[] {
  const key = searchKey(entry)
  return [entry, ...list.filter((e) => searchKey(e) !== key)].slice(0, MAX_RECENT)
}

export function addRecentRun(list: readonly RecentRun[], entry: RecentRun): RecentRun[] {
  return [entry, ...list.filter((e) => e.id !== entry.id)].slice(0, MAX_RECENT)
}

export function removeRecentSearch(list: readonly RecentSearch[], entry: SearchQuery): RecentSearch[] {
  const key = searchKey(entry)
  return list.filter((e) => searchKey(e) !== key)
}

export function removeRecentRun(list: readonly RecentRun[], id: string): RecentRun[] {
  return list.filter((e) => e.id !== id)
}

// Parsers for what is in localStorage: entries that do not have the expected shape are dropped.

export function parseRecentSearches(value: unknown): RecentSearch[] | undefined {
  return Array.isArray(value) ? value.filter(isRecentSearch) : undefined
}

export function parseRecentRuns(value: unknown): RecentRun[] | undefined {
  return Array.isArray(value) ? value.filter(isRecentRun) : undefined
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null
}

function isStationRef(value: unknown): value is StationRef {
  return isRecord(value) && typeof value.id === "string" && typeof value.name === "string"
}

function isRecentSearch(value: unknown): value is RecentSearch {
  return (
    isRecord(value) &&
    Array.isArray(value.from) &&
    value.from.every(isStationRef) &&
    Array.isArray(value.to) &&
    value.to.every(isStationRef) &&
    (value.date === undefined || typeof value.date === "string") &&
    typeof value.searchedAt === "string"
  )
}

function isRecentRun(value: unknown): value is RecentRun {
  return (
    isRecord(value) &&
    typeof value.id === "string" &&
    typeof value.train === "string" &&
    typeof value.origin === "string" &&
    typeof value.destination === "string" &&
    typeof value.openedAt === "string"
  )
}
