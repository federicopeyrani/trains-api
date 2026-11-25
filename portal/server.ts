import index from "./index.html"

/**
 * Serves the portal. In development (`bun --hot server.ts`) the page is bundled on the fly with hot reloading; for
 * production, `bun run build` bundles this server together with the page into `dist/`.
 *
 * `GET /config.json` tells the page which backend to call by default (`API_BASE_URL`); when it is not set, the page
 * calls the same host on the backend's port.
 */
const port = Number(process.env.PORT ?? 3000)
const apiBaseUrl = process.env.API_BASE_URL || null

const server = Bun.serve({
  port,
  development: process.env.NODE_ENV !== "production" && { hmr: true, console: true },
  routes: {
    "/config.json": Response.json({ apiBaseUrl }),
    "/*": index,
  },
})

console.log(`Portal listening on ${server.url} (API_BASE_URL: ${apiBaseUrl ?? "same host, port 8081"})`)
