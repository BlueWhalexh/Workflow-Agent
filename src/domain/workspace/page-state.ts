export type PageState = "BOOTSTRAP_MIRROR" | "AGENT_ORGANIZED" | "USER_EDITED" | "MIXED";

export function detectPageState(content: string): PageState {
  const hasAgentMeta = content.includes("<!-- agent-meta");
  const looksLikeMirror =
    content.includes("Raw mirror:") &&
    content.includes("Source path:") &&
    content.includes("## Content");

  if (!hasAgentMeta && looksLikeMirror) {
    return "BOOTSTRAP_MIRROR";
  }
  if (hasAgentMeta) {
    return "AGENT_ORGANIZED";
  }
  return "USER_EDITED";
}
