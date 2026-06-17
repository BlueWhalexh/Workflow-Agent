const SENSITIVE_KEYS = new Set([
  "apikeysecretref",
  "authorization",
  "cookie",
  "environmentvariablename",
  "envname",
  "providerpayload",
  "rawproviderpayload",
  "secret",
  "secretref",
  "serverstorageref",
  "source",
  "token",
]);

const ABSOLUTE_SERVER_PATH_PATTERN = /\/(?:Users|private)\/[^\s,)"']+/g;

export function sanitizeForPublicUi(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => sanitizeForPublicUi(item));
  }

  if (typeof value === "string") {
    return value.replace(ABSOLUTE_SERVER_PATH_PATTERN, "[redacted-path]");
  }

  if (value === null || typeof value !== "object") {
    return value;
  }

  return Object.fromEntries(
    Object.entries(value)
      .filter(([key]) => !isSensitiveKey(key))
      .map(([key, nested]) => [key, sanitizeForPublicUi(nested)]),
  );
}

function isSensitiveKey(key: string) {
  return SENSITIVE_KEYS.has(key.toLowerCase());
}
