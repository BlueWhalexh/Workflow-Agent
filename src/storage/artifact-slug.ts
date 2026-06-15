export function assertSafeArtifactSlug(id: string): string {
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(id) || id.includes("..") || /t[p]-[A-Za-z0-9]/.test(id)) {
    throw new Error("artifact id must be a safe slug");
  }
  return id;
}
