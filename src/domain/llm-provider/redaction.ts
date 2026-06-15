const SECRET_KEY_PATTERNS = ["authorization", "api_key", "apikey", "cookie", "set-cookie", "password"];
const SECRET_KEY_EXACT = new Set(["token", "access_token", "refresh_token", "id_token"]);

function shouldRedactKey(key: string): boolean {
  const normalized = key.toLowerCase();
  return SECRET_KEY_EXACT.has(normalized) || SECRET_KEY_PATTERNS.some((pattern) => normalized.includes(pattern));
}

export function redactProviderEnvelope(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => redactProviderEnvelope(item));
  }

  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([key, child]) => [
        key,
        shouldRedactKey(key) ? "[REDACTED]" : redactProviderEnvelope(child)
      ])
    );
  }

  return value;
}
