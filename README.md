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

### 🔬 What it looks for

| check | the question |
| --- | --- |
| `version-in-declared-name` | does the file a consumer receives carry the version that was published? |
| `dangling-redirect` | is every target the root module points at actually there? |
| `component-matches-path` | does the module document agree with the coordinate it is published under? |
| `api-omits-sibling` | does what the api variant advertises cover the siblings the run time gets? |
| `api-unreachable` | can a consumer name every type the public API hands out? |
| `bytecode-java-version` | is the Java the classes require one a consumer can find out about? |

Findings come in three severities and each is a **word** before it is a colour. A *defect* is wrong
and can be pointed at. A *suspicion* is a shape defects take, and one the metadata cannot tell apart
from the correct case — `implementation` is right exactly when the public API does not mention the
dependency, which is why the confirming tier exists. An *undetermined* is a check that could not run
here, and it never fails a build: turning "I do not know" into red teaches people to switch the
answers off along with the noise.

### 🧪 The first thing it found, and what happened to it

Two published versions of one library put a file with the *same name* on the consumer's classpath:

| coordinate asked for | file the consumer receives |
| --- | --- |
| `…:kompot-core:0.27.0.45` | `kompot-core-jvm-0.27.0.jar` |
| `…:kompot-core:0.27.0.46` | `kompot-core-jvm-0.27.0.jar` |

Anything that tells artefacts apart by file name — an SBOM, a licence scanner, fat-jar
deduplication, a dependency-report diff — cannot tell those two apart. The build, the tests and the
publish were all green, and the version it records was never released at all.

It was [reported](https://github.com/youndie/kompot/issues/59), fixed, and on `0.27.1` that check is
silent: the file now arrives under the version it was published as. That loop — found by a consumer,
named precisely enough to fix, and then confirmed silent by the same tool — is the shape of the whole
thing.

The same run still reports a defect, from a different check and a
[different report](https://github.com/youndie/kompot/issues/64): those classes require Java 25 and
nothing in the metadata says so. Which is the point of naming checks separately rather than putting a
verdict on a library.

### ▶️ Running it

```bash
# one coordinate, from the repository alone
./gradlew :resolver:installDist
./resolver/build/install/resolver/bin/resolver io.ktor:ktor-client-core:3.4.0

# …and with a real consumer build behind it
./resolver/build/install/resolver/bin/resolver io.ktor:ktor-client-core:3.4.0 --deep

# your own publication, before it leaves the machine
./resolver/build/install/resolver/bin/resolver my.group:my-lib:1.0.0 --repo "file://$HOME/.m2/repository"
```

A report as a page, and a run across a whole group as it happens:

```bash
./gradlew :server:run                              # the checker, on 8081
pnpm --filter proba-web dev                        # the pages, on 3000
open http://localhost:3000/report/io.ktor/ktor-client-core/3.4.0
open http://localhost:3000/sweep/io.ktor
```

The sweep is where the live-update channel earns its keep: one coordinate answers inside a request,
a group of two hundred does not, and a screen that appears only when the last module is done is a
screen nobody watches.

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

Written by the build, not answered by a service. A publication never changes, so the state of a badge
changes when a version is released — which is exactly when the build runs anyway:

```yaml
- uses: youndie/proba@main
  with:
    coordinate: io.github.youndie:kompot-core:0.29.0.56
    badge-dir: badges              # badges/io.github.youndie.kompot-core.svg
```

Publish that file however you publish anything — Pages, a branch, a commit — and point the README at
it. Running instance optional: `GET /badge/{group}/{artifact}/{version}.svg` serves the same picture
for an ad-hoc query, and computes on every view what cannot change between them.

The left half is the **artefact**, not the tool: badges are read beside others of their kind, and a
row of them all saying `proba` is one picture repeated with no way to tell whose state is whose —
which is worst exactly when one of them says `2 defects`.

```
kompot-core: clean        kompot-client: 1 suspicion        kompot-forms: 2 defects
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
| `design` | the token vocabulary: one file, read by the Kotlin server and by the browser |
| `research-stand` | the stands behind the numbers, including a library published with a defect on purpose |
| `scripts` | the gates — one per milestone, each runnable |

Documentation is in Russian and lives in [docs/](docs/); the research document records what was
verified, what was decided, and what is still a hypothesis.

### 🚦 How it is kept honest

**A check without a case where it must fire is not written.** Staying quiet is also what a check does
when it cannot run, when its subject never reaches it, and when nobody wired it up — so every check
carries a case on each side, and a guard *per check* fails the build when one does not. A total over
all checks would be satisfied by the ones that do have cases, on behalf of the one that does not.

**One version, pinned once.** The server compiles against kompot, the renderer generates its types
from kompot'''s schemas, and the corpus is fetched from it. All three read `gradle/libs.versions.toml`,
because when each had its own pin a single dependency bump produced a repository at three different
versions at once — and nothing noticed, since `schema:check` and `corpus:check` each compare a
committed copy against *their own* pin. `scripts/check-kompot-pins.py` guards the thing that made
that possible: a second pin appearing again.

**Every gate has been watched failing.** `scripts/gate*.sh` are runnable, and each was run against a
broken version of the thing it guards. The library published with a defect on purpose lives in
`research-stand/broken-publication` and is switched by one word, so the two halves of the pair cannot
drift into being two different libraries.

Twice this cost a check rather than producing one. The rule about the Java a publication requires
started out as "the metadata does not declare it" — which is true of `kotlinx-coroutines` and `ktor`
too, and they exclude nobody; the signal is the bytecode. And a check about missing sources was
dropped outright: the positive control it was written for turned out to be a defect in this reader's
own grouping, and with that fixed there is no publication in reach that lacks them.

### 📄 Licence

MIT
