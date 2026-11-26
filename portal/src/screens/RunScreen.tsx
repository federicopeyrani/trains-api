import { useEffect, useEffectEvent } from "react"
import { Button, Cell, Column, Row, Table, TableBody, TableHeader } from "react-aria-components"
import { useApi } from "../api/ApiContext.tsx"
import { unwrap, type Run, type Timing } from "../api/client.ts"
import { ErrorView } from "../components/ErrorView.tsx"
import { RawJson } from "../components/RawJson.tsx"
import { StatusBadge } from "../components/Status.tsx"
import { TimingView } from "../components/TimingView.tsx"
import { formatDateTime, formatRelative } from "../format/time.ts"
import { useRequest } from "../hooks/useRequest.ts"
import { addRecentRun } from "../storage/recent.ts"
import { useRecentRuns } from "../storage/useRecent.ts"

export function RunScreen({ runId }: { runId: string }) {
  const { client, baseUrl } = useApi()
  const [, updateRecent] = useRecentRuns()

  const { state, reload } = useRequest(`${baseUrl} ${runId}`, (signal) =>
    unwrap(client.GET("/runs/{runId}", { params: { path: { runId } }, signal })),
  )

  const run = state.status === "success" ? state.data : undefined
  const remember = useEffectEvent((opened: Run) =>
    updateRecent((list) =>
      addRecentRun(list, {
        id: opened.id,
        train: opened.train.id,
        origin: opened.origin.station.name,
        destination: opened.destination.station.name,
        openedAt: new Date().toISOString(),
      }),
    ),
  )
  useEffect(() => {
    if (run) remember(run)
  }, [run])

  return (
    <>
      <div className="row">
        <h1>
          Run <span className="mono">{runId}</span>
        </h1>
        <span className="spacer" />
        <Button onPress={reload} isDisabled={state.status === "loading"}>
          Reload
        </Button>
      </div>
      {state.status === "loading" && <p className="muted">Loading…</p>}
      {state.status === "error" && <ErrorView error={state.error} onRetry={reload} />}
      {run && state.status === "success" && <RunDetails run={run} receivedAt={state.receivedAt} />}
    </>
  )
}

interface StopRow {
  key: string
  index: number
  kind: "origin" | "stop" | "destination"
  name: string
  id: string
  status: Run["origin"]["status"]
  arrival?: Timing
  departure?: Timing
}

function stopRows(run: Run): StopRow[] {
  const rows: Omit<StopRow, "index">[] = [
    { key: "origin", kind: "origin", ...station(run.origin), departure: run.origin.departureTime },
    ...run.stops.map((stop, index) => ({
      key: `stop-${index}`,
      kind: "stop" as const,
      ...station(stop),
      arrival: stop.arrivalTime,
      departure: stop.departureTime,
    })),
    { key: "destination", kind: "destination", ...station(run.destination), arrival: run.destination.arrivalTime },
  ]
  return rows.map((row, index) => Object.assign(row, { index }))
}

function station(stop: Run["origin"]) {
  return { name: stop.station.name, id: stop.station.id, status: stop.status }
}

function RunDetails({ run, receivedAt }: { run: Run; receivedAt: string }) {
  return (
    <section>
      <dl className="facts">
        <dt>Train</dt>
        <dd>
          <strong>{run.train.id}</strong> · {run.train.origin.name} → {run.train.destination.name}
        </dd>
        <dt>Status</dt>
        <dd>
          <StatusBadge status={run.status} />
        </dd>
        <dt>Run</dt>
        <dd>
          {run.origin.station.name} → {run.destination.station.name} · {run.stops.length} intermediate stops
        </dd>
        <dt>Last update</dt>
        <dd title={run.lastUpdate}>
          {formatDateTime(run.lastUpdate)} ({formatRelative(run.lastUpdate)})
        </dd>
        <dt>Tracking</dt>
        <dd title={run.tracking.lastDetection}>
          <StatusBadge status={run.tracking.state} />{" "}
          {run.tracking.lastDetection
            ? `last detected ${formatDateTime(run.tracking.lastDetection)} (${formatRelative(run.tracking.lastDetection)})`
            : "never detected"}
        </dd>
        <dt>Received</dt>
        <dd>{formatDateTime(receivedAt)}</dd>
      </dl>

      <Table aria-label="Stops" className="react-aria-Table stops">
        <TableHeader>
          <Column>#</Column>
          <Column isRowHeader>Station</Column>
          <Column>Arrival</Column>
          <Column>Departure</Column>
          <Column>Status</Column>
        </TableHeader>
        <TableBody items={stopRows(run)}>
          {(row) => (
            <Row id={row.key} data-kind={row.kind} data-status={row.status}>
              <Cell>{row.index}</Cell>
              <Cell>
                {row.name} <span className="mono muted">{row.id}</span>
              </Cell>
              <Cell>
                {row.kind === "origin" ? <span className="muted">–</span> : <TimingView timing={row.arrival} />}
              </Cell>
              <Cell>
                {row.kind === "destination" ? <span className="muted">–</span> : <TimingView timing={row.departure} />}
              </Cell>
              <Cell>
                <StatusBadge status={row.status} />
              </Cell>
            </Row>
          )}
        </TableBody>
      </Table>

      <RawJson value={run} />
    </section>
  )
}
