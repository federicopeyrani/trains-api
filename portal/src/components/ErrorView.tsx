import { Button } from "react-aria-components"
import { ApiError } from "../api/client.ts"

const hints: Record<number, string> = {
  400: "The request was rejected as malformed.",
  404: "The station or run does not exist (runs are only known for today's trains).",
  503: "The provider (ViaggiaTreno) cannot serve the request right now: retry later.",
}

/** Shows a failed request: the RFC 9457 problem when the API sent one. */
export function ErrorView({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const apiError = error instanceof ApiError ? error : undefined
  const problem = apiError?.problem
  const status = apiError?.status

  return (
    <div className="error" role="alert">
      <strong>
        {status === undefined ? "Request failed" : `HTTP ${status}`}
        {problem?.title ? ` · ${problem.title}` : ""}
      </strong>
      <p>{problem?.detail ?? (error instanceof Error ? error.message : String(error))}</p>
      {status !== undefined && hints[status] && <p className="muted">{hints[status]}</p>}
      {status === undefined && (
        <p className="muted">Is the backend running, and does it allow this origin (CORS)? Check the base URL.</p>
      )}
      {problem?.instance && <p className="muted mono">{problem.instance}</p>}
      {onRetry && <Button onPress={onRetry}>Retry</Button>}
    </div>
  )
}
