import type { StationRef } from "../storage/recent.ts"

export function StationList({ stations }: { stations: StationRef[] }) {
  return <span>{stations.map((s) => s.name).join(", ")}</span>
}
