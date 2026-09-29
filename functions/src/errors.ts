export type ApiErrorCode = "invalid-argument" | "resource-exhausted" | "unavailable";

/** An error the client is allowed to see; index.ts maps it to HttpsError with the same code. */
export class ApiError extends Error {
  constructor(readonly code: ApiErrorCode, message: string) {
    super(message);
  }
}
