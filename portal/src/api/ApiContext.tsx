import { createContext, use, useEffect, useMemo, useState, type ReactNode } from "react"
import { BASE_URL_KEY, defaultBaseUrl, fetchConfiguredBaseUrl, resolveBaseUrl } from "../config/baseUrl.ts"
import { useStoredString } from "../storage/localStorage.ts"
import { createApiClient, type ApiClient } from "./client.ts"

interface Api {
  client: ApiClient
  /** The backend in use. */
  baseUrl: string
  /** The backend used without an override. */
  defaultBaseUrl: string
  /** The base URL set in the settings, if any. */
  override: string | null
  setOverride: (value: string | null) => void
}

const ApiContext = createContext<Api | null>(null)

export function ApiProvider({ children }: { children: ReactNode }) {
  // undefined while /config.json loads, so no request goes to the wrong backend.
  const [configured, setConfigured] = useState<string | null | undefined>(undefined)
  const [override, setOverride] = useStoredString(BASE_URL_KEY)

  useEffect(() => {
    let active = true
    void fetchConfiguredBaseUrl().then((value) => {
      if (active) setConfigured(value)
    })
    return () => {
      active = false
    }
  }, [])

  const fallback = defaultBaseUrl(window.location, configured)
  const baseUrl = resolveBaseUrl(override, fallback)
  const client = useMemo(() => createApiClient(baseUrl), [baseUrl])
  const api = useMemo(
    () => ({ client, baseUrl, defaultBaseUrl: fallback, override, setOverride }),
    [client, baseUrl, fallback, override, setOverride],
  )

  if (configured === undefined) return null
  return <ApiContext value={api}>{children}</ApiContext>
}

export function useApi(): Api {
  const api = use(ApiContext)
  if (!api) throw new Error("useApi must be used inside ApiProvider")
  return api
}
