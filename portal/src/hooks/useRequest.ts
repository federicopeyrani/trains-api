import { useCallback, useEffect, useEffectEvent, useState } from "react"
import { isAbort } from "../api/client.ts"

type Settled<T> = { status: "success"; data: T; receivedAt: string } | { status: "error"; error: unknown }

export type RequestState<T> = { status: "idle" } | { status: "loading" } | Settled<T>

/**
 * Runs [load] whenever [key] changes (none while it is null), aborting the previous call. No caching: every key change
 * or `reload()` asks the API again, which is what a debug tool wants.
 */
export function useRequest<T>(
  key: string | null,
  load: (signal: AbortSignal) => Promise<T>,
): { state: RequestState<T>; reload: () => void } {
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<{ id: string; state: Settled<T> } | null>(null)
  const id = key === null ? null : `${attempt} ${key}`
  const run = useEffectEvent((signal: AbortSignal) => load(signal))

  useEffect(() => {
    if (id === null) return
    const controller = new AbortController()
    run(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) {
          setResult({ id, state: { status: "success", data, receivedAt: new Date().toISOString() } })
        }
      },
      (error: unknown) => {
        if (!controller.signal.aborted && !isAbort(error)) setResult({ id, state: { status: "error", error } })
      },
    )
    return () => controller.abort()
  }, [id])

  const reload = useCallback(() => setAttempt((n) => n + 1), [])
  const state: RequestState<T> =
    id === null ? { status: "idle" } : result?.id === id ? result.state : { status: "loading" }
  return { state, reload }
}
