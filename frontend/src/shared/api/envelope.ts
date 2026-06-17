export const JAVA_BACKEND_API_SCHEMA = "java-backend-api.v1";

export type ApiErrorBody = {
  code: string;
  message: string;
  retryable: boolean;
};

export type ApiEnvelope<T> = {
  schemaVersion: string;
  ok: boolean;
  data?: T | null;
  error?: ApiErrorBody | null;
};

export class ApiClientError extends Error {
  readonly code: string;
  readonly retryable: boolean;

  constructor(error: ApiErrorBody) {
    super(error.message);
    this.name = "ApiClientError";
    this.code = error.code;
    this.retryable = error.retryable;
  }
}

export type ApiFetch = (url: string, init?: RequestInit) => Promise<Response>;

export async function requestApiJson<T>(fetcher: ApiFetch, url: string, init: RequestInit = {}): Promise<T> {
  const response = await fetcher(url, {
    ...init,
    headers: {
      Accept: "application/json",
      ...init.headers,
    },
  });

  if (!response.ok && !isJsonResponse(response)) {
    throw new ApiClientError({
      code: `HTTP_${response.status}`,
      message: `HTTP ${response.status} ${response.statusText}`.trim(),
      retryable: response.status >= 500,
    });
  }

  return unwrapApiEnvelope<T>((await response.json()) as ApiEnvelope<T>);
}

export function unwrapApiEnvelope<T>(envelope: ApiEnvelope<T>): T {
  if (envelope.schemaVersion !== JAVA_BACKEND_API_SCHEMA) {
    throw new ApiClientError({
      code: "UNSUPPORTED_SCHEMA",
      message: `Unsupported API schema: ${envelope.schemaVersion}`,
      retryable: false,
    });
  }

  if (!envelope.ok) {
    throw new ApiClientError(
      envelope.error ?? {
        code: "UNKNOWN_API_ERROR",
        message: "API returned an error envelope without error details",
        retryable: true,
      },
    );
  }

  if (envelope.data === undefined || envelope.data === null) {
    throw new ApiClientError({
      code: "EMPTY_RESPONSE",
      message: "API success envelope did not include data",
      retryable: true,
    });
  }

  return envelope.data;
}

function isJsonResponse(response: Response) {
  return response.headers.get("content-type")?.includes("application/json") ?? false;
}
