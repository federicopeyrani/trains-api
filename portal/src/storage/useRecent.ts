import { useStoredJson } from "./localStorage.ts"
import {
  parseRecentRuns,
  parseRecentSearches,
  RECENT_RUNS_KEY,
  RECENT_SEARCHES_KEY,
  type RecentRun,
  type RecentSearch,
} from "./recent.ts"

const NONE: never[] = []

export function useRecentSearches() {
  return useStoredJson<RecentSearch[]>(RECENT_SEARCHES_KEY, NONE, parseRecentSearches)
}

export function useRecentRuns() {
  return useStoredJson<RecentRun[]>(RECENT_RUNS_KEY, NONE, parseRecentRuns)
}
