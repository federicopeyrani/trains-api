# ViaggiaTreno: open questions before further fixes

Improvements from the architecture review that cannot be made safely until we know how the ViaggiaTreno API
actually behaves. The API is unofficial and inconsistent: the same concept has different field names across
endpoints, and a field present in one response can be `null` or missing in another (e.g. `codDestinazione` on
board entries). `viaggiatreno-api.yaml` describes some of this, but parts of it are inferred, not observed.

How to investigate:
- Use the app's client: `./gradlew providerContractTest`, or a throwaway test built on `ViaggiaTrenoClients`. Plain
  `curl` gets rate limited (403) sooner, and headers must not be spoofed to get around it.
- Record what you learn as fixtures in `src/test/resources/providers/viaggiatreno/`.
- Pin each confirmed behaviour with a provider contract test (`ViaggiaTrenoContractTests`). Keep it to a handful of
  calls.

Each entry lists what the code does today, what is unknown, what to look at, and the likely fix once it is known.

---

## 1. Run status ignores delays, and maps provider code `2` to `DELAYED`

**Today.** `ViaggiaTrenoTripMapper.asStatus` maps `AndamentoTreno.provvedimento` as follows: `1` becomes `CANCELLED`,
`2` becomes `DELAYED`, and anything else becomes `ON_TIME`. The train-level `ritardo` (delay in minutes) is never
used, so a train 40 minutes late is reported `ON_TIME`.

**Unknown.**
- What `provvedimento` really means. The spec says `0` regular, `1` cancelled, `2` partially
  cancelled/diverted/rescheduled, `3` diverted. The `PartenzeArrivi` (board) schema only lists `0`–`2`. It's also
  unclear whether the value matches between `partenze` and `andamentoTreno` for the same train.
- How `ritardo` behaves:
  - before the train departs (`0`? `null`? a forecast?)
  - after it arrives
  - for trains that are early (negative values)
- What `tipoTreno` (`PG`/`ST`/`PP`/`SI`/`SF`/`DV`) and the board's `riprogrammazione` mean, and whether they are more
  reliable than `provvedimento`.

**Look at.** The same train on `partenze` and on `andamentoTreno`, for:
- a regular train
- a delayed train
- a partially cancelled train (look for strikes or disruption notices)
- a diverted train

**Likely fix.**
- Derive the status from `ritardo` with a threshold, plus a separate cancellation flag.
- Possibly add `PARTIALLY_CANCELLED`/`DIVERTED` to the public `Status` enum. That changes the public contract.

## 2. Cancelled trains disappear

**Today.**
- The spec says a cancelled train comes back as `tipoTreno = ST`, `provvedimento = 1`, with an empty `fermate`, or
  `andamentoTreno` may answer 204 ("often for cancelled or rescheduled trains").
- With an empty `fermate`, `toRun` throws ("has no stops"). `orNull` swallows the error, the departure has no run,
  and `Departure.serves` is then always false. So the train is missing from `/trips`. It shows up in `/departures`,
  but with no sign that it is cancelled.
- As a result, `CANCELLED` is effectively never produced.

**Unknown.** For a cancelled train:
- What `partenze` returns: does the entry stay on the board, and with which `provvedimento`/`statoTreno`?
- Whether `andamentoTreno` answers 200 with empty stops, or 204.
- Whether partially cancelled trains keep their stop list, and how the cancelled stops are marked
  (`Fermata.actualFermataType`? `tipoFermata`?).

**Look at.**
- A real cancelled train, on both endpoints.
- A partially cancelled train's `fermate`.

**Likely fix.**
- Carry the cancellation on `Departure` from the board entry.
- Build a run without stops (or a cancelled-run shape) instead of failing.
- Mark cancelled stops so `serves` can exclude them.

## 3. `/trips` silently drops trains whose run could not be fetched

**Today.** `serves` needs the run's stop list, so any train whose `andamentoTreno` call fails (403, 5xx, 204) is
missing from `/trips` and `/trips/all`. The response is still a 200, and looks exactly like "no trains".

**Unknown.**
- How often `andamentoTreno` fails or answers 204 for trains that are really running, apart from rate limiting.
- Whether another endpoint answers "trains from A to B" directly. The ViaggiaTreno website has a journey planner
  (`soluzioniViaggioNew/...`), but it is not in our spec and its shape and reliability are unknown.

**Look at.**
- The 204 rate across a few boards.
- The journey-planner endpoint's payload, and whether it is rate limited separately.

**Likely fix.** One of:
- Signal partial results (a header, a field, or an SSE event).
- Fall back to a different endpoint for A→B searches.
- Return board-only trips flagged as "unverified".

This is a product decision, informed by how often runs are missing.

## 4. "Expected" time means different things at each stop

**Today.**
- **Origin:** `expected = Fermata.partenzaTeorica`. This looks like the scheduled time, not a forecast.
- **Intermediate stops:** `expected = scheduled + AndamentoTreno.ritardo`. The train's current delay is applied to
  every stop, including ones already passed.
- **Destination:** `expected = compOrarioArrivoZeroEffettivo`, an `HH:mm` string placed on the nearest day.
- `scheduled` also comes from different sources: `orarioPartenza`/`orarioArrivo` at the ends, `partenzaTeorica`/
  `arrivoTeorico` at the stops.

**Unknown.** What these fields actually mean, and whether they are reliable:
- On `Fermata`: `programmata`, `partenzaTeorica`/`arrivoTeorico` (and their `...Zero` forms), `ritardoArrivo`,
  `ritardoPartenza`, and the per-stop `ritardo`. The current code says per-stop `ritardo` stays `0` until the train
  reaches the stop.
- Whether `compOrarioArrivoZeroEffettivo` is "scheduled plus current delay" or the provider's own forecast.

**Look at.** One train followed through its run (several `andamentoTreno` snapshots), comparing those fields before
and after each stop.

**Likely fix.**
- One rule for `expected` at every call: probably `scheduled + delay`, where the delay is the stop's own delay once
  it is known and the train's delay otherwise.
- One source for `scheduled`, documented in the mapper.

## 5. Resolving a destination by name picks the first fuzzy match

**Today.** When a board entry has no run, the destination is resolved with `cercaStazione(destinazione)` and the
first result is taken. `codDestinazione` is not used because "it can be null for some trips".

**Unknown.**
- How often `codDestinazione` is null on boards, and for which trains (international or regional?).
- Whether `destinazione` always matches some station's `nomeLungo` exactly, so we could require an exact match.

**Look at.**
- A few boards: count null `codDestinazione`, and compare `destinazione` with the `cercaStazione` results.

**Likely fix.** Use `codDestinazione` when present, an exact name match otherwise, and never a guess. Item 7 would
make both lookups local.

## 6. A trip search only sees one board page

**Today.** `/trips/{from}/{to}` filters a single `partenze` response. How far ahead that looks is undocumented, so
infrequent connections may never show up, and the API does not say so.

**Unknown.**
- How many entries `partenze` returns, and over what time span. It may differ between busy and quiet stations.
- Whether trains that already left but are delayed are included.
- Whether asking for a later `orarioAttuale` gives a clean next page, for paging.

**Look at.** Boards at a busy station (Milano Centrale) and a quiet one, at several times; compare first/last
`orarioPartenza` and the entry counts.

**Likely fix.**
- Document the window in `openapi.yaml`.
- Optionally page through boards up to a horizon, within the upstream call budget.

## 7. A local station registry instead of per-request station lookups

**Today.** Every station takes two upstream calls (`regione`, then `dettaglioStazione`), cached for 60 minutes.
Resolving a board fans out to hundreds of calls when the cache is cold. That is the main cause of the 403 blocks and
of poor horizontal scaling, since every instance has its own cache.

**Unknown.**
- Whether `elencoStazioni/{codiceRegione}` returns every station with coordinates and names.
- Which region codes exist, how big the responses are, and how often they change.
- How it treats stations in region `0`. Real ones exist: see fixture `dettaglioStazione_S01139_0`. They must be told
  apart from the "Y" placeholder returned for unknown stations.
- Whether foreign stations (`origineEstera`/`destinazioneEstera`) appear anywhere.

**Look at.** `elencoStazioni` for every region, once. Compare it with `dettaglioStazione` for a sample of stations.

**Likely fix.**
- Load the registry at startup, or keep a committed snapshot refreshed on a schedule.
- Resolve ids and names locally (this also fixes item 5), and drop the region lookups.

## 8. Rate limit budget

**Today.** No backoff, circuit breaker or overall call budget. About 500 calls in 45 seconds triggered a block lasting
about 60 minutes. The block depends on the client (plain curl was blocked while the app's Ktor client was not).

**Unknown.** The threshold (calls per window), how long blocks last, and what the block is keyed on (IP, client,
headers). Find out only through normal use, never by spoofing.

**Likely fix.**
- A shared limiter on the Ktor client, sized below the observed threshold.
- A circuit breaker that turns 403s into fast 503s for a while, instead of hammering the provider.

## 9. Smaller questions

- **`dataPartenzaTreno` / `RunId.departureDate`.** Is it always midnight Europe/Rome, including on DST change days?
  `RunId` exposes it as epoch millis; a `LocalDate` would be cleaner, but only if the mapping is exact.
- **`ultimoRilev` and `oraUltimoRilevamento`.** What each one means (last position report? last data update?), since
  `lastUpdateFromProvider` exposes `ultimoRilev`.
- **Fields that differ between endpoints.** Spot-check fields read from both `partenze` and `andamentoTreno`
  (`orarioArrivo`, `ritardo`, `provvedimento`) for disagreements.

---

## Decisions needed (no investigation)

These came out of the same review but are product choices rather than API questions:

- **Result order.** `/departures` and `/trips` return items in the order their train lookups finish, not by time.
  - Sorting by departure time means waiting for every item, which removes the benefit of streaming over SSE.
  - Options:
    - Sort JSON responses only, and keep SSE streaming.
    - Sort everywhere.
    - Document the response as unordered.
- **Time zone on the wire.** Times are sent as UTC with no offset (`2026-01-23T21:15`), which is not valid RFC 3339
  `date-time`. Adding `Z` (and accepting offsets in `date`) is more correct, but it breaks clients that parse the
  current format.
