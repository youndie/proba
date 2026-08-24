import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { KompotScreen } from "../src";
import type { AnyComponent } from "../src";

/**
 * What these tests hold, and what they do not.
 *
 * jsdom computes no layout: every rectangle it reports is zero, so nothing here can say what the
 * screen looks like. They hold the encoding — which element carries which style, and in what order
 * elements nest — which is where the two mistakes the spec warns about actually live. Whether the
 * encoding draws the right picture is a question for a browser, and that is a separate check.
 */

const draw = (component: AnyComponent) => render(<KompotScreen component={component} />).container;

const modifier = (root: HTMLElement, type: string) =>
  root.querySelector<HTMLElement>(`[data-kompot-modifier="${type}"]`);

describe("the modifier chain is ordered", () => {
  const withChain = (modifiers: unknown[]): AnyComponent =>
    ({ type: "text", id: "t", text: "ok", modifiers }) as unknown as AnyComponent;

  it("puts the first node outermost, so padding shrinks what a later background covers", () => {
    const root = draw(withChain([{ type: "padding", all: 8 }, { type: "background", color: "primary" }]));

    const padding = modifier(root, "padding")!;
    expect(padding).not.toBeNull();
    expect(padding.contains(modifier(root, "background"))).toBe(true);
    expect(padding.style.padding).toBe("8px");
  });

  it("swapping the two swaps the nesting, because the result is not the same screen", () => {
    const root = draw(withChain([{ type: "background", color: "primary" }, { type: "padding", all: 8 }]));

    const background = modifier(root, "background")!;
    expect(background.contains(modifier(root, "padding"))).toBe(true);
  });

  it("spends one element per node, because one box cannot hold two orders", () => {
    // Written after a first version of this test failed to catch what it was written for. It compared
    // the markup of the two orders and expected it to differ — which it does even when the chain is
    // folded into a single element, since the styles land in a different order inside the one style
    // attribute. Counting the elements is what actually separates the two implementations.
    const root = draw(withChain([{ type: "padding", all: 8 }, { type: "background", color: "primary" }]));

    expect(root.querySelectorAll("[data-kompot-modifier]")).toHaveLength(2);
  });
});

describe("a weighted node takes its share", () => {
  const row = (childText: string, modifiers: unknown[]): AnyComponent =>
    ({
      type: "row",
      id: "r",
      children: [{ type: "text", id: "c", text: childText, modifiers }],
    }) as unknown as AnyComponent;

  it("carries the share on the flex item, and the background with it", () => {
    const root = draw(row("ok", [{ type: "weight", value: 2 }, { type: "background", color: "primary" }]));

    const background = modifier(root, "background")!;
    expect(background.style.flexGrow).toBe("2");
    expect(background.style.flexBasis).toBe("0px");
    expect(background.style.background).not.toBe("");
  });

  it("makes a background below the share fill it rather than the content", () => {
    const root = draw(
      row("ok", [
        { type: "padding", all: 4 },
        { type: "weight", value: 1 },
        { type: "background", color: "primary" },
      ]),
    );

    const padding = modifier(root, "padding")!;
    const background = modifier(root, "background")!;

    expect(padding.style.flexGrow).toBe("1");
    expect(background.style.width).toBe("100%");
    expect(background.style.height).toBe("100%");
  });

  it("does not depend on how long the text is", () => {
    // The mistake this guards against is one-sided: with long text the child stretches to the
    // constraint on its own, so the wrong encoding looks right, and only short strings show it. A
    // test written on long text would agree with the defect.
    const short = draw(row("ok", [{ type: "weight", value: 1 }, { type: "background", color: "primary" }]));
    const long = draw(row("ok ".repeat(200), [{ type: "weight", value: 1 }, { type: "background", color: "primary" }]));

    expect(modifier(short, "background")!.getAttribute("style")).toBe(
      modifier(long, "background")!.getAttribute("style"),
    );
  });

  it("gives no share to a child that asked for none", () => {
    // Without this the assertions above are satisfied by an implementation that weights everything.
    const root = draw(row("ok", [{ type: "background", color: "primary" }]));

    expect(modifier(root, "background")!.style.flexGrow).toBe("");
  });
});

describe("size", () => {
  const sized = (size: Record<string, unknown>): AnyComponent =>
    ({ type: "text", id: "t", text: "ok", modifiers: [{ type: "size", ...size }] }) as unknown as AnyComponent;

  it("takes the number and ignores the symbol on the same axis", () => {
    const root = draw(sized({ type: "size", width: "Fill", widthDp: 120 }));

    expect(modifier(root, "size")!.style.width).toBe("120px");
  });

  it("treats Wrap as saying nothing, because that is what a box does unasked", () => {
    const root = draw(sized({ type: "size", width: "Wrap", height: "Wrap" }));

    const element = modifier(root, "size")!;
    expect(element.style.width).toBe("");
    expect(element.style.height).toBe("");
  });
});

describe("an open hierarchy degrades", () => {
  it("draws a placeholder for a type this client has never heard of", () => {
    const root = draw({
      type: "column",
      id: "c",
      children: [
        { type: "text", id: "a", text: "before" },
        { type: "promo_carousel", id: "b", slides: 3 },
        { type: "text", id: "d", text: "after" },
      ],
    } as unknown as AnyComponent);

    expect(root.textContent).toContain("before");
    expect(root.textContent).toContain("after");
    expect(root.querySelector('[data-kompot-unknown="promo_carousel"]')).not.toBeNull();
  });

  it("loses styling and not the screen when a token is unknown", () => {
    const root = draw({
      type: "text",
      id: "t",
      text: "still here",
      modifiers: [{ type: "background", color: "promo_gold" }],
    } as unknown as AnyComponent);

    expect(root.textContent).toBe("still here");
    expect(modifier(root, "background")!.style.background).toBe("");
  });
});

describe("actions", () => {
  it("hands the button's action to the host", () => {
    const onAction = vi.fn();
    const { container } = render(
      <KompotScreen
        component={
          {
            type: "button",
            id: "b",
            text: "go",
            action: { type: "open_url", url: "https://example.org" },
          } as unknown as AnyComponent
        }
        onAction={onAction}
      />,
    );

    container.querySelector("button")!.click();

    expect(onAction).toHaveBeenCalledWith({ type: "open_url", url: "https://example.org" });
  });
});

describe("a constraint reaches the nodes after it", () => {
  const marker = (modifiers: unknown[]): AnyComponent =>
    ({ type: "column", id: "m", children: [], modifiers }) as unknown as AnyComponent;

  it("fills a fixed size with a background that follows it", () => {
    // Found by rendering rather than by reading: the severity mark is an empty box with a size and a
    // colour, and the colour had nowhere to go. In Compose `size(12.dp).background(c)` is a 12dp
    // square, because background fills the constraints it is handed; a nested element does not, and
    // around empty content it collapses to nothing at all.
    const root = draw(marker([{ type: "size", widthDp: 12, heightDp: 12 }, { type: "background", color: "primary" }]));

    const background = modifier(root, "background")!;
    expect(background.style.width).toBe("100%");
    expect(background.style.height).toBe("100%");
  });

  it("carries the constraint per axis, not for the whole element", () => {
    const root = draw(marker([{ type: "size", widthDp: 12 }, { type: "background", color: "primary" }]));

    const background = modifier(root, "background")!;
    expect(background.style.width).toBe("100%");
    expect(background.style.height).toBe("");
  });

  it("leaves an unconstrained chain alone, so a padded box does not stretch across its row", () => {
    const root = draw(marker([{ type: "padding", all: 8 }, { type: "background", color: "primary" }]));

    expect(modifier(root, "background")!.style.width).toBe("");
  });
});

describe("the component's own element inherits what the chain constrained", () => {
  it("stretches inside a chain that fixed a size", () => {
    // Found by measuring, not by reading: a column inside a chain that fixed a height was a flex
    // container of content height sitting in a box of the right size. Invisible along the inline
    // axis, where a block element fills its parent anyway; plain along the block axis, where the
    // measured share was 16px of a 300px frame.
    const root = draw({
      type: "column",
      id: "c",
      children: [{ type: "text", id: "t", text: "ok" }],
      modifiers: [{ type: "size", width: "Fill", height: "Fill" }],
    } as unknown as AnyComponent);

    const fill = root.querySelector<HTMLElement>("[data-kompot-fill]")!;
    expect(fill).not.toBeNull();
    expect(fill.style.height).toBe("100%");
    // A grid, so its single child stretches without needing a property it cannot be given from
    // outside.
    expect(fill.style.display).toBe("grid");
    expect(fill.querySelector('[data-kompot="column"]')).not.toBeNull();
  });

  it("adds nothing when the chain constrained nothing", () => {
    const root = draw({
      type: "column",
      id: "c",
      children: [],
      modifiers: [{ type: "padding", all: 8 }],
    } as unknown as AnyComponent);

    expect(root.querySelector("[data-kompot-fill]")).toBeNull();
  });
})

describe("a colour the server named", () => {
  const text = (extra: Record<string, unknown>): AnyComponent =>
    ({ type: "text", id: "t", text: "ok", ...extra }) as unknown as AnyComponent;

  it("paints the letters with the token the node carries", () => {
    const root = draw(text({ color: "error" }));

    expect(root.querySelector<HTMLElement>('[data-kompot="text"]')!.style.color).toBe("rgb(179, 38, 30)");
  });

  it("leaves the letters to the background pairing when the node names none", () => {
    // The mechanism that existed before the wire could carry a colour at all, and still the answer
    // for every node that does not ask for one.
    const root = draw({
      type: "column",
      id: "c",
      modifiers: [{ type: "background", color: "primary" }],
      children: [text({})],
    } as unknown as AnyComponent);

    expect(root.querySelector<HTMLElement>('[data-kompot="text"]')!.style.color).toBe("");
    expect(modifier(root, "background")!.style.color).toBe("rgb(255, 255, 255)");
  });

  it("costs the colour and not the text when the token is unknown", () => {
    const root = draw(text({ color: "promo_gold" }));

    const span = root.querySelector<HTMLElement>('[data-kompot="text"]')!;
    expect(span.style.color).toBe("");
    expect(span.textContent).toBe("ok");
  });

  it("colours one word inside a sentence, which a modifier could never do", () => {
    // Spans carry no modifiers, so before this field the only way to colour part of a line was not
    // to have one.
    const root = draw(
      text({
        text: "",
        spans: [
          { text: "paid " },
          { text: "1200", color: "error" },
        ],
      }),
    );

    const coloured = Array.from(root.querySelectorAll<HTMLElement>("span")).find((it) => it.textContent === "1200")!;
    expect(coloured.style.color).toBe("rgb(179, 38, 30)");
  });
});
