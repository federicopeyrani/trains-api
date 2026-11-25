# Trains debug portal

A small web tool to exercise the API by hand: search trips between several origin and destination stations
(`GET /trips/all`), open a run (`GET /runs/{runId}`), and get back to recent searches and runs.

Built with Bun, React and TypeScript, [React Aria Components](https://react-aria.adobe.com/), and a client generated from
the API's own contract (`../src/main/resources/openapi.yaml`). There is no cache layer: every screen asks the API again,
and every response can be inspected raw.

## Commands

Requires Bun (version in `../.tool-versions`).

```bash
bun install
bun run dev           # http://localhost:3000, hot reload (PORT to change it)
bun run build         # bundles the server and the page into dist/
bun run start         # serves dist/ (NODE_ENV=production)
bun run check         # format:check + lint + typecheck + test
bun run format        # oxfmt
bun run lint          # oxlint
bun run generate      # regenerate src/api/schema.gen.ts from the OpenAPI spec
```

`dev`, `build` and `typecheck` regenerate the client first, so a change to `openapi.yaml` that breaks the portal fails
its type check. `src/api/schema.gen.ts` is generated and not committed.

## Which backend it calls

In order:

1. the base URL set in the settings (⚙ in the header), kept in `localStorage`;
2. `API_BASE_URL` given to the portal server (`API_BASE_URL=https://trains.example.org bun run start`);
3. the page's own host on port 8081 (e.g. `http://192.168.1.10:8081` when the portal is opened at
   `http://192.168.1.10:3000`).

The browser calls the backend directly, so the backend must allow the portal's origin. It allows any origin by default
(`api.cors.allowed-origins` in `application.properties`).

## Layout

- `server.ts`: `Bun.serve` with the page as an HTML import, plus `GET /config.json` (`API_BASE_URL`).
- `src/api/`: the generated types, `openapi-fetch` client (JSON only) and `unwrap`, which turns error responses into an
  `ApiError` carrying the RFC 9457 problem.
- `src/routing/`: hash routes (`#/`, `#/search?from=S01700:Milano%20Centrale&to=...&date=...`, `#/runs/{runId}`),
  so every screen is a link.
- `src/storage/`: recent searches and runs in `localStorage` (newest first, deduplicated, at most 20), with an in-memory
  fallback when storage is blocked.
- `src/screens/`: home (recent searches and runs, open a run by id), search, run details.

## Notes

- `oxlint` and `oxfmt` are pinned to 1.94.0 and 0.79.0: the next releases (1.95.0 / 0.80.0) crash with "Illegal
  instruction" on CPUs without AVX (e.g. Intel Celeron J4125).
- `typescript` is pinned to 5.9: `openapi-typescript` needs the TypeScript compiler API, which TypeScript 7 does not
  ship.
- Station search matches name prefixes (`milano rog`, not `rogoredo`). A bare station id (`S01700`) followed by Enter
  is added as is.
