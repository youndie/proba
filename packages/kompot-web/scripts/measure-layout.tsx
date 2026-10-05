import { renderToStaticMarkup } from "react-dom/server";
import { chromium, type Browser } from "playwright";
import { KompotScreen, materialTheme } from "../src";
import type { AnyComponent } from "../src";

/**
 * The half of the tree the DOM tests cannot reach.
 *
 * jsdom computes no layout, so every assertion in `test/tree.test.tsx` is about the encoding: which
 * element carries which style. Whether that encoding draws the right picture is a question about
 * boxes, and only a browser has boxes. This measures them.
 *
 * The claim under test is the one the specification warns about (§5.2): a weighted child takes its
 * whole share rather than drawing itself to its content, so a background on it paints the share. It
 * is one-sided — with long text the child stretches to the constraint on its own and the wrong
 * encoding looks right — which is why every string here is two characters long.
 */
const WIDTH = 600;
const HEIGHT = 300;

const stack = (direction: "row" | "column", childModifiers: unknown[][]): AnyComponent =>
  ({
    type: direction,
    id: "s",
    modifiers: [{ type: "size", width: "Fill", height: "Fill" }],
    children: childModifiers.map((modifiers, at) => ({ type: "text", id: `c${at}`, text: "ok", modifiers })),
  }) as unknown as AnyComponent;

function page(tree: AnyComponent, breakFill: boolean): string {
  let body = renderToStaticMarkup(<KompotScreen component={tree} theme={materialTheme} />);
  if (breakFill) {
    // The negative control, made by removing exactly the thing under test from the output rather than
    // by changing the library.
    body = body.replaceAll("width:100%;height:100%;", "");
  }
  return `<!doctype html><html><body style="margin:0">
    <div id="frame" style="width:${WIDTH}px;height:${HEIGHT}px">${body}</div>
  </body></html>`;
}

async function measure(browser: Browser, tree: AnyComponent, breakFill: boolean) {
  const context = await browser.newContext({ viewport: { width: 900, height: 600 } });
  const sheet = await context.newPage();
  await sheet.setContent(page(tree, breakFill));
  const result = await sheet.evaluate(() => {
    const box = (element: Element | null) => {
      if (!element) return null;
      const rect = element.getBoundingClientRect();
      return { width: Math.round(rect.width), height: Math.round(rect.height) };
    };
    const stack = document.querySelector('[data-kompot="row"], [data-kompot="column"]')!;
    const first = stack.children[0]!;
    return {
      stack: box(stack),
      firstShare: box(first),
      secondShare: box(stack.children[1]!),
      painted: box(first.querySelector('[data-kompot-modifier="background"]') ?? first),
    };
  });
  await context.close();
  return result;
}

/** Where each child of the stack starts and ends along the row, relative to the row. */
async function edges(browser: Browser, tree: AnyComponent, rewrite: (markup: string) => string = (it) => it) {
  const context = await browser.newContext({ viewport: { width: 900, height: 600 } });
  const sheet = await context.newPage();
  await sheet.setContent(rewrite(page(tree, false)));
  const result = await sheet.evaluate(() => {
    const row = document.querySelector('[data-kompot="row"]')!;
    const origin = row.getBoundingClientRect().left;
    return Array.from(row.children).map((child) => {
      const rect = child.getBoundingClientRect();
      return { left: Math.round(rect.left - origin), right: Math.round(rect.right - origin) };
    });
  });
  await context.close();
  return result;
}

const arranged = (arrangement: string, childWidth: number): AnyComponent =>
  ({
    type: "row",
    id: "s",
    spacing: 16,
    arrangement,
    modifiers: [{ type: "size", width: "Fill" }],
    children: [0, 1, 2].map((at) => ({
      type: "text",
      id: `c${at}`,
      text: "ok",
      modifiers: [{ type: "size", widthDp: childWidth }],
    })),
  }) as unknown as AnyComponent;

// Content that cannot shrink: a fixed width does (a flex item gives way), one unbroken word does not.
// And no modifier on the row: the fill wrapper is a grid, whose item grows to its content, so a row
// inside one never overflows — the frame's own width is what holds this one.
const overflowing = (arrangement: string): AnyComponent =>
  ({
    type: "row",
    id: "s",
    spacing: 16,
    arrangement,
    children: [0, 1, 2].map((at) => ({ type: "text", id: `c${at}`, text: "W".repeat(40) })),
  }) as unknown as AnyComponent;

const problems: string[] = [];
const say = (ok: boolean, message: string) => {
  console.log(`  ${ok ? "ok  " : "FAIL"}  ${message}`);
  if (!ok) problems.push(message);
};

/**
 * The Chrome that is already there, and a bundled one only if it is not.
 *
 * A browser check that costs a 130 MB download on every machine is a check people switch off, and a
 * check nobody runs is worth less than no check at all — it also claims coverage. Both GitHub's
 * runners and this laptop have Chrome installed.
 */
async function open(): Promise<Browser> {
  const channel = process.env.PROBA_BROWSER_CHANNEL ?? "chrome";
  try {
    return await chromium.launch({ channel });
  } catch (failure) {
    console.log(`  (no ${channel}: ${String(failure).split("\n")[0]}) — falling back to the bundled browser`);
    return await chromium.launch();
  }
}

const browser = await open();

try {
  console.log("a weighted child takes its share, measured");
  const shares = await measure(
    browser,
    stack("row", [
      [{ type: "weight", value: 2 }, { type: "background", color: "primary" }],
      [{ type: "weight", value: 1 }, { type: "background", color: "secondary" }],
    ]),
    false,
  );
  say(shares.stack!.width === WIDTH, `the row fills its frame — ${shares.stack!.width} of ${WIDTH}`);
  say(
    Math.abs(shares.firstShare!.width - (WIDTH * 2) / 3) <= 1,
    `two thirds to the child that asked for two — ${shares.firstShare!.width}px`,
  );
  say(Math.abs(shares.secondShare!.width - WIDTH / 3) <= 1, `one third to the other — ${shares.secondShare!.width}px`);

  console.log("a background below the share paints the share, not the text");
  // Measured down the column, because that is the axis where the answer differs. A block element
  // already fills its parent's width without being told to, so removing the fill and measuring width
  // shows nothing — the first version of this check did exactly that and reported no difference at
  // all, which read as the library being fine rather than the measurement being blind.
  const column = stack("column", [
    [{ type: "padding", all: 8 }, { type: "weight", value: 2 }, { type: "background", color: "primary" }],
    [{ type: "weight", value: 1 }, { type: "background", color: "secondary" }],
  ]);
  const painted = await measure(browser, column, false);
  // Against the share that was actually handed out, not against arithmetic done in my head: with
  // border-box and a flex-basis of zero the padding is a floor that does not scale, so the share is
  // not exactly two thirds and never was.
  const inside = painted.firstShare!.height - 16;
  say(
    Math.abs(painted.painted!.height - inside) <= 1,
    `the painted box is the share less its padding — ${painted.painted!.height}px of a ${painted.firstShare!.height}px share`,
  );
  say(painted.painted!.height > 100, `and that is a share, not a line of text — ${painted.painted!.height}px`);

  console.log("and the measurement can tell the difference");
  const broken = await measure(browser, column, true);
  say(
    broken.painted!.height < painted.painted!.height / 2,
    `without filling, the same box paints its content — ${broken.painted!.height}px against ${painted.painted!.height}`,
  );

  console.log("arrangement shares what is left over spacing, and an overflow starts at the start (SPEC §4.7)");
  const between = await edges(browser, arranged("space_between", 100));
  say(between[0]!.left === 0 && between[2]!.right === WIDTH, `space_between reaches both ends — ${between[0]!.left}..${between[2]!.right}`);
  const roomy = between[1]!.left - between[0]!.right;
  say(roomy > 16, `and shares the rest between neighbours — a ${roomy}px gap`);
  const tight = await edges(browser, arranged("space_between", 196));
  const squeezed = Math.min(tight[1]!.left - tight[0]!.right, tight[2]!.left - tight[1]!.right);
  say(squeezed === 16, `with almost no room left, spacing is still the gap — ${squeezed}px`);
  const overflow = await edges(browser, overflowing("center"));
  say(overflow[0]!.left === 0, `a centred row wider than its frame starts at its leading edge — first child at ${overflow[0]!.left}px`);
  // The negative control: plain `center`, which is what the encoding would be without `safe`.
  const unsafe = await edges(browser, overflowing("center"), (markup) => markup.replaceAll("safe center", "center"));
  say(unsafe[0]!.left < 0, `and without "safe" it would not — first child at ${unsafe[0]!.left}px`);
} finally {
  await browser.close();
}

if (problems.length > 0) {
  console.error(`\nGATE FAILED: ${problems.length} of the measurements did not hold`);
  process.exit(1);
}
console.log("\nGATE PASSED — the share is what gets painted, and spacing is the smallest gap an arrangement leaves");
