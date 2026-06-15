import { describe, expect, it } from "vitest";
import {
  configureMimoKeychainEnv,
  DEFAULT_MIMO_KEYCHAIN_BASE_URL,
  DEFAULT_MIMO_KEYCHAIN_MODEL,
  mergeMimoKeychainEnv,
  MIMO_KEYCHAIN_SERVICE,
  readMimoKeychainEnv
} from "../../src/runtime/provider/mimo-keychain.js";

describe("mimo keychain helper", () => {
  it("fills missing MiMo env from keychain values without overriding explicit env", () => {
    const merged = mergeMimoKeychainEnv(
      {
        MIMO_API_KEY: "explicit-key",
        MIMO_BASE_URL: undefined,
        MIMO_MODEL: ""
      },
      {
        MIMO_API_KEY: "keychain-key",
        MIMO_BASE_URL: "https://token-plan-cn.xiaomimimo.com/v1",
        MIMO_MODEL: "mimo-v2.5"
      }
    );

    expect(merged).toMatchObject({
      MIMO_API_KEY: "explicit-key",
      MIMO_BASE_URL: "https://token-plan-cn.xiaomimimo.com/v1",
      MIMO_MODEL: "mimo-v2.5"
    });
  });

  it("reads configured MiMo values from macOS Keychain accounts", async () => {
    const calls: string[][] = [];
    const env = await readMimoKeychainEnv({
      execFile: async (_file, args) => {
        calls.push(args);
        const account = args[args.indexOf("-a") + 1];
        return {
          stdout: `${account}-value\n`,
          stderr: ""
        };
      }
    });

    expect(env).toEqual({
      MIMO_API_KEY: "MIMO_API_KEY-value",
      MIMO_BASE_URL: "MIMO_BASE_URL-value",
      MIMO_MODEL: "MIMO_MODEL-value"
    });
    expect(calls).toHaveLength(3);
    expect(calls.every((args) => args.includes(MIMO_KEYCHAIN_SERVICE))).toBe(true);
  });

  it("configures MiMo keychain accounts without returning secret values", async () => {
    const calls: string[][] = [];
    const result = await configureMimoKeychainEnv({
      apiKey: "test-api-key",
      execFile: async (_file, args) => {
        calls.push(args);
        return { stdout: "", stderr: "" };
      }
    });

    expect(result).toEqual({
      service: MIMO_KEYCHAIN_SERVICE,
      accounts: ["MIMO_API_KEY", "MIMO_BASE_URL", "MIMO_MODEL"]
    });
    expect(calls).toHaveLength(3);
    expect(calls[0]).toContain("test-api-key");
    expect(calls[1]).toContain(DEFAULT_MIMO_KEYCHAIN_BASE_URL);
    expect(calls[2]).toContain(DEFAULT_MIMO_KEYCHAIN_MODEL);
    expect(JSON.stringify(result)).not.toContain("test-api-key");
  });
});
