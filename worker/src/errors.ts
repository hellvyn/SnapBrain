export type ApiErrorCode =
  | "invalid-argument"
  | "resource-exhausted"
  | "unavailable"
  | "unauthenticated"
  | "failed-precondition";

/** An error the client is allowed to see; index.ts turns it into { error: CODE } with the matching HTTP status. */
export class ApiError extends Error {
  constructor(readonly code: ApiErrorCode, message: string) {
    super(message);
  }
}

const STATUS: Record<ApiErrorCode, number> = {
  "invalid-argument": 400,
  "unauthenticated": 401,
  "failed-precondition": 403,
  "resource-exhausted": 429,
  "unavailable": 503,
};

export const httpStatusOf = (code: ApiErrorCode): number => STATUS[code];

/** Wire names match FirebaseFunctionsException.Code so the Android core needs no change. */
export const wireCodeOf = (code: ApiErrorCode): string => code.toUpperCase().replace("-", "_");
