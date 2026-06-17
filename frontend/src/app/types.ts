export type StatusTone = "green" | "gold" | "clay" | "blue" | "violet";

export type ChipView = {
  label: string;
  tone?: StatusTone;
};

export type TreeItemView = {
  id: string;
  icon: string;
  label: string;
  count: string;
  depth?: boolean;
  active?: boolean;
};

export type SourceRefView = {
  title: string;
  path: string;
};

export type PageLinkView = {
  title: string;
  subtitle: string;
};

export type MetricView = {
  label: string;
  value: string;
};

export type ArticleViewModel = {
  chips: ChipView[];
  title: string;
  subtitle: string;
  toc: string[];
  summary: string[];
  boundaryNote: string;
  metrics: MetricView[];
  boundaries: string[];
  sources: SourceRefView[];
  patch: {
    label: string;
    status: string;
    summary: string;
    targetPath: string;
  };
  links: PageLinkView[];
};

export type RunEventView = {
  time: string;
  label: string;
};

export type AssistantViewModel = {
  contextChips: string[];
  run: {
    title: string;
    id: string;
    progress: number;
    events: RunEventView[];
  };
  messages: Array<{
    author: "你" | "助手" | "安全检查";
    text: string;
    kind: "user" | "ai";
  }>;
  approval: {
    title: string;
    summary: string;
    artifact: string;
    target: string;
    wroteWorkspace: boolean;
    approvalId?: string;
    status?: string;
    decision?: "APPROVED" | "REJECTED" | null;
    artifactPreview?: {
      title: string;
      contentType: string;
      content: string;
    };
  };
  composerText: string;
};

export type WorkbenchViewModel = {
  workspaceName: string;
  breadcrumb: string[];
  treeItems: TreeItemView[];
  recentItems: TreeItemView[];
  graphSummary: string;
  article: ArticleViewModel;
  assistant: AssistantViewModel;
};
