# kompot-web

A React renderer for [kompot](https://github.com/youndie/kompot) screens: the server describes a
screen as a tree, the browser draws it, and a new screen ships without a new client release.

```tsx
import { KompotScreen, webActionHandler } from "kompot-web";

<KompotScreen component={screen} onAction={webActionHandler({ onHostAction: navigate })} />;
```

### Where the types come from

`src/generated/kompot.ts` is printed by **kompot's own generator** (`TypeScriptDeclarations` in the
published `kompot-spec` jar) from the wire schemas inside that same jar — the version the Gradle
catalogue pins, and nothing else. It is the open file kompot commits as `kompot-spec/types/kompot.d.ts`,
byte for byte below its first line: one source for the types, not two generators that could disagree.

```bash
pnpm schema        # = ./gradlew :server:kompotTypes, regenerate from the pinned kompot-spec
pnpm schema:check  # = ./gradlew :server:checkKompotTypes, part of ./gradlew check
```

The generator runs on the JVM, so the check lives in the Gradle build rather than in this package.
`KompotComponent` and `KompotAction` are unions of the known variants **plus** a branch for a type this
build has never seen (SPEC.md §2.1) — a `switch` on `type` that looks exhaustive is not.

A second copy of a contract is a second source of truth, and the one nobody regenerates is the one
that quietly stops being true.

### What it draws

`text`, `button`, `row`, `column`, `table`, `paginated_list`, `divider` and `spacer`, five modifier
nodes, and the five original standard actions.

- **the modifier chain is ordered**, so it becomes one element per node with the first outermost:
  `padding` then `background` covers less than `background` then `padding`, and a single element's
  style cannot express the difference;
- **a weighted child takes its whole share**, so a background on it paints the share rather than the
  text — which only looks wrong with short strings, and is why the tests use them;
- **dp is a CSS pixel**, one to one, because a CSS pixel is already density-independent;
- **`spacing` is the smallest gap an `arrangement` leaves** (§4.7) — `gap` beside `justify-content`,
  with `safe` on `center` and `end` so that a stack too wide for its frame starts at its leading edge.
  Both are measured in a browser, with the unsafe encoding beside them;
- **an unknown component takes the server's equivalent** — its `fallback`, degrading again one level
  down if that is unknown too — **and otherwise draws nothing** (§2.1). Either way it is reported to
  `onDegradation` with what was drawn instead: `server_fallback`, `nothing`, or `placeholder` when the
  host supplied `renderUnknown`. An unknown token loses its styling and nothing else.

### Forms

The engine of §9 runs entirely on the client: visibility, validation and the payload are decided
here, which is what makes a form usable offline and testable without HTTP. The server keeps the
business validation — passing every rule here is not permission to succeed there.

It is held by the **conformance corpus of the other implementation**, fetched from the published
`kompot-client-tck` artefact rather than restated here:

```bash
pnpm corpus        # refresh from the pinned kompot-client-tck version
pnpm corpus:check  # fail if this copy is not that version's corpus
```

Cases written here would agree with whatever this implementation believes. Those can disagree, which
is the only reason to have them.

A rule the engine does not know is reported by `unenforcedRules()`, so that "no error" stays
distinguishable from "never checked".

An `amount_input` draws its currency on the side the component names — `currencyPrefix` in front,
`currencySuffix` behind, the suffix if a server named both — whichever place the symbol came from, and
closes the gap only on `currencySpaced: false` (§9.7.10–12). A `background` with a `role` takes the
shape the theme gives that role and clips to it (`themeWith(…, shapes)`, §5.5); the Material theme,
like kompot's, gives none.

A field with `triggersPatch` makes one `FormPatchRequest` per change, carrying the whole form as it is
at that moment (§9.6). The engine records them (`requests()`, which the corpus reads) and `KompotForm`
hands each to the host's `requestPatch`, applying the patch it answers with. Without a `requestPatch`
the value changes and nothing is sent: the endpoint is the application's, as with `suggest`.

The runner stops on a key it does not know — in a case, in its steps, in its expectations — and reads
the keys a case may carry from `client-corpus.schema.json`, which travels with the cases (SPEC.md §17).

### What it does not draw yet

`box`, `tabs`, `expandable`, `image`, a scrolling `row`, and the actions kompot 0.38 added
(`show_message`, `confirm`, `present`, `sequence`, `refresh`, …) — each degrades as §2.1 says, and the
list is [#35](https://github.com/youndie/proba/issues/35). Also wizards, server-driven themes, and
loading further pages of a `paginated_list` — the first page and the empty state are drawn, and no
control is offered for the rest. A button that silently fails would be worse than one that is not
there. An `autocomplete_input` without a host-supplied `suggest` renders disabled and says why, rather
than vanishing and leaving a form nobody can complete.

### What the tests establish

jsdom computes no layout: every rectangle it reports is zero. The tests hold the **encoding** —
which element carries which style, and how elements nest — which is where both mistakes the
specification warns about live. Whether that encoding draws the right picture is a question for a
browser, and it is a separate check that does not exist yet.
