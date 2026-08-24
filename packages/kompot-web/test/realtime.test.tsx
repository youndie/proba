import { act, render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { applyUpdate, KompotScreen, useLiveScreen, type Subscribe, type UpdateComponentMessage } from "../src";
import type { AnyComponent } from "../src";

const tree = {
  type: "column",
  id: "sweep",
  children: [
    { type: "text", id: "sweep-status", text: "0 of 2 read" },
    { type: "column", id: "m0", children: [{ type: "text", id: "m0-name", text: "waiting" }] },
  ],
} as unknown as AnyComponent;

const frame = (componentId: string, text: string): UpdateComponentMessage => ({
  componentId,
  component: { type: "text", id: componentId, text } as unknown as AnyComponent,
});

describe("a frame names a component already on screen", () => {
  it("replaces it wherever it sits in the tree", () => {
    const next = applyUpdate(tree, frame("m0-name", "done"));

    expect(JSON.stringify(next)).toContain("done");
    expect(JSON.stringify(next)).not.toContain("waiting");
  });

  it("ignores a frame for an id this screen does not have", () => {
    // Not appended, not thrown on: the frame is about a screen this client is not showing, which
    // happens to anyone who navigates while one is in flight.
    const next = applyUpdate(tree, frame("m9", "from another screen"));

    expect(next).toBe(tree);
    expect(JSON.stringify(next)).not.toContain("another screen");
  });

  it("leaves the rest of the tree identical, not merely equal", () => {
    // Same object, so React can skip the subtrees a frame did not touch. A rebuild of the whole tree
    // would be correct and would throw away the state of every list and input on the screen.
    const next = applyUpdate(tree, frame("sweep-status", "1 of 2 read")) as unknown as { children: unknown[] };
    const before = tree as unknown as { children: unknown[] };

    expect(next.children[1]).toBe(before.children[1]);
  });
});

describe("a live screen", () => {
  function Live(props: { subscribe: Subscribe; topic: string | null }) {
    const screen = useLiveScreen(tree, props.topic, props.subscribe);
    return <KompotScreen component={screen} />;
  }

  it("applies what arrives on the topic the server named", () => {
    let deliver: ((message: UpdateComponentMessage) => void) | undefined;
    const subscribe: Subscribe = (_topic, onFrame) => {
      deliver = onFrame;
      return () => {};
    };

    const { container } = render(<Live subscribe={subscribe} topic="sweep:abc" />);
    expect(container.textContent).toContain("0 of 2 read");

    act(() => deliver!(frame("sweep-status", "2 of 2 read")));

    expect(container.textContent).toContain("2 of 2 read");
  });

  it("subscribes to nothing when the server named no topic", () => {
    // A screen with no channel is a normal screen. Subscribing to "" would open a stream that can
    // never carry anything and would look, from the outside, exactly like one that works.
    const subscribe = vi.fn<Subscribe>(() => () => {});

    render(<Live subscribe={subscribe} topic={null} />);

    expect(subscribe).not.toHaveBeenCalled();
  });

  it("hands the topic back exactly as it arrived", () => {
    const subscribe = vi.fn<Subscribe>(() => () => {});

    render(<Live subscribe={subscribe} topic="sweep:io_github_youndie_2" />);

    expect(subscribe.mock.calls[0]![0]).toBe("sweep:io_github_youndie_2");
  });
});
