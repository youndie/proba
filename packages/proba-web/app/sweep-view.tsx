"use client";

import { useMemo } from "react";
import { KompotScreen, sseSubscribe, useLiveScreen, webActionHandler } from "kompot-web";
import type { AnyComponent } from "kompot-web";
import { cssVariableTheme } from "../src/theme";

const server = process.env.NEXT_PUBLIC_PROBA_SERVER ?? "http://127.0.0.1:8081";

/**
 * The sweep, filling in as it runs.
 *
 * The topic came from the server and is passed back untouched (SPEC §10.4). When it is absent the
 * screen is simply a screen — no stream is opened, because a stream that can never carry anything
 * looks from the outside exactly like one that works.
 */
export function SweepView(props: { screen: AnyComponent; topic: string | null }) {
  const subscribe = useMemo(() => sseSubscribe((topic) => `${server}/updates/${encodeURIComponent(topic)}`), []);
  const screen = useLiveScreen(props.screen, props.topic, subscribe);

  return <KompotScreen component={screen} theme={cssVariableTheme} onAction={webActionHandler()} />;
}
