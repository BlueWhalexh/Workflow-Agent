import { execFile } from "node:child_process";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

export const MIMO_KEYCHAIN_SERVICE = "my-workflow-agent.mimo";
export const DEFAULT_MIMO_KEYCHAIN_BASE_URL = "https://token-plan-cn.xiaomimimo.com/v1";
export const DEFAULT_MIMO_KEYCHAIN_MODEL = "mimo-v2.5";

type ExecFileAsync = (
  file: string,
  args: string[]
) => Promise<{
  stdout: string;
  stderr: string;
}>;

export interface MimoKeychainEnv {
  MIMO_API_KEY?: string;
  MIMO_BASE_URL?: string;
  MIMO_MODEL?: string;
}

async function readKeychainValue(account: keyof MimoKeychainEnv, execImpl: ExecFileAsync): Promise<string | undefined> {
  try {
    const { stdout } = await execImpl("/usr/bin/security", [
      "find-generic-password",
      "-s",
      MIMO_KEYCHAIN_SERVICE,
      "-a",
      account,
      "-w"
    ]);
    const value = stdout.trim();
    return value.length > 0 ? value : undefined;
  } catch {
    return undefined;
  }
}

async function writeKeychainValue(account: keyof MimoKeychainEnv, value: string, execImpl: ExecFileAsync): Promise<void> {
  if (value.trim().length === 0) {
    throw new Error(`${account} must be non-empty.`);
  }
  await execImpl("/usr/bin/security", [
    "add-generic-password",
    "-U",
    "-s",
    MIMO_KEYCHAIN_SERVICE,
    "-a",
    account,
    "-w",
    value
  ]);
}

export async function readMimoKeychainEnv(input: { execFile?: ExecFileAsync } = {}): Promise<MimoKeychainEnv> {
  const execImpl = input.execFile ?? execFileAsync;
  const [apiKey, baseUrl, model] = await Promise.all([
    readKeychainValue("MIMO_API_KEY", execImpl),
    readKeychainValue("MIMO_BASE_URL", execImpl),
    readKeychainValue("MIMO_MODEL", execImpl)
  ]);
  return {
    MIMO_API_KEY: apiKey,
    MIMO_BASE_URL: baseUrl,
    MIMO_MODEL: model
  };
}

export function mergeMimoKeychainEnv(
  env: Record<string, string | undefined>,
  keychainEnv: MimoKeychainEnv
): Record<string, string | undefined> {
  return {
    ...env,
    MIMO_API_KEY: env.MIMO_API_KEY || keychainEnv.MIMO_API_KEY,
    MIMO_BASE_URL: env.MIMO_BASE_URL || keychainEnv.MIMO_BASE_URL,
    MIMO_MODEL: env.MIMO_MODEL || keychainEnv.MIMO_MODEL
  };
}

export async function hydrateMimoEnvFromKeychain(
  env: Record<string, string | undefined>,
  input: { execFile?: ExecFileAsync } = {}
): Promise<Record<string, string | undefined>> {
  return mergeMimoKeychainEnv(env, await readMimoKeychainEnv(input));
}

export async function configureMimoKeychainEnv(input: {
  apiKey: string;
  baseUrl?: string;
  model?: string;
  execFile?: ExecFileAsync;
}): Promise<{ service: string; accounts: Array<keyof MimoKeychainEnv> }> {
  const execImpl = input.execFile ?? execFileAsync;
  const values: Required<MimoKeychainEnv> = {
    MIMO_API_KEY: input.apiKey,
    MIMO_BASE_URL: input.baseUrl ?? DEFAULT_MIMO_KEYCHAIN_BASE_URL,
    MIMO_MODEL: input.model ?? DEFAULT_MIMO_KEYCHAIN_MODEL
  };

  await writeKeychainValue("MIMO_API_KEY", values.MIMO_API_KEY, execImpl);
  await writeKeychainValue("MIMO_BASE_URL", values.MIMO_BASE_URL, execImpl);
  await writeKeychainValue("MIMO_MODEL", values.MIMO_MODEL, execImpl);

  return {
    service: MIMO_KEYCHAIN_SERVICE,
    accounts: ["MIMO_API_KEY", "MIMO_BASE_URL", "MIMO_MODEL"]
  };
}
