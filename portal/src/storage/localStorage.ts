import { useCallback, useMemo, useSyncExternalStore } from "react"

// Used when localStorage is unavailable (private mode, blocked site data), so the page keeps working for the session.
const memory = new Map<string, string>()
const listeners = new Set<() => void>()

function readRaw(key: string): string | null {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return memory.get(key) ?? null
  }
}

export function writeRaw(key: string, value: string | null): void {
  try {
    if (value === null) window.localStorage.removeItem(key)
    else window.localStorage.setItem(key, value)
  } catch {
    if (value === null) memory.delete(key)
    else memory.set(key, value)
  }
  for (const listener of listeners) listener()
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  window.addEventListener("storage", listener)
  return () => {
    listeners.delete(listener)
    window.removeEventListener("storage", listener)
  }
}

/** A string stored under [key], shared by every component (and tab) using it. */
export function useStoredString(key: string): [string | null, (value: string | null) => void] {
  const value = useSyncExternalStore(
    subscribe,
    () => readRaw(key),
    () => null,
  )
  const set = useCallback((next: string | null) => writeRaw(key, next), [key])
  return [value, set]
}

/**
 * A JSON value stored under [key]. [parse] validates what was stored (by an older version, or by hand) and returns
 * [fallback] when it is unusable; [fallback] must be stable.
 */
export function useStoredJson<T>(
  key: string,
  fallback: T,
  parse: (value: unknown) => T | undefined,
): [T, (update: (previous: T) => T) => void] {
  const [raw, setRaw] = useStoredString(key)
  const value = useMemo(() => decode(raw, fallback, parse), [raw, fallback, parse])
  const update = useCallback(
    (fn: (previous: T) => T) => setRaw(JSON.stringify(fn(decode(readRaw(key), fallback, parse)))),
    [key, fallback, parse, setRaw],
  )
  return [value, update]
}

function decode<T>(raw: string | null, fallback: T, parse: (value: unknown) => T | undefined): T {
  if (raw === null) return fallback
  try {
    return parse(JSON.parse(raw)) ?? fallback
  } catch {
    return fallback
  }
}
