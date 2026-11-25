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
| 6 | Time window of a board | About [t − 15 min, t + 105 min], sorted by time. Boards are **live data**: past slots are empty, far-future ones nearly empty. Runs exist **only for today** (other days answer 204). | Documented on the `date` parameter (`e82a151`). |
| 9 | `ultimoRilev`, `dataPartenzaTreno` | `ultimoRilev` equals `oraUltimoRilevamento`: the last position detection, null before departure. `dataPartenzaTreno` is local midnight in Rome, and any millis within that day work. | `lastUpdateFromProvider` documented. |
| – | `orarioPartenza` on boards | It is the departure from the board's own station: equal to that stop's `partenza_teorica` (18 of 18). | Already relied on; no change. |

## Closed without change

### 7. A local station registry

Not viable with the available endpoints:
- `elencoStazioni/0` has 723 entries for only 609 distinct codes, 135 of them without coordinates. Of 302 station codes
  seen on boards and in runs, **213 are missing** from it (e.g. Firenze Campo Marte, Fiumicino Aeroporto).
- Skipping the region lookup does not work either: `dettaglioStazione/{id}/0` returns `localita: null` for stations
  off the national map (e.g. Genova Brignole, Aosta), and only the right region fills it. For an unknown id it returns
  the "Y" placeholder.

The per-request `regione` + `dettaglioStazione` lookups, cached for 60 minutes, stay. Revisit if the per-region lists
(`elencoStazioni/{1..22}`) turn out to cover every station together; that has not been checked.

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

---

## Decisions needed (no investigation)

- **Result order.** `/departures` and the trip searches return trips in the order their train lookups finish, not by
  time. Boards themselves are sorted.
  - Sorting means waiting for every train, which loses the benefit of streaming over SSE for departures. Trip-search
    JSON is already collected, so it could be sorted at no cost.
- **Time zone on the wire.** Times are sent as UTC with no offset (`2026-01-23T21:15`), which is not valid RFC 3339
  `date-time`. Adding `Z`, and accepting offsets in `date`, is more correct but breaks clients that parse the current
  format.
