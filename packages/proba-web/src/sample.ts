import { shapes, type SeverityName } from "./theme";

/**
 * A stand-in for the server, so the design can be looked at before the server exists.
 *
 * It builds the same thing the Kotlin server will build and by the same means — six component types,
 * five modifier nodes, token names and nothing else. Its output is a plain screen document, so when
 * the server starts emitting one this producer is deleted and the page that renders it does not
 * change.
 */
type Node = Record<string, unknown>;

const text = (id: string, value: string, style: string, extra: Node = {}): Node => ({
  type: "text",
  id,
  text: value,
  style,
  ...extra,
});

/**
 * The severity mark, drawn out of the modifiers the protocol actually has.
 *
 * There is no border node, so a hollow square is a coloured box with padding and a second box inside
 * it — the ordered chain turns that into a ring. A glyph would have been shorter and would depend on
 * the reader's fonts having it.
 */
function mark(id: string, severity: SeverityName): Node {
  const { color, shape, surface } = severityOf(severity);
  const box = { type: "size", widthDp: shapes.sizeDp, heightDp: shapes.sizeDp };

  if (shape === "solid") {
    return { type: "column", id, children: [], modifiers: [box, { type: "background", color }] };
  }
  if (shape === "half") {
    return {
      type: "column",
      id,
      children: [],
      modifiers: [box, { type: "gradient", colors: [color, surface] }],
    };
  }
  return {
    type: "column",
    id,
    children: [
      { type: "column", id: `${id}-hole`, children: [], modifiers: [{ type: "size", width: "Fill", height: "Fill" }, { type: "background", color: surface }] },
    ],
    modifiers: [box, { type: "background", color }, { type: "padding", all: shapes.ringDp }],
  };
}

function severityOf(severity: SeverityName) {
  const table = {
    defect: { color: "severity_defect", surface: "severity_defect_surface", shape: "solid", word: "defect" },
    suspicion: { color: "severity_suspicion", surface: "severity_suspicion_surface", shape: "half", word: "suspicion" },
    undetermined: { color: "severity_undetermined", surface: "severity_undetermined_surface", shape: "hollow", word: "undetermined" },
  } as const;
  return table[severity];
}

export interface SampleFinding {
  checkId: string;
  severity: SeverityName;
  subject: string;
  message: string;
  evidence: string[];
}

function finding(index: number, item: SampleFinding): Node {
  const { word, surface } = severityOf(item.severity);
  const id = `f${index}`;
  return {
    type: "column",
    id,
    spacing: 8,
    modifiers: [
      { type: "size", width: "Fill" },
      { type: "background", color: surface },
      { type: "padding", all: 20 },
    ],
    children: [
      {
        type: "row",
        id: `${id}-head`,
        spacing: 8,
        children: [
          // The mark and the word travel together: either one alone would be a colour with a shape,
          // or a shape with no name, and neither survives being read out loud or pasted as text.
          {
            type: "row",
            id: `${id}-badge`,
            spacing: 6,
            modifiers: [{ type: "background", color: surface }, { type: "padding", all: 6 }],
            children: [mark(`${id}-mark`, item.severity), text(`${id}-word`, word, "label")],
          },
          text(`${id}-check`, item.checkId, "code"),
        ],
      },
      text(`${id}-subject`, item.subject, "title_section"),
      text(`${id}-message`, item.message, "body"),
      {
        type: "column",
        id: `${id}-evidence`,
        spacing: 4,
        modifiers: [
          { type: "size", width: "Fill" },
          { type: "background", color: "surface_sunken" },
          { type: "padding", all: 12 },
        ],
        children: item.evidence.map((line, at) => text(`${id}-e${at}`, line, "code_small")),
      },
    ],
  };
}

export function sampleScreen(coordinate: string, findings: SampleFinding[]): Node {
  return {
    type: "column",
    id: "report",
    spacing: 16,
    modifiers: [
      { type: "size", width: "Fill" },
      { type: "background", color: "surface" },
      { type: "padding", all: 32 },
    ],
    children: [
      {
        type: "column",
        id: "header",
        spacing: 4,
        children: [
          text("title", coordinate, "title_page"),
          text("subtitle", `${findings.length} finding(s) from both tiers`, "body_small"),
        ],
      },
      ...findings.map((item, index) => finding(index, item)),
      {
        type: "row",
        id: "actions",
        spacing: 8,
        children: [
          { type: "button", id: "rerun", text: "Run again", action: { type: "load_page", url: "/report/rerun" } },
          { type: "button", id: "spec", text: "What this checks", variant: "text", action: { type: "open_url", url: "https://github.com/youndie/proba" } },
        ],
      },
    ],
  };
}
