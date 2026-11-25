# ViaggiaTreno: how the official web client uses the API

Reverse-engineered from the official site <http://www.viaggiatreno.it/infomobilita/index.jsp> on
**2026-11-25** (evening, Europe/Rome). This describes what the site's JavaScript sends and how it reads
each response field. It does not describe this repository's usage.

Method:
- Read the site's JavaScript: `rest-jsapi` (generated RESTEasy client), `app.js` (v7.1.30), `main.js`,
  `map.js`, `vt.canvasV2.js` and the Handlebars templates embedded in `index.jsp`.
- Drove the page in headless Chromium (Playwright) and recorded every `/resteasy/` request and response.
- Called the endpoints directly to test edge cases, using different stations and trains for each test
  (see the appendix).

Conventions:
- "Site uses" means a field the page code actually reads.
- A field not listed under an endpoint is present in the payload but never read by the site.

---

## 1. Transport and general conventions

| Topic | Finding |
|---|---|
| Base URL | `http://www.viaggiatreno.it/infomobilita/resteasy/...`. The page sets `REST.apiURL = protocol//hostname + "/infomobilita"`. |
| Client | `rest-jsapi` is a RESTEasy-generated JS client (`ViaggiaTrenoService.*`, `NewsService.*`). Some calls bypass it and use `$.ajax` with hand-built URLs. |
| Auth | None. Session cookies are set but not needed; plain `curl` with any browser-like `User-Agent` works. |
| Bot protection | Akamai answers **403 "Access Denied"** on `index.jsp` when the UA contains `HeadlessChrome`; a normal Chrome UA passes. API calls with `curl -A "Mozilla/5.0"` returned 200 throughout testing (about 300 calls, at most about 1 request/s). |
| Sync vs async | `getLabels`, `getElencoStazioni`, `getElencoTratte` and `getDatiMeteo` are called **without** a `$callback`, which makes the generated client use a **synchronous** XHR and return the parsed body. Every other call is async. |
| Path encoding | Path params go through `encodePathSegment`, so spaces become `%20` and `’` becomes UTF-8 percent-encoded. |
| Content types | Lookups that return lists (`autocompleta*`, `cercaNumeroTrenoTrenoAutocomplete`) are `text/plain`, one `name|value` per line with a trailing `\n`. RSS endpoints are `text/plain` HTML fragments. Everything else is `application/json`. Errors are `text/html` (or `text/plain`) bodies with the literal text `Error\n`. |
| Empty / not found | Most endpoints answer **204 No Content** with an empty body for "nothing found". Searches return 200 with an empty body or `[]`. |
| `StampaTreno` charset | JSON declared as `charset=ISO-8859-1`; Italian accented characters arrive as HTML entities or Latin-1 bytes. |

### 1.1 Identifiers

- **Station code**: `S` + 5 digits (e.g. `S01700` Milano Centrale). Without the `S` prefix the run endpoints return 204.
- **NTS station id** (only from `autocompletaStazioneNTS`): `830` + 6 digits. It is the same number as the station code
  (`S05059` → `830005059`); `83` is Italy's UIC country code. The site never uses it.
- **Train run key**: `(codOrigine, numeroTreno, dataPartenzaTreno)`. Train numbers are **not unique**, even on the same day:
  `2241` was both a Milano Cadorna run and a Milano Centrale run. The site always carries all three values together.
- **`dataPartenzaTreno`**: epoch millis of **local midnight (Europe/Rome)** of the run's start day
  (`1795561200000` = 2026-11-25 00:00 CET).
- **Region ids** (from `map.js` and `/regione`):

  | id | region | id | region | id | region |
  |---|---|---|---|---|---|
  | 0 | Italia (whole network) | 8 | Emilia Romagna | 16 | Puglia |
  | 1 | Lombardia | 10 | Friuli Venezia Giulia | 17 | Calabria |
  | 2 | Liguria | 11 | Marche | 18 | Campania |
  | 3 | Piemonte | 12 | Veneto | 19 | Abruzzo |
  | 4 | Valle d'Aosta | 13 | Toscana | 20 | Sardegna |
  | 5 | Lazio | 14 | Sicilia | 21 | Prov. di Trento |
  | 6 | Umbria | 15 | Basilicata | 22 | Prov. di Bolzano |
  | 7 | Molise | | | | |

### 1.2 Times

- Instants are **epoch milliseconds**. The site formats every instant with
  `moment(ms).tz("Europe/Rome").format("HH:mm")` (`formatTime`). `null`/`undefined` becomes `"--:--"`.
- The `comp*` fields are **pre-formatted strings** (`"20:35"`). The site prefers them for scheduled times on
  boards and in the train header (§4, §5).
- `oraPartenzaEstera` / `oraArrivoEstera` are **not instants**. They are a time of day in millis relative to
  1970-01-01 (CET). Observed values include `-3180000` (= 00:07), `16020000` (05:27) and `62820000` (18:27).
  They are only meaningful through `formatTime`, which yields Rome `HH:mm`.
- Some endpoints use other time types: `dettagliTratta` train entries carry `orarioPartenza`/`orarioArrivo` as
  **date strings** (`"2026-11-25"`), and `andamentoTreno.dataPartenza` is `"2026-11-25 00:00:00.0"`.

### 1.3 Localised `comp*` arrays

Many fields are **9-element arrays**, one entry per UI language. The site picks one with
`indiceTraduzione = TRADUZIONI[cookie LANG]`:

| index | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| lang | it | en | de | fr | sp | ro | jp | zh | ru |

- The strings contain **HTML entities** (`Versp&#228;tung`, `&icirc;nt&acirc;rziere`). The templates render them with
  triple-stash `{{{ }}}`, so they are treated as HTML.
- Some arrays have **`null` for ro/jp/zh/ru** (e.g. `Cancellato` and `Mancato rilevamento`).
- One known malformed entity: `Annul&eacute` (no semicolon).

### 1.4 Delay-mode flags (`attivaRitardo*`)

Every train object (board entries, `andamentoTreno`, `dettagliTratta` entries) carries four booleans that select
how the delay is shown:

| flag | meaning (from UI labels) | what the site shows |
|---|---|---|
| `attivaRitardoRR` | real delay from detections | `compRitardo[lang]` (board) / `compRitardoAndamento[lang]` (detail) + icon `compImgRitardo2` (board) / `compImgRitardo` (detail) |
| `attivaRitardoNONE` | no delay information (observed exactly when `nonPartito: true`) | same as RR, which in practice is the "non partito" text and icon |
| `attivaRitardoRA` | "Dato in aggiornamento" (circulation event in progress) | `compRitardoCorsaAttualizzataAndamento[lang]` + `compImgRitardoCorsaAttualizzata`; if `nonPartito`, also `ritardoAttualizzato` + " Min."; plus the label "In aggiornamento" |
| `attivaRitardoPR` | "Ritardo stimato" (estimated delay) | same, but `ritardoPromessa` + label "Stimato"; the last-detection block is replaced by `orarioRilevamentoPromessaFormatted`, and all effective times become `--:--` |

- If `compImgRitardoCorsaAttualizzata == "/vt_static/img/avvisi/alert3.png"`, the site draws it as a small warning
  icon and suppresses the RA/PR sub-labels.
- Values observed across 349 board entries and 35 runs:
  - RR=true for every train that had started (218); NONE=true for every not-departed train (131).
  - RA and PR were never true.
  - `decisioneAttualizzata` was `"SNN--"` (with RR), `"NNN--"` (with NONE) or once `"SNSN-"`. The site only passes it to templates that never read it.

### 1.5 Status images used as data

The site renders these paths directly as `<img>`. Each one also encodes a status:

| path (`/vt_static/img/legenda/icone_legenda/…` or `/vt_static/img/avvisi/…`) | meaning (legend) |
|---|---|
| `regolare.png` | on time / regular |
| `nonpartito.png` | not departed yet |
| `ritardo01/02/03.png` | delay band (national: >15/>30/>60 min; regional: >5/>15/>30 min) |
| `cancellazione.png` | train with cancellations |
| `numerazioni.png` (inside `compImgCambiNumerazione`) | train has several numbers |
| `avvisi/alert3.png` | "Mancato rilevamento": missing detection |

---

## 2. Endpoint catalogue

All paths are relative to `/infomobilita/resteasy`. "UI" is where the site calls it.

| Endpoint | UI | Status today |
|---|---|---|
| `GET /viaggiatreno/autocompletaStazione/{text}` | train/station search box, "print arrival" form, journey form inside train tab | works |
| `GET /viaggiatreno/autocompletaStazioneImpostaViaggio/{text}` | "Imposta viaggio" (journey) form | works |
| `GET /viaggiatreno/autocompletaStazioneNTS/{text}` | defined in client, **never called** | works |
| `GET /viaggiatreno/cercaStazione/{text}` | defined, **never called** | works |
| `GET /viaggiatreno/cercaNumeroTrenoTrenoAutocomplete/{numero}` | train-number search, journey-solution detail | works |
| `GET /viaggiatreno/cercaNumeroTreno/{numero}` | links inside journey solutions and infomobility HTML | works |
| `GET /viaggiatreno/partenze/{codStazione}/{orario}` | station board, "connections" in train tab | works |
| `GET /viaggiatreno/arrivi/{codStazione}/{orario}` | station board | works |
| `GET /viaggiatreno/andamentoTreno/{codOrigine}/{numeroTreno}/{dataPartenza}` | train tab | works |
| `GET /viaggiatreno/tratteCanvas/{codOrigine}/{numeroTreno}/{dataPartenza}` | train tab route strip | works |
| `GET /viaggiatreno/elencoStazioniCitta/{codStazione}` | station tab (sibling-station selector) | works |
| `GET /viaggiatreno/regione/{codStazione}` | station tab, train tab (map button) | works |
| `GET /viaggiatreno/dettaglioStazione/{codStazione}/{codRegione}` | station tab title, city map | works |
| `GET /viaggiatreno/getCoordinateStazione/{codStazione}` | defined, never called | works |
| `GET /viaggiatreno/coordinateCitta/{codStazione}` | defined, never called | works |
| `GET /viaggiatreno/soluzioniViaggioNew/{orig}/{dest}/{yyyy-MM-ddTHH:mm:ss}` | journey solutions | **404 `Error` for every input tried** (site shows "Servizio momentaneamente non disponibile") |
| `GET /viaggiatreno/elencoStazioni/{codRegione}` | map markers | works |
| `GET /viaggiatreno/elencoTratte/{idRegione}/{zoom}/{categorie}/{catAV}/{timestamp}` | map line segments | works |
| `GET /viaggiatreno/dettagliTratta/{idRegione}/{idTrattaAB}/{idTrattaBA}/{categorie}/{catAV}` | click on a map segment | works |
| `GET /viaggiatreno/datimeteo/{codRegione}` | map weather mode | works |
| `GET /viaggiatreno/statistiche/{timestamp}` | header counters | works |
| `GET /viaggiatreno/news/{codRegione}/{lingua}` | "Ultime notizie" box | works (returned 1 old item for region 0/it, `[]` for others) |
| `GET /viaggiatreno/infomobilitaRSS/{isInfoLavori}` | Infomobilità / works pages | works |
| `GET /viaggiatreno/infomobilitaRSSBox/{isInfoLavori}` | home-page box | works |
| `GET /viaggiatreno/infomobilitaTicker` | marquee | works |
| `GET /viaggiatreno/language/{lang}` | all UI labels | works |
| `GET /viaggiatreno/property/{name}` | defined, never called | 204 for `test` |
| `GET /news/infomobility?trainNumber=&region=&evidence=` | train tab "Infomobilità" button | works |
| `GET /news/smartcaring?commercialTrainNumber=&originCode=&searchDate=` | train tab "Smart Caring" button | works (always `[]` in tests) |
| `POST /infomobilita/StampaTreno` (not under resteasy) | "Stampa arrivo" (print arrival certificate) | works |

---

## 3. Search endpoints

### 3.1 `autocompletaStazione/{text}` and `autocompletaStazioneImpostaViaggio/{text}`

- **Response**: `text/plain`, lines `NOME STAZIONE|S01234\n`. An empty result is an empty body.
- **Matching**: case-insensitive **prefix** match on the station name.
  - `reggio` → 18 lines.
  - `S. ` → only names literally starting with "S. ".
  - `z` → 11 lines.
  - `basel` → nothing; foreign stations are not indexed.
  - `sant'` → empty.
- The two endpoints returned identical results for every query tried.
- **Site usage**:
  - It splits the body on `\n` and each line on `|`: part 0 is the label shown, part 1 is the code it keeps (`data('dati')[label] = code`).
  - The journey form loops to `length - 1`, relying on the trailing newline. The search box trims first.
  - The UI only queries once at least 2 characters are typed. A digits-only term in the main search box is treated as a train number instead (§3.3).
  - Selected `label|code` pairs are kept in `localStorage` (`lastSearchedItems*`), at most 3 each.

### 3.2 `autocompletaStazioneNTS/{text}`, `cercaStazione/{text}` (unused by the site)

- **NTS**:
  - Same line format, with ids `830xxxxxx` and sometimes different names (`REGGIO CAL.(T.STAZ.)`).
  - Returns stations the other indexes don't (`S. ANGELO IN FORMIS`).
  - An apostrophe in the query gives **500 `Error`**.
- **`cercaStazione`**: JSON `[{nomeLungo, nomeBreve, label, id}]`, with the same matches as `autocompletaStazione`.
  `label` is the city name and can be `null`.

### 3.3 `cercaNumeroTrenoTrenoAutocomplete/{numero}`

- **Response**: `text/plain` lines `"{num} - {ORIGIN NAME} - {dd/MM/yy}|{num}-{codOrigine}-{dataPartenzaTreno}\n"`.
  For example: `2241 - MILANO CADORNA - 25/11/26|2241-S01066-1795561200000`.
- **Site usage**:
  - **0 lines**: shows an error.
  - **1 line**: splits part 1 on `-` into `(numero, codOrigine, dataPartenza)` and opens the train tab.
  - **>1 line**: shows the left parts as a disambiguation dropdown; on select it does the same split.
  - From journey-solution rows it always takes the **first** line, ignoring any ambiguity.
- **Observed**:
  - Only today's runs are listed (`25/11/26`).
  - Numbers that don't run today (`1`, `2`, `600`, `10911`) return an empty body with status 200.

### 3.4 `cercaNumeroTreno/{numero}`

- **Response (JSON)**: `{numeroTreno, codLocOrig, descLocOrig, dataPartenza:"2026-11-25", corsa:"02241A", h24, tipo:"PG", millisDataPartenza:"1795561200000", formatDataPartenza:null}`.
  - `numeroTreno` and `millisDataPartenza` are **strings**.
- It returns **a single match even when the number is ambiguous**: `2241` gave only the Cadorna run.
- Unknown numbers give **204** with `text/plain`.
- **Site usage**: links inside infomobility HTML (`getSchedaTreno`) and journey solutions (`apriDettaglioSoluzione`).
  - It reads `codLocOrig` and `dataPartenza`, and passes `dataPartenza` (a `yyyy-MM-dd` string) as the run date to `andamentoTreno`.
  - The infomobility variant ignores the response, uses an origin code taken from the news HTML, and passes `new Date().getTime()` (now, not midnight) as the date. Both work, because the date is resolved to the day (§5.3).

---

## 4. Station boards: `partenze` / `arrivi`

### 4.1 Request

`GET /viaggiatreno/partenze/{codStazione}/{orario}` (same shape for `arrivi`).

- The site passes a JS `Date` object, so `orario` is **`Date.prototype.toString()`**, for example
  `Wed Nov 25 2026 20:39:47 GMT+0100 (Ora standard dell’Europa centrale)`. The timezone name in brackets
  depends on the browser locale.
- Accepted variants, all 200 with identical results:
  - with the bracketed name in Italian or English;
  - without the bracketed name;
  - without the `GMT+hhmm` offset (interpreted as Rome local time);
  - with `GMT+0000` (the offset is **honoured**: 08:00 `GMT+0000` gives the window starting 09:00 Rome time).
- Rejected with **400 `Error`**: epoch millis, ISO-8601, garbage.
- The site's station tab starts at "now" and moves in ±2 h steps (`DELTA_ORE_RICERCA = 2`). It cannot go earlier
  than the time the tab was opened, nor past the end of the current day.

### 4.2 Response window (measured)

- The response is sorted by scheduled time (departures by `orarioPartenza`, arrivals by `orarioArrivo`).
- The window is about **[t − 15 min, t + 105 min]**:
  - Milano C. 08:00 tomorrow → 07:45–09:45 (38 trains).
  - Roma Termini arrivals 07:00 → 06:48–08:45.
  - Bari C. 12:00 in five days → 11:50–13:45.
  - Bologna C. 23:30 → 23:18–00:44 (+1 day).
- **The boards are live data, not a timetable**:
  - Requesting a time earlier today returns only trains that haven't finished with the station. Firenze SMN at
    now−45 min and now−90 min both start at about now−27 min; now−180 min gives `[]`.
  - Venezia S. Lucia 10:00 (10 h before now) → `[]`. Palermo C. three days ago → `[]`.
  - A month ahead (Verona P.N., 25 Dec 09:00) returned a single, unrelated, **cancelled** train from **today** at 21:26.
  - A night-time slot with no service (Genova Brignole 03:00) → `[]`.
- Around midnight the list mixes runs whose `dataPartenzaTreno` is today and tomorrow (Bologna 23:30). Arrivals early
  tomorrow morning include runs that started today (overnight trains).
- An empty result is `200 []`.

### 4.3 Fields the site uses (`manageWebPartenze` / `manageWebArrivi`)

| Site variable | Departure field | Arrival field | Notes |
|---|---|---|---|
| row `data-treno` | `numeroTreno` | same | run key for the click |
| row `data-origine` | `codOrigine` | same | **train origin**, not this station |
| row `data-datapartenza` | `dataPartenzaTreno` | same | |
| train label | `compNumeroTreno` | same | e.g. `"REG 2284"`, `" FR 9674"` (leading space on FR) |
| renumbering icon | `compImgCambiNumerazione` | same | HTML: `&nbsp;&nbsp;` or `&nbsp;&nbsp;<img …numerazioni.png>` |
| destination / origin | `destinazioneEstera ?? destinazione` | `origineEstera ?? origine` | on departures `origine` is `null`; on arrivals `destinazione` is `null` |
| scheduled time | `compOrarioPartenza` | `compOrarioArrivo` | pre-formatted `HH:mm`; raw millis are in `orarioPartenza` / `orarioArrivo` |
| scheduled platform | `binarioProgrammatoPartenzaDescrizione` | `binarioProgrammatoArrivoDescrizione` | free text: `"2 Tronco Ovest"`, `"X"` |
| actual platform | `binarioEffettivoPartenzaDescrizione` | `binarioEffettivoArrivoDescrizione` | e.g. `"IITR O"`; `null` until assigned |
| coach orientation | `compOrientamento[lang]` | same | `"--"` or `"Executive in testa"` / `"Executive in coda"` |
| delay text | `compRitardo[lang]` | same | `"in orario"`, `"ritardo N min."`, `"non partito"`, `"Cancellato"`, `"Mancato rilevamento"` |
| delay icon | `compImgRitardo2` | same | **not** `compImgRitardo`; see §7 |
| departed / arrived flag | `compInStazionePartenza[lang]` | `compInStazioneArrivo[lang]` | `"Partito"` / `"Arrivato"` or `""` |
| RA/PR display | `attivaRitardo{RR,RA,PR,NONE}`, `ritardoAttualizzato`, `ritardoPromessa`, `compRitardoCorsaAttualizzataAndamento[lang]`, `compImgRitardoCorsaAttualizzata`, `nonPartito` | same | §1.4 |
| foreign-departure guard | `oraPartenzaEstera`, `circolante` | same | if `oraPartenzaEstera != null && !circolante` and `formatTime(oraPartenzaEstera) < formatTime(now)` (string compare of `HH:mm`), the **whole delay cell is hidden** |

- Clicking a row opens the train tab with `(numeroTreno, codOrigine, dataPartenzaTreno)`.
- Present but unused by the site: `ritardo` (int minutes), `inStazione`, `arrivato`, `circolante` (except in the foreign guard), `orarioPartenza`/`orarioArrivo`, `millisDataPartenza` (string on departures, `null` on arrivals), `partenzaTreno`, `ultimoRilev`, `categoria`, `categoriaDescrizione`, `codiceCliente`, `tipoProdotto`, `provvedimento`, `riprogrammazione`, `haCambiNumero`, `binario*Codice` / `*Tipo`, `compTipologiaTreno`, `compClassRitardo*`, `progr`.

### 4.4 Values observed (349 distinct runs, 8 big stations, now ±2 h)

- **`codiceCliente`** (operator) vs `categoriaDescrizione` / `tipoProdotto`:

  | codiceCliente | categoriaDescrizione | tipoProdotto | notes |
  |---|---|---|---|
  | 1 | `" FR"`, `" FB"`, `"EC"`, `"EC FR"` | 100 (FR), 102 (FB), 0 (EC) | Trenitalia long-distance |
  | 2 | `REG` | 0 | Trenitalia regional |
  | 4 | `IC`, `ICN` | 0 | Intercity |
  | 18 | `REG` | 0 | regional, Tuscany and Bologna area |
  | 63 | `REG` | 0 | Lombardy regional (Trenord) |

  Frecce have `categoria: ""` with `categoriaDescrizione: " FR"` (leading space).
- `compRitardo[0]` values: `ritardo N min.` (171), `non partito` (127), `in orario` (39), `Mancato rilevamento` (8), `Cancellato` (4).
- **Cancelled entries** have `compRitardo = "Cancellato"`, `compImgRitardo2 = …cancellazione.png`, **`compImgRitardo = …regolare.png`**, `provvedimento: 1`, `riprogrammazione: null` and `ritardo: null`.
- **"Mancato rilevamento"** entries have both images set to `…/avvisi/alert3.png`, and `compRitardoAndamento` is all `null`.
- Negative delays are shown as "anticipo" ("con un anticipo di 1 min.").

---

## 5. Train run: `andamentoTreno`

### 5.1 Request

`GET /viaggiatreno/andamentoTreno/{codOrigine}/{numeroTreno}/{dataPartenza}`

The site uses the triple taken from a board row, an autocomplete line, or `cercaNumeroTreno`.

### 5.2 Response (top level)

Top-level object fields (FR 9674 example values in brackets):
- Train identity and classification:
  - `tipoTreno` (`"PG"`; see below), `codiceCliente`, `categoria`, `tipoProdotto`.
  - `compNumeroTreno` (`" FR 9674"`), `numeroTreno`.
- Run day and endpoints:
  - `dataPartenza` (string), `dataPartenzaTreno` (millis midnight), `dataPartenzaTrenoAsDate` (`"2026-11-25"`).
  - `idOrigine`, `idDestinazione`, `origine`, `destinazione`, `origineZero`, `destinazioneZero`.
  - `origineEstera`, `destinazioneEstera`, `oraPartenzaEstera`, `oraArrivoEstera`.
- Endpoint times:
  - Millis: `orarioPartenza`, `orarioArrivo`, `orarioPartenzaZero`, `orarioArrivoZero`.
  - Pre-formatted: `compOrarioPartenza`, `compOrarioArrivo`, `compOrarioPartenzaZero`, `compOrarioArrivoZero`,
    `compOrarioPartenzaZeroEffettivo`, `compOrarioArrivoZeroEffettivo`, `compDurata` (`"2:50"`).
- Delay and last detection:
  - `ritardo` (int, current delay), `oraUltimoRilevamento` (millis), `stazioneUltimoRilevamento` (name, or `"--"` if none),
    `compOraUltimoRilevamento`.
  - Localised texts: `compRitardo[]`, `compRitardoAndamento[]`, `compRitardoCorsaAttualizzataAndamento[]`.
  - Images: `compImgRitardo`, `compImgRitardo2`, `compImgRitardoCorsaAttualizzata`.
  - Mode flags: `attivaRitardo*`, `ritardoAttualizzato`, `ritardoPromessa`, `orarioRilevamento*`.
- State: `arrivato`, `nonPartito`, `circolante`, `inStazione`.
- Measures:
  - `provvedimento` (int), `hasProvvedimenti`, `subTitle` (string, e.g. `"Treno cancellato"`).
  - `cambiNumero[]`, `haCambiNumero`, `fermateSoppresse[]`, `anormalita[]`, `provvedimenti[]`, `segnalazioni[]`.
- Other: `descrizioneVCO`, `orientamento`, `descOrientamento[]`, `compOrientamento[]`, `motivoRitardoPrevalente`, `materiale_label`.
- `fermate[]`: the ordered stops (§5.4).

Coded values:
- `tipoTreno` observed: **`PG`** (regular, 33/35) and **`ST`** (whole train cancelled, 2/35; it comes with
  `provvedimento: 1`, `subTitle: "Treno cancellato"`, `nonPartito: true` and `stazioneUltimoRilevamento: "--"`).
  The site only special-cases `"ST"`.
- In 35 sampled runs, the lists `cambiNumero`, `fermateSoppresse`, `anormalita`, `provvedimenti`, `segnalazioni`,
  `corrispondenze` and `servizi` were **always empty**.

### 5.3 Behaviour

- **Only today's runs exist.**
  - Yesterday's, tomorrow's and 3-days-ago runs of a train that ran today all give **204**.
  - A tomorrow run that appears on a board for tomorrow (EC 41 Domodossola) also gives 204 from `andamentoTreno`.
- **The date is resolved to the day.**
  - `dataPartenzaTreno + 1h` → 200, same run.
  - `"2026-11-25"` → 204 here. The site does pass such a string from `cercaNumeroTreno`; that path wasn't exercised end-to-end.
  - `abc` → 204.
- **The origin must be the true origin.** An intermediate stop's code gives 204, and so does a code without the `S`.
- An unknown number gives 204. The empty body has `Content-Type: text/plain`.

### 5.4 `fermate[]` (stops)

Each stop object has these fields:
- Identity: `id` (station code), `stazione` (name), `codLocOrig` (train origin), `progressivo`.
- Stop kind: `tipoFermata`: **`P`** origin, **`F`** intermediate, **`A`** final.
- Scheduled times:
  - `programmata`: the scheduled time of the event that matters, i.e. departure for `P` and arrival for `F`/`A`. This held for every stop checked.
  - `partenza_teorica`, `arrivo_teorico`.
  - `programmataZero`, `partenzaTeoricaZero`, `arrivoTeoricoZero`: always `null`.
- Actual times:
  - `partenzaReale`, `arrivoReale`.
  - `effettiva` = **the latest actual event** (departure if departed, else arrival). This held for 186/186 stops with real times.
  - `ritardo` = the delay of that latest event; also `ritardoPartenza` and `ritardoArrivo`.
- Platforms: `binario{Programmato,Effettivo}{Partenza,Arrivo}{Codice,Descrizione,Tipo}`.
- Status codes:
  - `actualFermataType`: **0** not yet reached, **1** reached/passed (sometimes without any real time), **3** cancelled stop (per site code; never observed).
  - `nextTrattaType` (segment after this stop): **0** already travelled, **1** the segment the train is currently on, **2** not yet travelled. Observed on 424 stops.
- Unused or always constant: `orientamento` (`"A"`/`"B"`/`null`), `listaCorrispondenze` (always `[]`), `kcNumTreno` (always `null`), `isNextChanged`/`nextChanged` (always `false`), `visualizzaPrevista` (always `true`), `materiale_label`.

### 5.5 How the site derives what it shows (`manageDettaglioTreno`)

**Stops and endpoint names**
- `primaFermata = fermate[0]`.
- `ultimaFermata` = the **last stop whose `actualFermataType !== 3`**, so a cancelled terminus is skipped. If every stop is cancelled it falls back to the last stop.
- Origin name: `origineEstera ?? primaFermata.stazione`. Destination name: `destinazioneEstera ?? ultimaFermata.stazione`.
  The top-level `origine`/`destinazione` fields are not used here.

**Scheduled departure and arrival**
- Departure: `compOrarioPartenza`. If it differs from the original `compOrarioPartenzaZero` (or `formatTime(oraPartenzaEstera)`), the original is shown in brackets.
- Arrival: `compOrarioArrivo`, with the original from `compOrarioArrivoZero` in brackets if they differ.

**Actual and predicted times**
- Actual departure: **`formatTime(fermate[0].effettiva)`**.
  - The pre-formatted `compOrarioPartenzaZeroEffettivo` can differ: for FR 9674 it was 20:31 while `effettiva` was 20:33, and the site shows 20:33.
- Actual or predicted arrival:
  - `ultimaFermata.arrivoReale ? formatTime(ultimaFermata.effettiva) : compOrarioArrivoZeroEffettivo`.
  - The label switches between "Arrivo Effettivo" and "Arrivo previsto" on `arrivato = !!ultimaFermata.arrivoReale || treno.arrivato`.
- **Staleness rules**, with `tsNow = now − 15 min`:
  - `oraUltimoRilevamento == null && tsNow > fermate[0].programmata` → actual departure and arrival both become `--:--`.
  - `oraUltimoRilevamento != null && tsNow > oraUltimoRilevamento && !arrivato` → arrival becomes `--:--`.
  - `attivaRitardoPR && treno.orarioArrivoEffettivo == null` → arrival becomes `--:--`. The field `orarioArrivoEffettivo` doesn't exist in the payload, so this always applies under PR.

**Foreign origin or destination**
- `oraPartenzaEstera != null` → departure = `formatTime(oraPartenzaEstera)` and actual departure `--:--`.
- `oraArrivoEstera != null` → arrival = `formatTime(oraArrivoEstera)` and predicted = that + `ritardo` minutes.

**Missed-detection alert**
- Condition: `ultimaFermata.arrivoReale === null`, there is a last detection, `arrivato === false`, and now (Rome) is more than 15 min after **today's date + the `HH:mm` of `oraUltimoRilevamento`**.
- Effect: the "missed acquisition" warning icon and popup appear, and the delay text is blanked.

**"Departed" flag**
- `tipoTreno == "ST"` → false.
- Otherwise true if `fermate[0].effettiva !== null` **or** `stazioneUltimoRilevamento !== "--"`.

**Cancelled train (`tipoTreno == "ST"`)**
- The delay text is forced to "non partito" with the `nonpartito.png` icon.
- The last-detection block is cleared.

**Header box and "Infomobilità" panel**
- Delay text: `compRitardoAndamento[lang]`, or the RA/PR variant (§1.4). Icon: `compImgRitardo`, or `compImgRitardoCorsaAttualizzata`.
- Last detection: `stazioneUltimoRilevamento` at `formatTime(oraUltimoRilevamento)`.
- Measures dropdown: shown if `cambiNumero.length > 0 || subTitle.length > 0`.
  - One option per `cambiNumero[]` item, using the label `"{{numero}} diventa {{{nuovoNumeroTreno}}} a {{{stazione}}}"`. So items have `nuovoNumeroTreno` and `stazione`, and the site injects `numero = compNumeroTreno`.
  - Plus `subTitle`, rendered as raw HTML.
- Header date: `moment(dataPartenzaTreno).format('DD/MM/YYYY')`.

**News calls made when the tab opens**
- `news/infomobility?trainNumber={numero}` is always requested.
- `news/smartcaring?commercialTrainNumber={numero}&originCode={codOrigine}&searchDate={yyyy-MM-dd of dataPartenza, Rome}` is requested **only if** `codiceCliente` is not 63 or 64 and `compNumeroTreno` (trimmed) doesn't start with FR, FB, FA or IC.
- Both calls also send a random `correlationId` header.

**Refresh button**: re-calls `andamentoTreno`; it doesn't poll automatically.

---

## 6. Route strip: `tratteCanvas`

`GET /viaggiatreno/tratteCanvas/{codOrigine}/{numeroTreno}/{dataPartenza}`. It has the same 204 semantics as `andamentoTreno`.

**Response**: an array with one element per stop in `fermate`, in the same order:

```text
{ id, stazione, first, last, stazioneCorrente,
  previousTrattaType, actualFermataType, nextTrattaType, trattaType,   // canvas codes, see below
  partenzaReale: bool, arrivoReale: bool,                              // booleans here (not millis)
  orientamento: [9 langs],
  fermata: { …the same stop object as andamentoTreno.fermate[i]… } }
```

**Canvas codes and colours** (`vt.canvasV2.js`)
- `previousTrattaType` / `nextTrattaType` colour the line before and after a stop. **These are not the same codes as `fermata.nextTrattaType`.**

  | code | colour | legend meaning |
  |---|---|---|
  | 0 | grey | not yet travelled |
  | 1 | blue | travelled |
  | 2 | yellow | changed route |
  | 3 | red | cancelled |
  | 4 | orange | changed route, already travelled |

  For example, the origin of a moving train has canvas `next=1` and fermata `next=1`, while a later stop has canvas `next=0` and fermata `next=2`.
- `actualFermataType` colours the station dot: 0 grey, 1 blue, 2 yellow, **3 red (cancelled stop, not clickable)**, 4 green, 5 pink.
- `first` / `last` suppress the line before or after the stop.
- The element with `stazioneCorrente: true` is selected and centred on load.

**Selecting a stop** (`manageInfoFermata`, using the element's `fermata` and the train-level `ritardo`, `oraUltimoRilevamento`, `attivaRitardoPR`):
- Scheduled time: `formatTime(fermata.programmata)`.
- Actual time: `formatTime(first ? fermata.partenzaReale : fermata.arrivoReale)`.
  If that is missing and `fermata.nextTrattaType !== 0`, it shows the predicted `programmata + ritardo·60 000`; otherwise `--:--`.
- For intermediate stops it also shows departure times:
  - Scheduled: `formatTime(partenza_teorica)`.
  - Actual: `partenzaReale`, **clamped to never be earlier than `partenza_teorica`**, so early departures display as on time.
    If there is no `partenzaReale`, it shows the predicted `partenza_teorica + ritardo` when `nextTrattaType != 0`, else `--:--`.
- "Arrived" and "departed" come from the element's **boolean** `arrivoReale` / `partenzaReale`.
- The same 15-minute staleness rules as §5.5 apply; under PR every time is `--:--`.
- Platform: the departure description if not null, else the arrival description. The effective platform wins over the scheduled one.
- Orientation: `orientamento[lang]` from the element.

**Connections panel**
- It requests `partenze/{stop id}/{time}`, choosing the time with `getOrario(fermata)`:
  - if `actualFermataType !== 0` → now;
  - else `effettiva`;
  - else `partenzaTeorica` (camelCase, which doesn't exist in the payload, so it is always skipped);
  - else `programmata`.
- It then calls `regione/{stop id}` to enable the city-map button.

---

## 7. Station tab helpers

### 7.1 `elencoStazioniCitta/{codStazione}`

- **Response**: `[{nomeLungo, nomeBreve, label, id}]`, the **other** stations in the same city (Napoli C. → Mergellina, Piazza Garibaldi, …).
- It includes the station itself only sometimes: it did for Bari C., but not for Roma Termini or Napoli C. Bergamo returns `[]`.
- **Site usage**:
  - If an entry's `id` equals the requested code, the site renders a `<select>` of all entries (`nomeBreve`) and uses that entry's `nomeLungo` as the tab title.
  - Otherwise there is no selector, and the title comes from `dettaglioStazione.localita.nomeBreve` or the searched label.

### 7.2 `regione/{codStazione}`

- **Response**: a **bare JSON number** (`5`, `20`, `22`). Unknown codes give **204**.
- The site uses it as `codRegione` for `dettaglioStazione`, falling back to `0` or the previous value.

### 7.3 `dettaglioStazione/{codStazione}/{codRegione}`

- **Response fields**:
  - Identity and position: `key`, `codReg`, `codiceStazione`, `codStazione`, `lat`, `lon`, `latMappaCitta`, `lonMappaCitta`, `esterno`, `nomeCitta`.
  - Map display: `tipoStazione` (1..4, the marker icon), `dettZoomStaz[]`, `offsetX`, `offsetY`, `pstaz[]`.
  - `mappaCitta: {urlImagePinpoint, urlImageBaloon}`.
  - `localita: {nomeLungo, nomeBreve, label, id}`.
- **Site usage**:
  - `localita.nomeBreve`: tab title fallback.
  - `lat`/`lon` not null: enables the "Mappa" button.
  - City map: centre is `latMappaCitta > 0 ? latMappaCitta : lat` (and likewise for lon); pin is `mappaCitta.urlImagePinpoint`; photo is `mappaCitta.urlImageBaloon`. The photo is prefixed with `/` and was always empty.
- **Region behaviour**:
  - `codRegione = 0` gives full data **only for stations on the national map** (Bergamo, Bari C., Firenze SMN).
    For other stations (Genova Brignole, Aosta) it returns the skeleton described below.
  - The correct region (from `/regione`) works.
  - An **unknown** code with region 0 (`S99999`) gives 200 with a placeholder:
    `localita.nomeLungo/nomeBreve/nomeCitta = "Y"`, `label: ""`, `codReg: 0`, `tipoStazione: 3`, and fake `lat`/`lon`
    `44.040219, 10.854492`. These coordinates are the same as the dummy node `S99999` in `elencoTratte/0`.
  - A **wrong** region returns a skeleton (`codReg: null`, `localita: null`, `mappaCitta: null`, `tipoStazione: null`) that still has `lat`/`lon`.
  - `getCoordinateStazione/{code}` returns that same skeleton; `coordinateCitta/{code}` returns `{latitudine, longitudine}`.

---

## 8. Journey solutions: `soluzioniViaggioNew` (currently dead)

- **Request built by the site**:
  - Path: `/soluzioniViaggioNew/{origNum}/{destNum}/{YYYY-MM-DDTHH:mm:ss}`.
  - `origNum`/`destNum` are the station codes with the `S` and leading zeros removed (`S06500` → `6500`).
  - The date is the chosen day at **00:00 local** (from the `DD/MM/YYYY` picker). The site always fetches the whole day and filters on the client.
  - Timeout is 10 s.
- **Observed**: **404 `Error`** for every attempt:
  - two UI runs;
  - 4 direct variants with different station pairs, times, and `S` prefixes.

  The site then renders "Servizio momentaneamente non disponibile".
- **Expected response, from the code only**:
  - Top level: `{ soluzioni: [...], origine, destinazione, errore }`. The service counts as available when `errore` is null or empty.
  - Each solution: `{ durata: "h:mm", vehicles: [...] }`.
  - Each vehicle: `{ numeroTreno, categoria, origine, destinazione, orarioPartenza, orarioArrivo, codOrigine }`. The times are instants that moment can parse.
- **Client logic**:
  - Departure is `vehicles[0].orarioPartenza` and arrival is `vehicles[last].orarioArrivo`; there are `vehicles.length − 1` changes.
  - Solutions are filtered into 7 time bands by departure: 00–06, 06–09, 09–12, 12–15, 15–18, 18–21, 21–24.
  - Vehicles with `categoria` `193` (bus), `195` (SFM), `999` (ferry) or `83` (urban) are not linkable.
  - Train detail is only offered when the chosen day is today. It resolves through `cercaNumeroTrenoTrenoAutocomplete`, taking the first line.

---

## 9. Map endpoints

### 9.1 `elencoStazioni/{codRegione}` (synchronous)

- The response is an array of the `dettaglioStazione` shape. Region 0 is about 380 kB.
- **Site usage**, for each station:
  - For each `dettZoomStaz[j]` with `pinpointVisibile`, it creates a marker at `lat`/`lon` with icon `pp_st_{tipoStazione}.png`.
  - The marker is visible for zoom levels `zoomStartRange..zoomStopRange`.
  - If `labelVisibile`, the marker gets the label `nomeCitta`, offset by `offsetX`/`offsetY`.
  - Clicking opens the station tab with `codiceStazione`.
- Both Italian and English booleans are present (`pinpointVisibile`/`pinpointVisible`, `labelVisibile`/`labelVisible`); the site reads the Italian ones.

### 9.2 `elencoTratte/{idRegione}/{zoom}/{categorie}/{catAV}/{timestamp}` (synchronous)

- **Path parameters**:
  - `categorie`: comma-joined category filters, e.g. `ES*,IC,EXP,EC,EN` (national) or `ES*,IC,EXP,EC,EN,REG,MET` (all).
  - `catAV`: comma-joined Frecce product codes (`100` FR, `101` FA, `102` FB), or the literal `null`.
  - `timestamp`: `Date.now()`, a cache buster.
  - Zoom is the map zoom level. In tests, zoom 6 and 8 returned identical data.
- **Response**: `[{nodoA, nodoB, trattaAB, trattaBA, parentAB, parentBA, latitudineA, longitudineA, latitudineB, longitudineB, occupata}]`.
- **Site usage**: draws the A–B line blue if `occupata` (a train is on it) and grey otherwise. A click passes `trattaAB`/`trattaBA` to `dettagliTratta`.
  Changing the filter changes `occupata` (FR+FA only → mostly `false`).

### 9.3 `dettagliTratta/{idRegione}/{trattaAB}/{trattaBA}/{categorie}/{catAV}`

- **Response**: `[{tratta: "Prato - S.Benedetto", treni: [...]}, {tratta: "S.Benedetto - Prato", treni: [...]}]`, one entry per direction.
- Each train entry has the board shape, with `tratta` and `regione` filled. **`orarioPartenza`/`orarioArrivo` are date strings here**, not millis.
- **Site usage** (`manageTratta`):
  - Train: `numeroTreno`, `codOrigine`, `dataPartenzaTreno`, `compNumeroTreno`.
  - Endpoints: `origineEstera ?? origine`, `destinazioneEstera ?? destinazione`.
  - Delay: `compRitardo[lang]`, `compImgRitardo`, and the RA/PR fields; `nonPartito`.
  - Header: `tratta`.
  - An empty direction renders "Nessun Treno in circolazione".

### 9.4 `datimeteo/{codRegione}` (synchronous)

- **Response**: an object keyed by station code:
  `{ "S12891": { codStazione, oggiTemperatura, oggiTempo, oggiTemperaturaMattino|Pomeriggio|Sera, oggiTempoMattino|Pomeriggio|Sera, domani… } }`.
  - Temperatures are °C integers.
  - `*Tempo` values are icon ids (`/vt_static/img/legenda/meteo/{id}.png`); the site knows 0–18 and 101–118.
- **Site usage**: in weather mode the marker icon is `oggiTempo`, and the popup shows the morning/afternoon/evening icon and temperature for today and tomorrow, titled `stazione.localita.label`.
- Weather mode is a UI toggle (`#bottone-meteo`). It didn't trigger in headless runs, so the endpoint was verified with direct calls (regions 0 and 20).

---

## 10. Home page, news and labels

### 10.1 Statistics, news and RSS

| Endpoint | Response | Site usage |
|---|---|---|
| `statistiche/{timestamp}` | `{treniGiorno, treniCircolanti, ultimoAggiornamento}` | fills the label templates "Dalla mezzanotte … hanno viaggiato {{treniGiorno}} treni" / "Attualmente sono in viaggio {{treniCircolanti}} treni", plus `datetime ultimoAggiornamento`. The path value is a cache buster; `0` works too. |
| `news/{codRegione}/{lingua}` | `[{id, titolo, data (millis), primoPiano: "true"/"false" (string), testo (HTML)}]` | the site converts `primoPiano != "false"` to a boolean (highlight) and renders `date data` with raw `{{{testo}}}`; `titolo` is not displayed. The box shows the first 3 items and the news page shows all. Only `codRegione 0` is used. The 2019 item returned looks stale. |
| `infomobilitaRSS/{false\|true}` | HTML `<ul id="accordionGenericInfomob">…` (`true` = planned works "Infolavori", about 74 kB) | injected verbatim. The HTML may call back into the app via `getSchedaTreno(num, codOrigine)`, `getStazione(cod)` and `getTrattaTreno(reg, ab, ba)`; none appeared on the test day. Accented letters arrive mojibaked (`VELOCITÃ€`). |
| `infomobilitaRSSBox/{bool}` | shorter HTML for the home box | wrapped in `<div id='boxInfoMob'>` and injected |
| `infomobilitaTicker` | `<ul><li>…</li></ul>` | wrapped in `<marquee>` |

### 10.2 `news/infomobility`

- **Response**: `[{title, link, pubDate, description, trainTags: ["510", …], regionTags: ["Campania", …], evidenzia: bool}]`.
- **Filtering is done on the server**:
  - No parameters → all items (27 on the test day).
  - `evidence=true` → only highlighted items.
  - `trainNumber=510` → items whose `trainTags` contain it.
  - `region=Campania` → items whose `regionTags` contain it. The match uses the region **name**, not the id.
- **Site usage**: with `trainNumber` only. The button appears if the list is non-empty; it renders `title` (with class `inEvidenza` if `evidenzia`) and `description` after unescaping HTML entities.

### 10.3 `news/smartcaring`

- **Request**: query params `commercialTrainNumber`, `originCode`, `searchDate=yyyy-MM-dd`. The client also defines `productionTrainNumber`, `destinationCode` and `idNoteInfopush`, but the site doesn't send them.
- **Site usage**: for each item it renders `formatDateTime(insertTimestamp)` as the heading and `infoNote` as the text.
- Every response during testing was `[]`.

### 10.4 `language/{lang}`

- A flat JSON map of 254 label keys to strings. The strings contain Handlebars placeholders and HTML, for example `andamentoTreno.diviene = "{{numero}} diventa {{{nuovoNumeroTreno}}} a {{{stazione}}}"`.
- The site loads it **synchronously** at startup for the `LANG` cookie (default `it`).

### 10.5 `POST /infomobilita/StampaTreno` (arrival certificate)

- **Request**: form-encoded `numTreno`, `locArrivo` (station code), `locArrivoDesc` (name), `date` (`DD-MM-YYYY`). The form's date picker allows the last 7 days.
- **Response**: `{comunicazione: string, pdf: bool}`.
  - If `pdf` is true, the site opens `certificato/certificato.jsp?comunicazione=…`. Otherwise it `alert`s the message.
  - Example: `"…il treno 3244 del giorno 25/11/2026 è arrivato alla stazione di TORINO PORTA NUOVA alle ore 20:37 con 1 minuti di anticipo."`.
  - A stop the train didn't reach that day gives `pdf:false`, "la stazione inserita non è valida".

---

## 11. Quirks and inconsistencies (summary)

1. **`orario` format**: the boards want a `Date.toString()`-like string. Millis and ISO give 400. The `GMT±hhmm` offset is honoured, and without it the time is Rome local.
2. **Boards are live, not a timetable**:
   - The window is about [t−15 min, t+105 min], but departed or finished trains disappear about 25 minutes after leaving.
   - Past slots are empty, and far-future slots are nearly empty or return junk.
3. **Run endpoints cover only today's runs**: other days give 204, and any millis within the day works. Train-number search also lists only today's runs.
4. **Train numbers collide**: `cercaNumeroTreno` silently picks one run; the autocomplete variant lists all of them.
5. **Two different "delay icon" fields**: boards display `compImgRitardo2` and train detail displays `compImgRitardo`. For cancelled trains only `compImgRitardo2` shows the cancellation, while `compImgRitardo` says "regolare".
6. **Pre-formatted vs raw times disagree**: `compOrarioPartenzaZeroEffettivo` (20:31) vs `fermate[0].effettiva` (20:33). The site shows the raw one.
7. **`compOrarioEffettivoArrivo`** is an image path concatenated with a time (`"/vt_static/img/legenda/icone_legenda/regolare.png23:21"`). The site doesn't use it.
8. **`haCambiNumero: true` on boards (with the numbering icon)** while `andamentoTreno.cambiNumero` was `[]` for all 5 such trains checked.
9. **Foreign-endpoint fields**: `*Estera` times are time-of-day values, not instants. For EC 46 Venezia→Domodossola, both `origineEstera` and `destinazioneEstera` were `"GENEVE"` with the same time (00:07), and the site would display them as origin and departure time.
10. **Field name mismatches in the site code**: `getOrario` reads `partenzaTeorica` (the payload has `partenza_teorica`) and `manageDettaglioTreno` reads `orarioArrivoEffettivo` (absent). Both are always undefined.
11. **Type inconsistencies**:
    - `millisDataPartenza` and `numeroTreno` are strings in some payloads and numbers in others.
    - `primoPiano` is a string boolean.
    - `dettagliTratta` uses date strings for times.
    - `regione` returns a bare number.
    - The canvas `partenzaReale`/`arrivoReale` are booleans, while the same names inside `fermata` are millis.
12. **Two different `nextTrattaType` code tables**: the canvas element's codes (0 grey / 1 travelled / 2 changed / 3 cancelled / 4 changed+travelled) and the stop's codes (0 travelled / 1 current / 2 ahead).
13. **Early departures are hidden** in the stop panel, because actual departure is clamped to scheduled.
14. **Strings carry HTML entities** and some languages are `null`. The text endpoints return mojibake for accented letters.
15. **`soluzioniViaggioNew` is down** (404 `Error` for all inputs).
16. **NTS autocomplete** returns 500 on an apostrophe; the other autocompletes return empty.
17. **Akamai** blocks the `HeadlessChrome` user agent on the page.

**Open (not observed)**: partial cancellations and diversions. I never saw `actualFermataType == 3`, a non-empty
`fermateSoppresse`, `provvedimento` above 1, or `tipoTreno` other than PG/ST. If one turns up, record:
- whether `fermate` keeps the cancelled stops;
- the values of `provvedimento`, `tipoTreno` and `subTitle`.

---

## Appendix: inputs used in testing (each used once or for one flow)

- **Station boards**:
  - Bergamo S01529 (UI)
  - Napoli Centrale S09218 (UI)
  - Torino Porta Nuova S00219 (format test)
  - Milano Centrale S01700 (tomorrow 08:00)
  - Roma Termini S08409 (arrivals tomorrow 07:00)
  - Venezia S. Lucia S02593 (earlier today)
  - Bologna C. S05043 (23:30)
  - Bari C. S11119 (in 5 days)
  - Palermo C. S12002 (3 days ago)
  - Verona P.N. S02430 (in a month)
  - Genova Brignole S04702 (03:00)
  - Firenze SMN S06421 (backwards window)
  - Domodossola S01003, Ventimiglia, Brennero, Trieste C., Tarvisio (international scan)
  - Venezia Mestre S02589 (sampling)
- **Trains**:
  - FR 9674 Napoli→Firenze (UI)
  - REG 2241, ambiguous between Cadorna and Centrale (UI search)
  - FR 8529 Bolzano→… (date and origin edge cases)
  - EC 46 Venezia→Domodossola/Genève; EC 41 (tomorrow)
  - REG 3244 Cuneo→Torino (Smart Caring, certificate)
  - REG 2040 and REG 23565 (cancelled, `ST`)
  - REG 2978/2383/2980/2982/2984 (renumbering flag)
  - 35 randomly sampled runs for value distributions
- **Search terms**: `berga`, `NAPOLI C`, `DOMODOSS`, `BOLZANO`, `LECCE`, `MILANO`, `pisa c`, `ancona`, `reggio`, `S. `, `forl`, `AOSTA`, `z`, `basel`, `sant'`.
- **Map**: Toscana (13) with all categories, segment 860/910, FR+FA filter; Sardegna (20); weather for regions 0 and 20; city map of Firenze SMN.
