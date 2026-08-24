"use client";

import { useEffect, useState } from "react";
import type { AnyComponent } from "../types";
import { applyUpdate, type UpdateComponentMessage } from "./updates";

/**
 * Subscribes to a topic and hands each frame over.
 *
 * The transport is a parameter because the protocol does not fix one (SPEC §10.1): SSE is what the
 * Kotlin implementation uses and what a browser has natively, and a test has neither.
 */
export type Subscribe = (topic: string, onFrame: (message: UpdateComponentMessage) => void) => () => void;

export const sseSubscribe =
  (endpoint: (topic: string) => string): Subscribe =>
  (topic, onFrame) => {
    const source = new EventSource(endpoint(topic));
    source.onmessage = (event) => {
      // The stream opens with a frame carrying the topic rather than a component: it says the
      // subscription exists, which is otherwise indistinguishable from a channel that never speaks.
      if (!event.data.startsWith("{")) return;
      try {
        onFrame(JSON.parse(event.data) as UpdateComponentMessage);
      } catch {
        // A frame that will not parse costs that frame. The screen is still the one it was.
      }
    };
    return () => source.close();
  };

/**
 * The screen, kept up to date.
 *
 * The topic is opaque and comes from the server (SPEC §10.4): it is handed back as it arrived and
 * never parsed here.
 */
export function useLiveScreen(initial: AnyComponent, topic: string | null, subscribe: Subscribe): AnyComponent {
  const [screen, setScreen] = useState(initial);

  useEffect(() => setScreen(initial), [initial]);

  useEffect(() => {
    if (!topic) return;
    return subscribe(topic, (message) => setScreen((current) => applyUpdate(current, message)));
  }, [topic, subscribe]);

  return screen;
}
