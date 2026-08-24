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

### What it does not draw yet

Forms (SPEC §9), wizards, server-driven themes, and loading further pages of a `paginated_list` —
the first page and the empty state are drawn, and no control is offered for the rest. A button that
silently fails would be worse than one that is not there.

### What the tests establish

jsdom computes no layout: every rectangle it reports is zero. The tests hold the **encoding** —
which element carries which style, and how elements nest — which is where both mistakes the
specification warns about live. Whether that encoding draws the right picture is a question for a
browser, and it is a separate check that does not exist yet.
