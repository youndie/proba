# kompot-web

A React renderer for [kompot](https://github.com/youndie/kompot) screens: the server describes a
screen as a tree, the browser draws it, and a new screen ships without a new client release.

```tsx
import { KompotScreen, webActionHandler } from "kompot-web";

<KompotScreen component={screen} onAction={webActionHandler({ onHostAction: navigate })} />;
```

### Where the types come from

`src/generated/kompot.ts` is generated from the wire schemas **inside the published `kompot-spec`
artefact** — not from a copy kept here. The coordinate is in `package.json`:

```bash
pnpm schema        # regenerate from the pinned kompot-spec version
pnpm schema:check  # fail if the committed types are not what that version generates
```

A second copy of a contract is a second source of truth, and the one nobody regenerates is the one
that quietly stops being true.

### What it draws

Six components — `text`, `button`, `row`, `column`, `table`, `paginated_list` — five modifier nodes,
and the five standard actions.

- **the modifier chain is ordered**, so it becomes one element per node with the first outermost:
  `padding` then `background` covers less than `background` then `padding`, and a single element's
  style cannot express the difference;
- **a weighted child takes its whole share**, so a background on it paints the share rather than the
  text — which only looks wrong with short strings, and is why the tests use them;
- **dp is a CSS pixel**, one to one, because a CSS pixel is already density-independent;
- **an unknown component becomes a placeholder** and an unknown token loses its styling: the
  hierarchy is open so a server can ship a component before its clients know it, and that is worth
  nothing if the screen dies on arrival.

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

`max_amount_from_field` is not enforced: it is in the reference rule set and no case covers it, and a
rule implemented against nothing is a rule that passes for reasons nobody checked. It is reported by
`unenforcedRules()` so that "no error" stays distinguishable from "never checked".

### What it does not draw yet

Wizards, server-driven themes, and loading further pages of a `paginated_list` — the first page and
the empty state are drawn, and no control is offered for the rest. A button that silently fails would
be worse than one that is not there. An `autocomplete_input` without a host-supplied `suggest`
renders disabled and says why, rather than vanishing and leaving a form nobody can complete.

### What the tests establish

jsdom computes no layout: every rectangle it reports is zero. The tests hold the **encoding** —
which element carries which style, and how elements nest — which is where both mistakes the
specification warns about live. Whether that encoding draws the right picture is a question for a
browser, and it is a separate check that does not exist yet.
