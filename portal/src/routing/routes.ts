import type { SearchQuery, StationRef } from "../storage/recent.ts"

export type Route =
  | { name: "home" }
  | { name: "search"; query?: SearchQuery }
  | { name: "run"; runId: string }
  | { name: "notFound"; path: string }

// Routes live in the hash (`#/search?from=...`), so the portal is a set of static files and every screen is a link.

/** Parses `location.hash`. */
export function parseHash(hash: string): Route {
  const [path = "", queryString = ""] = hash.replace(/^#/, "").split("?", 2)
  const segments = path.split("/").filter(Boolean)

  if (segments.length === 0) return { name: "home" }
  if (segments[0] === "search" && segments.length === 1) return { name: "search", query: parseSearchQuery(queryString) }
  if (segments[0] === "runs" && segments.length === 2 && segments[1]) {
    return { name: "run", runId: decodeURIComponent(segments[1]) }
  }
  return { name: "notFound", path }
}

/** Formats [route] as a hash (`#/...`), usable as an `href`. */
export function routeHref(route: Route): string {
  switch (route.name) {
    case "home":
      return "#/"
    case "search":
      return route.query ? `#/search?${formatSearchQuery(route.query)}` : "#/search"
    case "run":
      return `#/runs/${encodeURIComponent(route.runId)}`
    case "notFound":
      return `#/${route.path}`
  }
}

// Stations are written as `id:name`, so a linked search shows names without asking the API. A bare id works too.

export function formatSearchQuery(query: SearchQuery): string {
  const params = new URLSearchParams()
  for (const station of query.from) params.append("from", formatStation(station))
  for (const station of query.to) params.append("to", formatStation(station))
  if (query.date) params.set("date", query.date)
  return params.toString()
}

/** The search in [queryString], or undefined when it lacks origins or destinations. */
export function parseSearchQuery(queryString: string): SearchQuery | undefined {
  const params = new URLSearchParams(queryString)
  const from = params
    .getAll("from")
    .map(parseStation)
    .filter((s) => s !== undefined)
  const to = params
    .getAll("to")
    .map(parseStation)
    .filter((s) => s !== undefined)
  if (from.length === 0 || to.length === 0) return undefined

  const date = params.get("date") || undefined
  return date ? { from, to, date } : { from, to }
}

function formatStation(station: StationRef): string {
  return station.name && station.name !== station.id ? `${station.id}:${station.name}` : station.id
}

function parseStation(value: string): StationRef | undefined {
  const separator = value.indexOf(":")
  const id = (separator === -1 ? value : value.slice(0, separator)).trim()
  const name = separator === -1 ? "" : value.slice(separator + 1).trim()
  return id ? { id, name: name || id } : undefined
}
