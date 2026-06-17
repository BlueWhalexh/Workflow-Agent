import { type ApiFetch, requestApiJson } from "../../shared/api/envelope.js";
import { sanitizeForPublicUi } from "../../shared/safety/public-fields.js";

type BackendArtifactResponse = {
  artifactId: string;
  runId: string;
  artifactRef: string;
  kind: string;
  redactionStatus: string;
  contentType: string;
  createdAt: string;
};

type BackendArtifactReadResponse = BackendArtifactResponse & {
  content: string;
};

export type RunArtifactView = {
  artifactId: string;
  runId: string;
  artifactRef: string;
  kind: string;
  redactionStatus: string;
  contentType: string;
  createdAt: string;
};

export type ArtifactContentView = RunArtifactView & {
  content: string;
};

export async function listRunArtifacts(fetcher: ApiFetch, runId: string): Promise<RunArtifactView[]> {
  const artifacts = await requestApiJson<BackendArtifactResponse[]>(
    fetcher,
    `/v1/agent-runs/${encodeURIComponent(runId)}/artifacts`,
  );
  return artifacts.map(toRunArtifactView);
}

export async function readArtifact(fetcher: ApiFetch, artifactId: string): Promise<ArtifactContentView> {
  return toArtifactContentView(await requestApiJson<BackendArtifactReadResponse>(
    fetcher,
    `/v1/artifacts/${encodeURIComponent(artifactId)}`,
  ));
}

function toRunArtifactView(artifact: BackendArtifactResponse): RunArtifactView {
  const publicArtifact = sanitizeForPublicUi(artifact) as BackendArtifactResponse;
  return {
    artifactId: publicArtifact.artifactId,
    runId: publicArtifact.runId,
    artifactRef: publicArtifact.artifactRef,
    kind: publicArtifact.kind,
    redactionStatus: publicArtifact.redactionStatus,
    contentType: publicArtifact.contentType,
    createdAt: publicArtifact.createdAt,
  };
}

function toArtifactContentView(artifact: BackendArtifactReadResponse): ArtifactContentView {
  const publicArtifact = sanitizeForPublicUi(artifact) as BackendArtifactReadResponse;
  return {
    ...toRunArtifactView(publicArtifact),
    content: publicArtifact.content,
  };
}
