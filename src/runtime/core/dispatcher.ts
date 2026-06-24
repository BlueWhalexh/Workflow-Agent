import type { AgentRuntimeAdapter, AgentRuntimeName } from "./types.js";

export interface RuntimeDispatcherConfig {
  defaultEngine: AgentRuntimeName;
  adapters: AgentRuntimeAdapter[];
}

export function selectRuntimeAdapter(
  config: RuntimeDispatcherConfig,
  requestedEngine?: AgentRuntimeName
): AgentRuntimeAdapter {
  const engine = requestedEngine ?? config.defaultEngine;
  const adapter = config.adapters.find((candidate) => candidate.engine === engine);
  if (!adapter) {
    throw new Error(`No runtime adapter registered for engine: ${engine}`);
  }
  return adapter;
}
