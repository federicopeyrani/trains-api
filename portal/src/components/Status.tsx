import type { RunStatus, StopStatus } from "../api/client.ts"

const labels: Record<StopStatus | RunStatus, string> = {
  NOT_YET_ARRIVED: "not yet arrived",
  ARRIVED: "arrived",
  DEPARTED: "departed",
  NOT_YET_DEPARTED: "not yet departed",
  CANCELLED: "cancelled",
  ON_TIME: "on time",
  DELAYED: "delayed",
}

export function StatusBadge({ status }: { status: StopStatus | RunStatus | undefined }) {
  if (!status) return <span className="badge">unknown</span>
  return (
    <span className="badge" data-status={status} title={status}>
      {labels[status]}
    </span>
  )
}
