/** The `localStorage` key of the base URL chosen in the settings. */
export const BASE_URL_KEY = "portal.baseUrl"

/** The backend port when the portal runs on the backend's host. */
export const DEFAULT_API_PORT = 8081

/**
 * The backend to call when no override is set: the server's `API_BASE_URL` if given, else the page's own host on the
 * backend's port.
 */
export function defaultBaseUrl(location: Pick<Location, "protocol" | "hostname">, configured?: string | null): string {
  return configured?.trim() || `${location.protocol}//${location.hostname}:${DEFAULT_API_PORT}`
}

/** The base URL to use: the override from the settings if set, else the default. */
export function resolveBaseUrl(override: string | null | undefined, fallback: string): string {
  return override?.trim() || fallback
}

/** Reads the server-side configuration (`/config.json`); missing or broken configuration means none. */
export async function fetchConfiguredBaseUrl(): Promise<string | null> {
  try {
    const response = await fetch("/config.json")
    if (!response.ok) return null
    const config: unknown = await response.json()
    if (typeof config === "object" && config !== null && "apiBaseUrl" in config) {
      return typeof config.apiBaseUrl === "string" ? config.apiBaseUrl : null
    }
    return null
  } catch {
    return null
  }
}
