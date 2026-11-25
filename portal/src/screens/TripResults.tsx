import { Button, Cell, Column, Row, Table, TableBody, TableHeader } from "react-aria-components"
import type { Trip, TripSearch } from "../api/client.ts"
import { ErrorView } from "../components/ErrorView.tsx"
import { RawJson } from "../components/RawJson.tsx"
import { StatusBadge } from "../components/Status.tsx"
import { TimingView } from "../components/TimingView.tsx"
import { formatDateTime, formatRelative } from "../format/time.ts"
import type { RequestState } from "../hooks/useRequest.ts"
import { routeHref } from "../routing/routes.ts"

export function TripResults({ state, reload }: { state: RequestState<TripSearch>; reload: () => void }) {
  if (state.status === "idle") return null
  if (state.status === "loading") return <p className="muted">Searching… (the API checks every departure's run)</p>
  if (state.status === "error") return <ErrorView error={state.error} onRetry={reload} />

  const { trips, unverifiedDepartures } = state.data

  return (
    <section className="results">
      <div className="row">
        <h2>
          {trips.length} {trips.length === 1 ? "trip" : "trips"}
        </h2>
        <span className="muted" title={state.receivedAt}>
          received {formatDateTime(state.receivedAt)}
        </span>
        <span className="spacer" />
        <Button onPress={reload}>Reload</Button>
      </div>

      {unverifiedDepartures > 0 && (
        <output className="warning">
          {unverifiedDepartures} {unverifiedDepartures === 1 ? "departure" : "departures"} could not be checked (run
          unavailable): the result may be incomplete.
        </output>
      )}

      <Table aria-label="Trips" className="react-aria-Table trips">
        <TableHeader>
          <Column isRowHeader>Train</Column>
          <Column>From</Column>
          <Column>Departure</Column>
          <Column>To</Column>
          <Column>Arrival</Column>
          <Column>Run</Column>
          <Column>Updated</Column>
        </TableHeader>
        <TableBody
          items={trips.map((trip, index) => ({ key: tripKey(trip, index), trip }))}
          renderEmptyState={() => "No trips in this window."}
        >
          {({ key, trip }) => (
            <Row id={key} href={trip.run ? routeHref({ name: "run", runId: trip.run.id }) : undefined}>
              <Cell>
                {trip.run ? (
                  <>
                    <strong>{trip.run.train.id}</strong>
                    <div className="muted small">→ {trip.run.train.destination.name}</div>
                  </>
                ) : (
                  <span className="muted">no run</span>
                )}
              </Cell>
              <Cell>
                {trip.from.station.name} <StatusBadge status={trip.from.status} />
              </Cell>
              <Cell>
                <TimingView timing={trip.from.departureTime} />
              </Cell>
              <Cell>
                {trip.to.station.name} <StatusBadge status={trip.to.status} />
              </Cell>
              <Cell>
                <TimingView timing={trip.to.arrivalTime} />
              </Cell>
              <Cell>
                <StatusBadge status={trip.run?.status} />
              </Cell>
              <Cell>
                <span
                  title={`lastUpdate: ${trip.lastUpdate}\nlastUpdateFromProvider: ${trip.lastUpdateFromProvider ?? "–"}`}
                >
                  {formatRelative(trip.lastUpdateFromProvider ?? trip.lastUpdate)}
                </span>
              </Cell>
            </Row>
          )}
        </TableBody>
      </Table>

      <RawJson value={state.data} />
    </section>
  )
}

function tripKey(trip: Trip, index: number): string {
  return `${trip.run?.id ?? `none-${index}`} ${trip.from.station.id} ${trip.to.station.id}`
}
