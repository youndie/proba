import type { FieldValue, FormCondition, FormFieldDefinition, FormPatch, FormSchema } from "../generated/kompot";

/**
 * The form engine of SPEC §9, run entirely on the client.
 *
 * Visibility, validation and the payload are decided here and not asked of the server, which is what
 * makes a form usable offline and what makes the rules testable without HTTP. The server keeps the
 * business validation: passing every rule here is not permission to succeed there (§9.5).
 */
export interface FormClient {
  /** Fields whose condition holds. A field that is not here is not drawn, not validated and not sent. */
  visibleFields(): string[];

  /** Errors a person should be seeing right now — not every error the values would produce. */
  errors(): Readonly<Record<string, string>>;

  value(fieldId: string): FieldValue | undefined;

  setValue(fieldId: string, value: FieldValue | undefined): void;

  /** Validation waits for this: an error raised while someone is still typing is noise (§9.5). */
  blur(fieldId: string): void;

  /** A patch changes values, never the set of fields — a changed set is a whole new response (§9.6). */
  applyPatch(patch: FormPatch): void;

  submit(): SubmitResult;
}

export interface SubmitResult {
  blocked: boolean;
  payload: Record<string, FieldValue>;
  errors: Readonly<Record<string, string>>;
  /** Named by a patch, carried through so a host can move the cursor there. */
  focusOn?: string;
}

/** Rules this engine does not enforce, so that "no error" and "never checked" are distinguishable. */
export interface UnenforcedRule {
  fieldId: string;
  type: string;
}

export function createFormClient(
  schema: FormSchema,
  /** The draft a caller already had. It beats the schema's initialValue: one is what was, the other is a suggestion (§9.7). */
  draft: Record<string, FieldValue> = {},
): FormClient & { unenforcedRules(): UnenforcedRule[] } {
  const fields = schema.fields ?? [];
  const values: Record<string, FieldValue> = {};
  const touched = new Set<string>();
  let submitted = false;
  let focusOn: string | undefined;

  for (const field of fields) {
    const initial = (field as { initialValue?: FieldValue | null }).initialValue;
    const existing = draft[field.fieldId];
    if (existing !== undefined) values[field.fieldId] = existing;
    else if (initial != null) values[field.fieldId] = initial;
  }

  const byId = new Map(fields.map((field) => [field.fieldId, field]));

  const visible = (): FormFieldDefinition[] => fields.filter((field) => holds(field.visibleIf, values));

  const failure = (field: FormFieldDefinition): string | undefined => {
    // In order, and the first failure wins: `regex` lets an empty value through on purpose, so the
    // whole difference between a required field and an optional one is that `required` comes first.
    for (const rule of field.rules ?? []) {
      const message = check(rule, values[field.fieldId], values);
      if (message !== undefined) return message;
    }
    return undefined;
  };

  const allErrors = (): Record<string, string> => {
    const found: Record<string, string> = {};
    for (const field of visible()) {
      const message = failure(field);
      if (message !== undefined) found[field.fieldId] = message;
    }
    return found;
  };

  const shownErrors = (): Record<string, string> => {
    const all = allErrors();
    if (submitted) return all;
    // Only what the person has finished with. A field they have not left yet is a field they may
    // still be typing into.
    return Object.fromEntries(Object.entries(all).filter(([fieldId]) => touched.has(fieldId)));
  };

  return {
    visibleFields: () => visible().map((field) => field.fieldId),

    errors: shownErrors,

    value: (fieldId) => values[fieldId],

    setValue(fieldId, value) {
      if (value === undefined) delete values[fieldId];
      else values[fieldId] = value;
    },

    blur(fieldId) {
      if (byId.has(fieldId)) touched.add(fieldId);
    },

    applyPatch(patch) {
      for (const [fieldId, value] of Object.entries(patch.updates ?? {})) values[fieldId] = value;
      // A cleared field is empty, not absent-and-therefore-fine: it goes back to failing whatever
      // rules it has, which is why clearing a required field blocks a submit.
      for (const fieldId of patch.clearFields ?? []) delete values[fieldId];
      focusOn = patch.focusOn ?? undefined;
    },

    submit() {
      submitted = true;
      const errors = allErrors();
      const payload: Record<string, FieldValue> = {};
      for (const field of visible()) {
        const value = values[field.fieldId];
        // A hidden field's value never reaches here, even one that was typed before the condition
        // turned false: it is the value a person can no longer see and can no longer remove (§9.4).
        if (value !== undefined) payload[field.fieldId] = value;
      }
      return { blocked: Object.keys(errors).length > 0, payload, errors, focusOn };
    },

    unenforcedRules: () =>
      fields.flatMap((field) =>
        (field.rules ?? [])
          .filter((rule) => !enforced.has(rule.type))
          .map((rule) => ({ fieldId: field.fieldId, type: rule.type })),
      ),
  };
}

const enforced = new Set(["required", "regex", "required_if"]);

function check(
  rule: { type: string; [k: string]: unknown },
  value: FieldValue | undefined,
  values: Record<string, FieldValue>,
): string | undefined {
  switch (rule.type) {
    case "required":
      return empty(value) ? String(rule.errorMessage) : undefined;

    case "regex": {
      // An empty value passes, or an optional field could never be left empty (§9.5).
      if (empty(value)) return undefined;
      const pattern = new RegExp(String(rule.pattern));
      return pattern.test(asText(value)) ? undefined : String(rule.errorMessage);
    }

    case "required_if": {
      const target = values[String(rule.targetFieldId)];
      const applies = same(target, rule.expectedValue as FieldValue);
      return applies && empty(value) ? String(rule.errorMessage) : undefined;
    }

    default:
      // The rule set is a plug-in one, so an unfamiliar rule costs its check and not the form. It is
      // reported by unenforcedRules() so that this stays distinguishable from having passed.
      return undefined;
  }
}

function holds(condition: FormCondition | null | undefined, values: Record<string, FieldValue>): boolean {
  if (!condition) return true;
  const actual = values[String((condition as { fieldId?: unknown }).fieldId)];
  const expected = (condition as { expectedValue?: FieldValue }).expectedValue;
  switch (condition.type) {
    case "equals":
      return same(actual, expected);
    case "not_equals":
      return !same(actual, expected);
    default:
      // An unknown condition cannot be judged. Showing the field is the safer half of being wrong:
      // hiding it would drop it from the payload as well, silently.
      return true;
  }
}

function empty(value: FieldValue | undefined): boolean {
  if (value === undefined || value === null) return true;
  switch (value.type) {
    case "text_value":
      return String((value as { text?: unknown }).text ?? "").trim() === "";
    case "entity_value":
      return String((value as { id?: unknown }).id ?? "") === "";
    case "boolean_value":
      return (value as { value?: unknown }).value !== true;
    case "amount_value":
      return (value as { long?: unknown }).long == null;
    default:
      return false;
  }
}

function asText(value: FieldValue | undefined): string {
  if (value === undefined) return "";
  const candidate = (value as { text?: unknown; title?: unknown }).text ?? (value as { title?: unknown }).title;
  return candidate === undefined ? "" : String(candidate);
}

/** Structural, because a value is data: two entity values are the same value when they say the same thing. */
function same(a: FieldValue | undefined, b: FieldValue | undefined): boolean {
  if (a === undefined || b === undefined) return a === b;
  return stable(a) === stable(b);
}

function stable(value: unknown): string {
  if (value === null || typeof value !== "object") return JSON.stringify(value) ?? "null";
  if (Array.isArray(value)) return `[${value.map(stable).join(",")}]`;
  const entries = Object.entries(value as Record<string, unknown>)
    .filter(([, v]) => v !== undefined)
    .sort(([x], [y]) => (x < y ? -1 : x > y ? 1 : 0));
  return `{${entries.map(([k, v]) => `${JSON.stringify(k)}:${stable(v)}`).join(",")}}`;
}
