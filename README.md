# proba

**what a consumer actually gets when they add your library** — point it at a Maven coordinate and it
reads the publication from the other side of the wire, the way a stranger's build does

> 🧪 the build is green, the tests are green, the publish is green — and the artefact is still wrong

### 🤔 What it is

Everything that checks a library runs *inside* its own build: linters, binary-compatibility
validators, CI. They share the author's assumptions, and the defects that matter most are exactly
the ones those assumptions hide. A module whose every dependency is `implementation` compiles,
tests and publishes without a word of complaint, and the metadata it ships says a consumer needs
nothing but the Kotlin standard library. Only a consumer finds out.

`proba` looks from the consumer's side. It reads what is actually published — Gradle module
metadata, POMs, jars, klibs — and, where reading is not enough, resolves the coordinate with a real
build and reports what lands on the classpath.

- **the root module of a multiplatform library is a redirector** — a reader that does not follow
  `available-at` sees zero dependencies on every variant and calls every library healthy;
- **`api` and `runtime` differ in the data**, so what a consumer can compile against is a static
  property of the publication and needs no build to see;
- **what the public signatures mention** is not in the metadata at all — it is in the artefact, so
  the artefact gets read too;
- **the honest check is a consumer performing it**, so the confirming tier runs Gradle rather than
  a second implementation of Gradle's resolution rules.

### 🔬 Found by the first probe ever run

Two published versions of one library put a file with the *same name* on the consumer's classpath:

| coordinate asked for | file the consumer receives |
| --- | --- |
| `…:kompot-core:0.27.0.45` | `kompot-core-jvm-0.27.0.jar` |
| `…:kompot-core:0.27.0.46` | `kompot-core-jvm-0.27.0.jar` |

Anything that tells artefacts apart by file name — an SBOM, a licence scanner, fat-jar
deduplication, a dependency-report diff — cannot tell those two apart.

### 🤖 In a build

```yaml
- uses: youndie/proba@main
  with:
    coordinate: |
      io.github.youndie:kompot-core:0.27.1.50
      io.github.youndie:kompot-standard:0.27.1.50
    deep: "true"        # run a real consumer build; the only way to confirm a suspicion
    fail-on: defect     # defect | suspicion | none
```

It runs on the runner rather than behind an API, so a publication you have not released yet is never
handed to somebody else's service to be looked at. The findings land in the step summary, and the
step fails on a defect — never on an *undetermined*, which means a check could not run here: turning
"I do not know" into a red build teaches people to switch the answers off along with the noise.

### 🏷 A badge

```markdown
![proba](https://your-proba/badge/io.github.youndie/kompot-core/0.27.1.50.svg)
```

The state is a **word** — `clean`, `2 defects`, `1 unchecked` — and the colour only agrees with it. A
badge is the smallest surface the severity language has to survive on and the one most often read
where colour is not available at all; the same sentence is the `aria-label`, which is what is left
when the image does not load.

`1 unchecked` is not `clean`: a check that could not run is not a check that passed, and a badge is
seen most and inspected least.

### 📐 How it is put together

| part | what for |
| --- | --- |
| `reader` | reads a publication from a repository: follows the redirector, keeps `api` and `runtime` apart |
| `checks` | the questions, and the corpus of cases each one must fire and stay quiet on |
| `resolver` | the confirming tier: a real consumer build, and the public API read out of the artefact |
| `server` | Kotlin + Ktor: runs the checks and describes the report as a kompot screen |
| `packages/proba-web` | Next.js: the report pages, server-rendered and shareable |
| `packages/kompot-web` | a React renderer for [kompot](https://github.com/youndie/kompot) screens, typed from the published wire schemas |
| `research-stand` | the consumer build that produced the numbers in the research document |

Documentation is in Russian and lives in [docs/](docs/); the research document records what was
verified, what was decided, and what is still a hypothesis.

### 📄 Licence

MIT
