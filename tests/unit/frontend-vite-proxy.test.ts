import { describe, expect, test } from "vitest";
import viteConfig from "../../frontend/vite.config.js";

describe("frontend Vite proxy contract", () => {
  test("dev server proxies backend API and health endpoints to the Java backend", () => {
    expect(viteConfig.server?.proxy).toMatchObject({
      "/v1": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
      "/health": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
      "/ready": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
    });
  });
});
