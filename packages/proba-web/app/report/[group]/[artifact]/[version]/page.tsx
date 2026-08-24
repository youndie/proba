import type { AnyComponent } from "kompot-web";
import { ReportView } from "../../../../report-view";

/**
 * A permanent address: everything the report is about is in the path, so the link somebody pastes
 * tomorrow asks the same question it asked today.
 */
export const dynamic = "force-dynamic";

const server = process.env.PROBA_SERVER ?? "http://127.0.0.1:8081";

export default async function ReportPage(props: {
  params: Promise<{ group: string; artifact: string; version: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { group, artifact, version } = await props.params;
  const search = await props.searchParams;
  const repo = typeof search.repo === "string" ? `?repo=${encodeURIComponent(search.repo)}` : "";

  const response = await fetch(`${server}/report/${group}/${artifact}/${version}${repo}`, { cache: "no-store" });
  if (!response.ok) {
    // Said in words rather than drawn as an empty screen: a page with nothing on it is exactly what a
    // healthy publication with nothing to report looks like.
    return (
      <main style={{ padding: "32px" }}>
        <h1>{`${group}:${artifact}:${version}`}</h1>
        <p>The checker did not answer ({response.status}).</p>
      </main>
    );
  }

  const screen = (await response.json()) as AnyComponent;
  return <ReportView screen={screen} />;
}
