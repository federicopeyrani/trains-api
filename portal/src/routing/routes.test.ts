import { describe, expect, test } from "bun:test"
import { parseHash, parseSearchQuery, routeHref, type Route } from "./routes.ts"

describe("routes", () => {
  const routes: Route[] = [
    { name: "home" },
    { name: "search" },
    {
      name: "search",
      query: {
        from: [
          { id: "S01700", name: "MILANO CENTRALE" },
          { id: "S01820", name: "MILANO ROGOREDO" },
        ],
        to: [{ id: "S05043", name: "BOLOGNA C.LE: EST" }],
        date: "2026-11-25T13:15:00Z",
      },
    },
    { name: "search", query: { from: [{ id: "S01700", name: "MILANO" }], to: [{ id: "S05043", name: "BOLOGNA" }] } },
    { name: "run", runId: "S01700_9545_1769122800000" },
  ]

  test.each(routes)("round trips %p", (route) => {
    expect(parseHash(routeHref(route))).toEqual(route)
  })

  test("parses an empty hash as home", () => {
    expect(parseHash("")).toEqual({ name: "home" })
  })

  test("reads bare station ids, using the id as name", () => {
    expect(parseSearchQuery("from=S01700&to=S05043")).toEqual({
      from: [{ id: "S01700", name: "S01700" }],
      to: [{ id: "S05043", name: "S05043" }],
    })
  })

  test("needs both origins and destinations", () => {
    expect(parseSearchQuery("from=S01700")).toBeUndefined()
  })

  test("reports unknown paths", () => {
    expect(parseHash("#/nope")).toEqual({ name: "notFound", path: "/nope" })
  })
})
