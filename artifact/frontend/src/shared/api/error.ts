/** An error the backend answered with; the UI shows a translation of `errorCode`. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly errorCode: string,
    readonly params: Record<string, unknown>,
    message: string,
  ) {
    super(message)
  }
}
