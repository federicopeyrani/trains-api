import { useState } from "react"
import { Button, Form, Input, Label, Link, TextField } from "react-aria-components"
import { StationList } from "../components/StationList.tsx"
import { formatDateTime, formatRelative } from "../format/time.ts"
import { routeHref } from "../routing/routes.ts"
import { navigate } from "../routing/useRoute.ts"
import { removeRecentRun, removeRecentSearch } from "../storage/recent.ts"
import { useRecentRuns, useRecentSearches } from "../storage/useRecent.ts"

export function HomeScreen() {
  const [searches, updateSearches] = useRecentSearches()
  const [runs, updateRuns] = useRecentRuns()
  const [runId, setRunId] = useState("")

  return (
    <>
      <div className="row">
        <Link href={routeHref({ name: "search" })} className="react-aria-Link button primary big">
          Search trips
        </Link>
        <span className="spacer" />
        <Form
          className="row"
          onSubmit={(event) => {
            event.preventDefault()
            if (runId.trim()) navigate({ name: "run", runId: runId.trim() })
          }}
        >
          <TextField value={runId} onChange={setRunId} aria-label="Run id">
            <Label className="sr-only">Run id</Label>
            <Input placeholder="Open a run id: S01700_9545_1769122800000" className="react-aria-Input mono wide" />
          </TextField>
          <Button type="submit" isDisabled={!runId.trim()}>
            Open run
          </Button>
        </Form>
      </div>

      <section>
        <div className="row">
          <h2>Recent searches</h2>
          <span className="spacer" />
          {searches.length > 0 && <Button onPress={() => updateSearches(() => [])}>Clear</Button>}
        </div>
        {searches.length === 0 ? (
          <p className="muted">No searches yet.</p>
        ) : (
          <ul className="recent">
            {searches.map((search) => (
              <li key={routeHref({ name: "search", query: search })}>
                <Link href={routeHref({ name: "search", query: search })}>
                  <StationList stations={search.from} /> → <StationList stations={search.to} />
                </Link>
                <span className="muted small">
                  {search.date ? `departing ${formatDateTime(search.date)}` : "departing now"} · searched{" "}
                  {formatRelative(search.searchedAt)}
                </span>
                <Button
                  className="react-aria-Button quiet"
                  aria-label="Remove search"
                  onPress={() => updateSearches((list) => removeRecentSearch(list, search))}
                >
                  ×
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <div className="row">
          <h2>Recent runs</h2>
          <span className="spacer" />
          {runs.length > 0 && <Button onPress={() => updateRuns(() => [])}>Clear</Button>}
        </div>
        {runs.length === 0 ? (
          <p className="muted">No runs opened yet.</p>
        ) : (
          <ul className="recent">
            {runs.map((run) => (
              <li key={run.id}>
                <Link href={routeHref({ name: "run", runId: run.id })}>
                  <strong>{run.train}</strong> {run.origin} → {run.destination}
                </Link>
                <span className="muted small">
                  <span className="mono">{run.id}</span> · opened {formatRelative(run.openedAt)}
                </span>
                <Button
                  className="react-aria-Button quiet"
                  aria-label={`Remove run ${run.id}`}
                  onPress={() => updateRuns((list) => removeRecentRun(list, run.id))}
                >
                  ×
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}
