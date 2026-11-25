import type { Timing } from "../api/client.ts"
import { bestTime, delayMinutes, formatDelay, formatTime } from "../format/time.ts"

/** The best known time of an event, with the schedule and delay when they differ. Hover for every raw value. */
export function TimingView({ timing }: { timing: Timing | undefined }) {
  if (!timing) return <span className="muted">–</span>

  const best = bestTime(timing)
  const delay = delayMinutes(timing)
  const kind = timing.actual ? "actual" : timing.expected ? "expected" : "scheduled"
  const title = [
    `scheduled: ${timing.scheduled}`,
    `expected: ${timing.expected ?? "–"}`,
    `actual: ${timing.actual ?? "–"}`,
  ].join("\n")

  return (
    <span className="timing" title={title} data-kind={kind}>
      <time dateTime={best}>{formatTime(best)}</time>
      {best !== timing.scheduled && <s className="muted">{formatTime(timing.scheduled)}</s>}
      {delay !== undefined && (
        <span className="delay" data-late={delay > 0 || undefined} data-early={delay < 0 || undefined}>
          {formatDelay(delay)}
        </span>
      )}
      <span className="kind">{kind}</span>
    </span>
  )
}
