import { getLocalTimeZone, now, parseAbsoluteToLocal, toZoned, type DateValue } from "@internationalized/date"
import { useState } from "react"
import {
  Button,
  Calendar,
  CalendarCell,
  CalendarGrid,
  DateInput,
  DatePicker,
  DateSegment,
  Dialog,
  Form,
  Group,
  Heading,
  Label,
  Popover,
  Text,
} from "react-aria-components"
import { useApi } from "../api/ApiContext.tsx"
import { unwrap } from "../api/client.ts"
import { useRequest } from "../hooks/useRequest.ts"
import { formatSearchQuery } from "../routing/routes.ts"
import { navigate } from "../routing/useRoute.ts"
import { addRecentSearch, type SearchQuery, type StationRef } from "../storage/recent.ts"
import { useRecentSearches } from "../storage/useRecent.ts"
import { StationPicker } from "../components/StationPicker.tsx"
import { TripResults } from "./TripResults.tsx"

/** `2026-11-25T13:15:00Z`: RFC 3339 in UTC, without milliseconds. */
function toApiDate(value: DateValue): string {
  // The picker can hand back a date without zone (CalendarDateTime): read it as the browser's local time.
  const zoned = "timeZone" in value ? value : toZoned(value, getLocalTimeZone())
  // The field has minute precision: drop any seconds.
  return zoned
    .set({ second: 0, millisecond: 0 })
    .toAbsoluteString()
    .replace(/\.\d+Z$/, "Z")
}

export function SearchScreen({ query }: { query: SearchQuery | undefined }) {
  // Remount the form when the URL changes (e.g. back/forward), so it shows the search in the URL.
  const formKey = query ? formatSearchQuery(query) : ""
  return (
    <>
      <h1>Search trips</h1>
      <SearchForm key={formKey} initial={query} />
      {query && <SearchResults query={query} />}
    </>
  )
}

/** The current local time, to the minute. */
function currentMinute() {
  return now(getLocalTimeZone()).set({ second: 0, millisecond: 0 })
}

/**
 * The departure time in the form. It starts at the current time so the picker can be moved relative to it, but while
 * [isNow] (untouched, or reset with "Now") the search is sent without a date, which means "now" whenever it runs.
 */
interface DepartureTime {
  value: DateValue
  isNow: boolean
}

function SearchForm({ initial }: { initial: SearchQuery | undefined }) {
  const [from, setFrom] = useState<StationRef[]>(initial?.from ?? [])
  const [to, setTo] = useState<StationRef[]>(initial?.to ?? [])
  const [date, setDate] = useState<DepartureTime>(() =>
    initial?.date
      ? { value: parseAbsoluteToLocal(initial.date), isNow: false }
      : { value: currentMinute(), isNow: true },
  )
  const canSearch = from.length > 0 && to.length > 0

  return (
    <Form
      className="search-form"
      onSubmit={(event) => {
        event.preventDefault()
        if (!canSearch) return
        navigate({ name: "search", query: date.isNow ? { from, to } : { from, to, date: toApiDate(date.value) } })
      }}
    >
      <div className="stations">
        <StationPicker label="From" value={from} onChange={setFrom} />
        <Button
          className="react-aria-Button quiet swap"
          aria-label="Swap origins and destinations"
          onPress={() => {
            setFrom(to)
            setTo(from)
          }}
        >
          ⇄
        </Button>
        <StationPicker label="To" value={to} onChange={setTo} />
      </div>

      <div className="row">
        <DatePicker
          value={date.value}
          // Clearing the field goes back to now.
          onChange={(value) => setDate(value ? { value, isNow: false } : { value: currentMinute(), isNow: true })}
          granularity="minute"
          hourCycle={24}
          hideTimeZone
        >
          <Label>Departing from</Label>
          <Group>
            <DateInput>{(segment) => <DateSegment segment={segment} />}</DateInput>
            <Button aria-label="Open calendar">▾</Button>
          </Group>
          <Text slot="description">
            {date.isNow ? "Now: searches from the current time" : "Your local time, sent as UTC"}
          </Text>
          <Popover>
            <Dialog>
              <Calendar>
                <header>
                  <Button slot="previous">◀</Button>
                  <Heading />
                  <Button slot="next">▶</Button>
                </header>
                <CalendarGrid>{(day) => <CalendarCell date={day} />}</CalendarGrid>
              </Calendar>
            </Dialog>
          </Popover>
        </DatePicker>
        <Button
          className="react-aria-Button field-action"
          onPress={() => setDate({ value: currentMinute(), isNow: true })}
          isDisabled={date.isNow}
        >
          Now
        </Button>
        <span className="spacer" />
        <Button type="submit" className="react-aria-Button primary field-action" isDisabled={!canSearch}>
          Search
        </Button>
      </div>
    </Form>
  )
}

function SearchResults({ query }: { query: SearchQuery }) {
  const { client, baseUrl } = useApi()
  const [, updateRecent] = useRecentSearches()
  const key = `${baseUrl} ${formatSearchQuery(query)}`

  const { state, reload } = useRequest(key, (signal) => {
    // Every search sent (typed, linked, reopened or reloaded) goes first in the recent searches.
    updateRecent((list) => addRecentSearch(list, { ...query, searchedAt: new Date().toISOString() }))
    const params = { query: { from: query.from.map((s) => s.id), to: query.to.map((s) => s.id), date: query.date } }
    return unwrap(client.GET("/trips/all", { params, signal }))
  })

  return <TripResults state={state} reload={reload} />
}
