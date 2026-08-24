import type { AnyComponent } from "kompot-web";
import { SweepView } from "../../sweep-view";

export const dynamic = "force-dynamic";

const server = process.env.PROBA_SERVER ?? "http://127.0.0.1:8081";

export default async function SweepPage(props: {
  params: Promise<{ group: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { group } = await props.params;
  const search = await props.searchParams;
  const repo = typeof search.repo === "string" ? `?repo=${encodeURIComponent(search.repo)}` : "";

  const response = await fetch(`${server}/sweep/${group}${repo}`, { cache: "no-store" });
  if (!response.ok) {
    return (
      <main style={{ padding: "32px" }}>
        <h1>{group}</h1>
        <p>{await response.text()}</p>
      </main>
    );
  }

  // A form response, because that is the only shape on the wire that can carry an update topic.
  const body = (await response.json()) as { screen: AnyComponent; realtimeTopic?: string | null };
  return <SweepView screen={body.screen} topic={body.realtimeTopic ?? null} />;
}
