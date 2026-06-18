import {
  JAVA_BACKEND_API_SCHEMA,
  type ApiFetch,
  requestApiJson,
} from "../../shared/api/envelope.js";

const INTEGRATION_CONTRACT_SCHEMA = "java-backend-integration-contract.v1";

const REQUIRED_ENDPOINTS: IntegrationEndpointView[] = [
  { method: "GET", path: "/v1/me" },
  { method: "GET", path: "/v1/workspaces" },
  { method: "POST", path: "/v1/workspaces/{workspaceId}/agent-runs" },
  { method: "GET", path: "/v1/workspaces/{workspaceId}/agent-runs" },
  { method: "GET", path: "/v1/agent-runs/{runId}/events/stream" },
  { method: "GET", path: "/v1/agent-runs/{runId}/artifacts" },
  { method: "POST", path: "/v1/agent-runs/{runId}/approvals" },
  { method: "GET", path: "/v1/ops/integration-contract" },
];

const REQUIRED_CAPABILITIES: Array<keyof IntegrationCapabilitiesView> = [
  "asyncAgentRuns",
  "sseRunEvents",
  "approvalBoundary",
  "artifactRegistry",
];

type BackendIntegrationEndpointResponse = {
  method: string;
  path: string;
};

type BackendIntegrationContractResponse = {
  schemaVersion: string;
  publicEnvelopeSchema: string;
  frontendRequiredEndpoints: BackendIntegrationEndpointResponse[];
  capabilities: IntegrationCapabilitiesView;
};

export type IntegrationEndpointView = {
  method: string;
  path: string;
};

export type IntegrationCapabilitiesView = {
  asyncAgentRuns?: boolean;
  sseRunEvents?: boolean;
  approvalBoundary?: boolean;
  artifactRegistry?: boolean;
};

export type IntegrationContractView = {
  schemaVersion: string;
  publicEnvelopeSchema: string;
  frontendRequiredEndpoints: IntegrationEndpointView[];
  capabilities: IntegrationCapabilitiesView;
  frontendReady: boolean;
};

export async function loadIntegrationContract(fetcher: ApiFetch): Promise<IntegrationContractView> {
  const contract = await requestApiJson<BackendIntegrationContractResponse>(
    fetcher,
    "/v1/ops/integration-contract",
  );

  const view = {
    schemaVersion: contract.schemaVersion,
    publicEnvelopeSchema: contract.publicEnvelopeSchema,
    frontendRequiredEndpoints: contract.frontendRequiredEndpoints.map((endpoint) => ({
      method: endpoint.method.toUpperCase(),
      path: endpoint.path,
    })),
    capabilities: {
      asyncAgentRuns: contract.capabilities.asyncAgentRuns === true,
      sseRunEvents: contract.capabilities.sseRunEvents === true,
      approvalBoundary: contract.capabilities.approvalBoundary === true,
      artifactRegistry: contract.capabilities.artifactRegistry === true,
    },
  };

  return {
    ...view,
    frontendReady: isFrontendReadyContract(view),
  };
}

function isFrontendReadyContract(contract: Omit<IntegrationContractView, "frontendReady">): boolean {
  return (
    contract.schemaVersion === INTEGRATION_CONTRACT_SCHEMA &&
    contract.publicEnvelopeSchema === JAVA_BACKEND_API_SCHEMA &&
    hasRequiredEndpoints(contract.frontendRequiredEndpoints) &&
    REQUIRED_CAPABILITIES.every((capability) => contract.capabilities[capability] === true)
  );
}

function hasRequiredEndpoints(endpoints: IntegrationEndpointView[]): boolean {
  const available = new Set(endpoints.map((endpoint) => `${endpoint.method.toUpperCase()} ${endpoint.path}`));
  return REQUIRED_ENDPOINTS.every((endpoint) => available.has(`${endpoint.method} ${endpoint.path}`));
}
