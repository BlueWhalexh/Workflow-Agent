export type RealSmokeProvider = "deepseek-real-smoke" | "claude-code-real-smoke";

export type RealSmokeStatus = "SKIPPED" | "BLOCKED" | "PASSED" | "FAILED";

export interface RealProviderSmokeResult {
  provider: RealSmokeProvider;
  status: RealSmokeStatus;
  realExternalCall: boolean;
  reason: string;
  requiredEnv: string[];
  httpStatus?: number;
}

const DEEPSEEK_REQUIRED_ENV = ["DEEPSEEK_API_KEY", "DEEPSEEK_BASE_URL", "DEEPSEEK_MODEL"];

function missingEnv(requiredEnv: string[], env: Record<string, string | undefined>): string[] {
  return requiredEnv.filter((name) => !env[name]);
}

export function inspectRealProviderSmoke(input: {
  provider: RealSmokeProvider;
  env: Record<string, string | undefined>;
}): RealProviderSmokeResult {
  if (input.provider === "claude-code-real-smoke") {
    return {
      provider: input.provider,
      status: "BLOCKED",
      realExternalCall: false,
      reason: "CLAUDE_CODE_SDK_NOT_WIRED",
      requiredEnv: []
    };
  }

  const missing = missingEnv(DEEPSEEK_REQUIRED_ENV, input.env);
  if (missing.length > 0) {
    return {
      provider: input.provider,
      status: "SKIPPED",
      realExternalCall: false,
      reason: "MISSING_ENV",
      requiredEnv: DEEPSEEK_REQUIRED_ENV
    };
  }

  return {
    provider: input.provider,
    status: "SKIPPED",
    realExternalCall: false,
    reason: "EXECUTE_REAL_NOT_SET",
    requiredEnv: DEEPSEEK_REQUIRED_ENV
  };
}

export async function runRealProviderSmoke(input: {
  provider: RealSmokeProvider;
  env: Record<string, string | undefined>;
  executeReal: boolean;
}): Promise<RealProviderSmokeResult> {
  const inspected = inspectRealProviderSmoke(input);
  if (inspected.status !== "SKIPPED" || inspected.reason === "MISSING_ENV" || !input.executeReal) {
    return inspected;
  }

  const response = await fetch(input.env.DEEPSEEK_BASE_URL!, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${input.env.DEEPSEEK_API_KEY!}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      model: input.env.DEEPSEEK_MODEL!,
      messages: [{ role: "user", content: "Reply with the word ok." }],
      max_tokens: 4
    })
  });

  return {
    provider: input.provider,
    status: response.ok ? "PASSED" : "FAILED",
    realExternalCall: true,
    reason: response.ok ? "REAL_EXTERNAL_CALL_PASSED" : "REAL_EXTERNAL_CALL_FAILED",
    requiredEnv: DEEPSEEK_REQUIRED_ENV,
    httpStatus: response.status
  };
}
