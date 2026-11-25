import { useEffect, useState } from "react"
import {
  Button,
  ComboBox,
  Input,
  Label,
  ListBox,
  ListBoxItem,
  Popover,
  Tag,
  TagGroup,
  TagList,
  Text,
} from "react-aria-components"
import { useApi } from "../api/ApiContext.tsx"
import { unwrap, type StationDetails } from "../api/client.ts"
import { useRequest } from "../hooks/useRequest.ts"
import type { StationRef } from "../storage/recent.ts"

const DEBOUNCE_MS = 250
const MIN_QUERY = 2
const acceptAll = () => true

/**
 * Picks several stations: a combo box searching `/stations`, and the picked stations as removable tags. Accepts a raw
 * station id too (press Enter), to test ids the search does not return.
 */
export function StationPicker({
  label,
  value,
  onChange,
}: {
  label: string
  value: StationRef[]
  onChange: (stations: StationRef[]) => void
}) {
  const { client, baseUrl } = useApi()
  const [input, setInput] = useState("")
  const [query, setQuery] = useState("")

  useEffect(() => {
    const timeout = setTimeout(() => setQuery(input.trim()), DEBOUNCE_MS)
    return () => clearTimeout(timeout)
  }, [input])

  const { state } = useRequest(query.length >= MIN_QUERY ? `${baseUrl} ${query}` : null, (signal) =>
    unwrap(client.GET("/stations", { params: { query: { query } }, signal })),
  )

  const picked = new Set(value.map((s) => s.id))
  const results: StationDetails[] = state.status === "success" ? state.data.filter((s) => !picked.has(s.id)) : []

  const add = (station: StationRef) => {
    if (!picked.has(station.id)) onChange([...value, station])
    setInput("")
    setQuery("")
  }

  return (
    <div className="station-picker">
      <ComboBox
        items={results}
        inputValue={input}
        onInputChange={setInput}
        selectedKey={null}
        onSelectionChange={(key) => {
          const station = results.find((s) => s.id === key)
          if (station) add({ id: station.id, name: station.name })
        }}
        defaultFilter={acceptAll}
        menuTrigger="input"
        // Shows "Searching…" and "No stations found", but not once the input is cleared after a pick, so the menu
        // closes then instead of reopening empty.
        allowsEmptyCollection={input.trim().length > 0}
        allowsCustomValue
        onKeyDown={(event) => {
          // A bare station id (S01700), not in the results: add it as is.
          if (event.key === "Enter" && /^S\d+$/i.test(input.trim()) && results.length === 0) {
            add({ id: input.trim().toUpperCase(), name: input.trim().toUpperCase() })
          }
        }}
      >
        <Label>{label}</Label>
        <div className="combo">
          <Input placeholder="Type a station name, or an id like S01700" />
          <Button aria-label="Show results">▾</Button>
        </div>
        <Popover className="react-aria-Popover combo-popover">
          <ListBox<StationDetails>
            renderEmptyState={() => (
              <div className="empty">
                {query.length < MIN_QUERY
                  ? `Type at least ${MIN_QUERY} characters`
                  : state.status === "error"
                    ? `Search failed: ${state.error instanceof Error ? state.error.message : String(state.error)}`
                    : state.status === "success"
                      ? "No stations found"
                      : "Searching…"}
              </div>
            )}
          >
            {(station) => (
              <ListBoxItem id={station.id} textValue={station.name}>
                <Text slot="label">{station.name}</Text>
                <Text slot="description" className="mono muted">
                  {station.id}
                  {station.location &&
                    ` · ${station.location.latitude.toFixed(4)}, ${station.location.longitude.toFixed(4)}`}
                </Text>
              </ListBoxItem>
            )}
          </ListBox>
        </Popover>
      </ComboBox>

      <TagGroup
        aria-label={`${label}: picked stations`}
        onRemove={(keys) => onChange(value.filter((s) => !keys.has(s.id)))}
      >
        <TagList items={value} renderEmptyState={() => <span className="muted">No stations picked</span>}>
          {(station) => (
            <Tag id={station.id} textValue={station.name}>
              {station.name} <span className="mono muted">{station.id}</span>
              <Button slot="remove" aria-label={`Remove ${station.name}`}>
                ×
              </Button>
            </Tag>
          )}
        </TagList>
      </TagGroup>
    </div>
  )
}
