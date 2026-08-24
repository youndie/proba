import Link from "next/link";

const examples = [
  { coordinate: "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0", note: "a healthy publication" },
  { coordinate: "io.ktor:ktor-client-core:3.4.0", note: "another one, 25 targets" },
];

export default function Home() {
  return (
    <main style={{ padding: "32px", maxWidth: "60ch" }}>
      <h1>proba</h1>
      <p>
        What a consumer actually gets when they add your library. Point it at a Maven coordinate and it
        reads the publication from the other side of the wire, the way a stranger&rsquo;s build does.
      </p>
      <ul>
        {examples.map((example) => {
          const [group, artifact, version] = example.coordinate.split(":");
          return (
            <li key={example.coordinate}>
              <Link href={`/report/${group}/${artifact}/${version}`}>{example.coordinate}</Link> — {example.note}
            </li>
          );
        })}
      </ul>
    </main>
  );
}
