import type { RunStatus, StopStatus, TrackingState } from "../api/client.ts"

type Status = StopStatus | RunStatus | TrackingState

const labels: Record<Status, string> = {
  NOT_YET_ARRIVED: "not yet arrived",
  ARRIVED: "arrived",
  DEPARTED: "departed",
  NOT_YET_DEPARTED: "not yet departed",
  CANCELLED: "cancelled",
  ON_TIME: "on time",
  DELAYED: "delayed",
  LIVE: "live",
  STALE: "stale data",
  PENDING: "awaiting data",
  MISSING: "no data",
  NOT_TRACKED: "not tracked",
}

export function StatusBadge({ status }: { status: Status | undefined }) {
  if (!status) return <span className="badge">unknown</span>
  return (
    <span className="badge" data-status={status} title={status}>
      {labels[status]}
    </span>
  )
}
