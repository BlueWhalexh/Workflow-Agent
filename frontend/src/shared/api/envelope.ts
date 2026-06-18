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
  const requestInit = await withSessionCsrf(fetcher, init);
  const response = await fetcher(url, {
    ...requestInit,
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...requestInit.headers,
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

async function withSessionCsrf(fetcher: ApiFetch, init: RequestInit): Promise<RequestInit> {
  if (!requiresSessionCsrf(init)) {
    return init;
  }

  const csrfResponse = await fetcher("/v1/session/csrf", {
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
  });
  const csrf = unwrapApiEnvelope<{ token: string; headerName: string }>(
    (await csrfResponse.json()) as ApiEnvelope<{ token: string; headerName: string }>,
  );

  return {
    ...init,
    headers: {
      ...toHeaderRecord(init.headers),
      [csrf.headerName]: csrf.token,
    },
  };
}

function requiresSessionCsrf(init: RequestInit): boolean {
  const method = (init.method ?? "GET").toUpperCase();
  return !["GET", "HEAD", "OPTIONS"].includes(method) && !hasBearerAuthorization(init.headers);
}

function hasBearerAuthorization(headers: HeadersInit | undefined): boolean {
  const authorization = toHeaderRecord(headers).Authorization ?? toHeaderRecord(headers).authorization;
  return authorization?.startsWith("Bearer ") === true;
}

function toHeaderRecord(headers: HeadersInit | undefined): Record<string, string> {
  if (!headers) {
    return {};
  }
  if (headers instanceof Headers) {
    return Object.fromEntries(headers.entries());
  }
  if (Array.isArray(headers)) {
    return Object.fromEntries(headers);
  }
  return headers;
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
