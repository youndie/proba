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
} finally {
  await browser.close();
}

if (problems.length > 0) {
  console.error(`\nGATE FAILED: ${problems.length} of the measurements did not hold`);
  process.exit(1);
}
console.log("\nGATE PASSED — the share is what gets painted, and short text does not change that");
