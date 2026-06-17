import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const backendTarget = process.env.MY_WORKFLOW_BACKEND_URL ?? "http://127.0.0.1:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      "/v1": {
        target: backendTarget,
        changeOrigin: true,
      },
      "/health": {
        target: backendTarget,
        changeOrigin: true,
      },
      "/ready": {
        target: backendTarget,
        changeOrigin: true,
      },
    },
  },
});
