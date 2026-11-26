# ViaggiaTreno: open questions

Improvements from the architecture review that depended on how the ViaggiaTreno API actually behaves. The API is
unofficial and inconsistent: the same concept has different field names across endpoints, and a field present in one
response can be `null` in another.

Most questions were answered on 2026-11-25. The sources are the official site's own client code and live calls, from
[`viaggiatreno-web-client-reverse-engineering.md`](viaggiatreno-web-client-reverse-engineering.md) and follow-up
checks: 16 boards (453 entries), 35 runs, and about 350 calls.

How to investigate what is still open:
- Use the app's client: `./gradlew providerContractTest`, or a throwaway test built on `ViaggiaTrenoClients`. Keep
  to about 1 request per second, and never spoof headers.
- Record what you learn as fixtures in `src/test/resources/providers/viaggiatreno/`.
- Pin each confirmed behaviour with a provider contract test (`ViaggiaTrenoContractTests`).

---

## Resolved

| # | Question | Finding | Change |
|---|---|---|---|
| 1 | What `provvedimento` and `ritardo` mean | `provvedimento` was only ever 0 or 1, and `tipoTreno` only PG or ST. `ritardo` is the train's current delay in minutes: null before departure, negative when early. | Status is `CANCELLED` for ST/1, `DELAYED` when `ritardo > 0`, otherwise `ON_TIME` (`46500c5`). |
| 2 | What a cancelled train looks like | `andamentoTreno` answers **200 with the full stop list** (not empty, not 204), with `ritardo` null and normal scheduled times. On boards it is `provvedimento: 1`, `compRitardo` "Cancellato", `ritardo` null. | Cancelled runs map normally, so trip searches return them with status `CANCELLED` (`46500c5`). Fixed the provider spec, which said the list was empty. |
| 3 | `/trips` dropping unverifiable trains | Our own model, not the API. The journey planner (`soluzioniViaggioNew`) answers 404 for every input, so it is no alternative. | `unverifiedDepartures` count and an SSE `summary` event (`e23b05b`). |
| 4 | What "expected" means at each stop | The site takes the scheduled time from the stop (`partenza_teorica` / `arrivo_teorico`). Until the event happens, it forecasts scheduled + the train's `ritardo`; the stop's own `ritardo` only describes past events. Pre-formatted `comp*` times can disagree with the raw ones. | The same rule at every call, and no more `HH:mm` parsing (`0c56063`). |
| 5 | Resolving a destination by name | `codDestinazione` was null on 200 of 200 board entries. `destinazione` is the exact `nomeLungo` of the last stop (19 of 19), and `cercaStazione` matches by prefix. | Exact name match only (`0374cfb`). |
| 6 | Time window of a board | About [t − 15 min, t + 105 min], sorted by time. Past slots are empty. Runs exist **only for today** (other days answer 204). Boards for another day can still be full: on 2026-11-25 at 22:50, tomorrow's 08:00 board for Saronno (S01933) had 54 departures, but all 194 `andamentoTreno` calls for tomorrow answered 204. So a trip search for another day finds only unverified departures (see D). | Documented on the `date` parameter (`e82a151`). |
| 9 | `ultimoRilev`, `dataPartenzaTreno` | `ultimoRilev` equals `oraUltimoRilevamento`: the last position detection, null before departure. `dataPartenzaTreno` is local midnight in Rome, and any millis within that day work. | `lastUpdateFromProvider` documented. |
| 7 | A local station registry | No single list is complete (`elencoStazioni/0` misses most stations, and region 0 details lack names off the national map), but **regions 0..22 together** covered all 272 station codes seen. That is 3,362 stations with names, about 1.8 MB in 23 calls. 48 stations have no coordinates, and the 102 listed in several regions carry identical data. | `ViaggiaTrenoStationRegistry`, loaded once a day, with direct lookups as fallback. |
| – | `orarioPartenza` on boards | It is the departure from the board's own station: equal to that stop's `partenza_teorica` (18 of 18). | Already relied on; no change. |

---

## Still open

### A. Partial cancellations and diversions

Never observed: no `provvedimento` 2/3 and no `tipoTreno` PP/SI/SF/DV, and `fermateSoppresse` was always empty.

Implemented from the ViaggiaTreno site's code, which treats a stop with `actualFermataType == 3` as cancelled:
- such a stop gets status `CANCELLED`;
- the run does not serve it, as origin or as destination;
- the train's destination is its last stop still served.

**Still to confirm,** on a partially cancelled or diverted train (strikes, works):
- that `fermate` keeps the cancelled stops, marked 3;
- what `provvedimento`/`tipoTreno`/`subTitle` say, and whether the run should get a status such as
  `PARTIALLY_CANCELLED` or `DIVERTED`.

Record that response as a fixture and pin it with a contract test.

### B. Rate limit budget

No 403 in about 330 calls at about 1 request per second. Earlier, a cold-cache run of about 500 calls in 45 seconds
(about 11 per second, `providers.viaggiatreno.concurrency=10`) caused a block that lasted about 60 minutes. So the
threshold lies somewhere between those two rates, and the block lasts long.

**Decide:**
- a shared limiter on the Ktor client, below the observed threshold;
- a circuit breaker that turns 403s into fast 503s for a while;
- whether a cold `/departures` (about 1 + N + 2M calls) is acceptable at the resulting latency.

**Look at, only through normal use:** the rate at which blocks start.

### C. Board destination known without the run

A departure whose run is unknown is counted as unverified, even when the board's `destinazione` is the searched
station, which it certainly reaches. Now that the name resolves only on an exact match (5), that case could become a
trip built from the board's times. This is a product decision, not an API question.

### D. Trip searches for another day

Because runs exist only for today (6), a `/trips` search for tomorrow answers with no trips, and every departure is
counted in `unverifiedDepartures`. For example, Saronno → Milano Cadorna on 26/11 at 08:00 gave 0 trips and 54
unverified departures.

The same thing happens for a search made today, late in the evening. A board at 23:18 Rome time includes trains
leaving after midnight. They are tomorrow's runs (`dataPartenzaTreno` = tomorrow), so they answer 204 until midnight.
For example, Saronno/Saronno Sud → Cadorna/Porta Venezia at 23:18 on 2026-11-25 gave 5 trips and 4 unverified
departures: the 4 board entries from 00:06 onwards.

**Options:**
- reject dates other than today with a 400 (this does not help the late-evening case);
- use today's run of the same train as a timetable, shifted to the requested day, and flag the trip as planned;
- build trips from the board alone when `destinazione` is the searched station.

**How the official site handles it** (its `js/app.js`, and 15 live calls on 2026-11-25): it never shows a run for
another day.
- Every way into a run goes through `apriDettaglioTreno`, which makes one `andamentoTreno` call with the given date.
- Train-number search (`cercaNumeroTrenoTrenoAutocomplete`) lists today's runs only.
- The journey planner turns train details off for any other day: `shouldOpenTrainDetail = this.data.isSame(moment(), 'day')`.
- The station board UI can't go past the end of today. A hand-built `partenze` call for tomorrow does return a full
  board, with tomorrow's `dataPartenzaTreno`.
- `tratteCanvas` for tomorrow answers 204 too.
- `soluzioniViaggioNew` answers 404 even in the site's exact format (`/1933/1066/2026-11-26T08:00:00`). When it
  worked, it returned legs only, with no intermediate stops.
- `cercaNumeroTreno/{n}` returns only today's first match, which can be another train with the same number (12018
  gave Genova Brignole rather than Varese Nord).

There is no timetable endpoint, so for another day the only stop list available is today's run of the same train.

**Today's run as a template:** 8 Cadorna-bound trains were taken from tomorrow's Saronno board.
- For 7, today's run existed, and its scheduled Saronno departure matched tomorrow's board to the minute.
- For 1 (1126/S01765), there was no run today: the train runs on Thursdays but not Wednesdays.
- Stops and times elsewhere can't be checked, because tomorrow's board only gives the time at the board station.

A possible rule: use today's run only when it matches the board entry on number, origin and time of day at the board
station, and mark the trip as planned. Otherwise, the departure stays unverified.

---

## Decisions needed (no investigation)

- **Result order.** `/departures` and the trip searches return trips in the order their train lookups finish, not by
  time. Boards themselves are sorted.
  - Sorting means waiting for every train, which loses the benefit of streaming over SSE for departures. Trip-search
    JSON is already collected, so it could be sorted at no cost.
- **Time zone on the wire.** Times are sent as UTC with no offset (`2026-01-23T21:15`), which is not valid RFC 3339
  `date-time`. Adding `Z`, and accepting offsets in `date`, is more correct but breaks clients that parse the current
  format.
