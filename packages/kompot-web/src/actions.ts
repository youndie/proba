import type { AnyAction } from "./types";

/**
 * What a screen asks the host to do.
 *
 * The action hierarchy is open, so a handler receives types it does not know and must not treat that
 * as an error: an unknown action costs the tap, never the screen.
 */
export type ActionHandler = (action: AnyAction) => void;

export interface WebActionOptions {
  /** Navigation, page loads and closing belong to the application, not to the renderer. */
  onHostAction?: ActionHandler;
  onUnknownAction?: (action: AnyAction) => void;
  openUrl?: (url: string) => void;
  copyText?: (text: string) => void;
}

/** The two actions a browser can honour by itself; everything else is handed to the host. */
export function webActionHandler(options: WebActionOptions = {}): ActionHandler {
  const open = options.openUrl ?? ((url: string) => window.open(url, "_blank", "noopener,noreferrer"));
  const copy = options.copyText ?? ((text: string) => void navigator.clipboard?.writeText(text));

  return (action) => {
    switch (action.type) {
      case "open_url":
        open(String((action as { url?: unknown }).url ?? ""));
        return;
      case "copy_text":
        copy(String((action as { text?: unknown }).text ?? ""));
        return;
      case "navigate":
      case "load_page":
      case "close":
        options.onHostAction?.(action);
        return;
      default:
        (options.onUnknownAction ?? options.onHostAction)?.(action);
    }
  };
}
