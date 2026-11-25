import { expect, test } from "bun:test"
import { defaultBaseUrl, resolveBaseUrl } from "./baseUrl.ts"

const location = { protocol: "http:", hostname: "trains.lan" }

test("defaults to the page's host on the backend port", () => {
  expect(defaultBaseUrl(location, null)).toBe("http://trains.lan:8081")
})

test("prefers the server's configured base URL", () => {
  expect(defaultBaseUrl(location, "https://api.example.org")).toBe("https://api.example.org")
  expect(defaultBaseUrl(location, "  ")).toBe("http://trains.lan:8081")
})

test("prefers the override from the settings", () => {
  expect(resolveBaseUrl("http://localhost:9000", "http://trains.lan:8081")).toBe("http://localhost:9000")
  expect(resolveBaseUrl("", "http://trains.lan:8081")).toBe("http://trains.lan:8081")
  expect(resolveBaseUrl(null, "http://trains.lan:8081")).toBe("http://trains.lan:8081")
})
