import createClient from "openapi-fetch"
import type { components, paths } from "./schema.gen.ts"

type Schemas = components["schemas"]
export type StationDetails = Schemas["StationDetails"]
export type Trip = Schemas["Trip"]
export type TripSearch = Schemas["TripSearch"]
export type Run = Schemas["Run"]
export type Timing = Schemas["Timing"]
export type StopStatus = Schemas["StopStatus"]
export type RunStatus = Schemas["Status"]
export type Problem = Schemas["Problem"]

export type ApiClient = ReturnType<typeof createApiClient>

/** A typed client for the API at [baseUrl], generated from `src/main/resources/openapi.yaml`. */
export function createApiClient(baseUrl: string) {
  return createClient<paths, "application/json">({
    baseUrl: baseUrl.replace(/\/+$/, ""),
    // The trip endpoints also answer `text/event-stream`: ask for the complete JSON response.
    headers: { Accept: "application/json" },
  })
}

/** A failed API call: an error response (with its RFC 9457 problem, if any) or no response at all. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status?: number,
    readonly problem?: Problem,
  ) {
    super(message)
    this.name = "ApiError"
  }
}

type Result<T> = { data: T; error?: never; response: Response } | { data?: never; error: unknown; response: Response }

/**
 * Returns the body of a successful openapi-fetch call, or throws an [ApiError] (also for a success without a body).
 * Aborts are rethrown as they are.
 */
export async function unwrap<T>(request: Promise<Result<T>>): Promise<NonNullable<T>> {
  let result: Result<T>
  try {
    result = await request
  } catch (error) {
    if (isAbort(error)) throw error
    throw new ApiError(`Network error: ${error instanceof Error ? error.message : String(error)}`)
  }
  if (result.error === undefined && result.response.ok) {
    if (result.data === undefined || result.data === null) {
      throw new ApiError(`HTTP ${result.response.status} without a body`, result.response.status)
    }
    return result.data
  }

  const problem = isProblem(result.error) ? result.error : undefined
  const status = result.response.status
  throw new ApiError(problem?.detail ?? problem?.title ?? `HTTP ${status}`, status, problem)
}

export function isAbort(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError"
}

function isProblem(value: unknown): value is Problem {
  return typeof value === "object" && value !== null && ("title" in value || "detail" in value || "status" in value)
}
