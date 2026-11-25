import { useSyncExternalStore } from "react"
import { parseHash, routeHref, type Route } from "./routes.ts"

function subscribe(listener: () => void): () => void {
  window.addEventListener("hashchange", listener)
  return () => window.removeEventListener("hashchange", listener)
}

/** The current hash, kept in sync with navigation. */
export function useHash(): string {
  return useSyncExternalStore(
    subscribe,
    () => window.location.hash,
    () => "",
  )
}

export function useRoute(): Route {
  return parseHash(useHash())
}

export function navigate(route: Route): void {
  window.location.hash = routeHref(route)
}
