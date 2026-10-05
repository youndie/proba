"use client";

// React Server Components cannot hold a context or a hook, and this file has both. Without the
// directive a framework that renders on the server refuses the whole import chain — which is every
// consumer who wanted server-rendered kompot screens in the first place.
import { createContext, useContext, useMemo, useRef, useState, type ReactNode } from "react";
import { KompotNode, useKompot } from "../render";
import type { AnyComponent } from "../types";
import type { FieldValue, FormPatch, FormPatchRequest, FormSchema, SelectOption } from "../generated/kompot";
import { createFormClient, type FormClient } from "./controller";

/**
 * The form components, bound to the engine of §9.
 *
 * The engine decides what is visible, what is wrong and what gets sent; these draw it. Keeping the
 * two apart is what let the engine be held to somebody else's conformance corpus without a browser
 * anywhere in sight.
 */
interface FormBinding {
  client: FormClient;
  revision: number;
  change(fieldId: string, value: FieldValue | undefined): void;
  leave(fieldId: string): void;
  /** Suggestions for an autocomplete field. Absent until a host supplies one. */
  suggest?: Suggest;
}

export type Suggest = (dataSourceId: string, query: string) => Promise<SelectOption[]>;

/**
 * Sends a patch request and answers with the server's patch (§9.6). The host's, like `suggest`: the
 * endpoint is the application's and this package has no HTTP in it.
 */
export type RequestPatch = (request: FormPatchRequest) => Promise<FormPatch | null | undefined>;

const FormContext = createContext<FormBinding | null>(null);

export function useForm(): FormBinding | null {
  return useContext(FormContext);
}

export function KompotForm(props: {
  schema: FormSchema;
  screen: AnyComponent;
  draft?: Record<string, FieldValue>;
  suggest?: Suggest;
  /** Without one, a field with `triggersPatch` changes its value and nothing else happens. */
  requestPatch?: RequestPatch;
  onSubmit?: (payload: Record<string, FieldValue>) => void;
  children?: ReactNode;
}): ReactNode {
  const [revision, setRevision] = useState(0);
  // Read at the moment of the request, so a host passing a new function each render does not rebuild
  // the engine and lose what was typed.
  const requestPatch = useRef(props.requestPatch);
  requestPatch.current = props.requestPatch;
  const client = useMemo(
    () =>
      createFormClient(props.schema, props.draft ?? {}, {
        onPatchRequest(request) {
          void requestPatch.current?.(request).then((patch) => {
            if (!patch) return;
            client.applyPatch(patch);
            setRevision((it) => it + 1);
          });
        },
      }),
    [props.schema, props.draft],
  );

  const binding: FormBinding = {
    client,
    revision,
    change(fieldId, value) {
      client.setValue(fieldId, value);
      setRevision((it) => it + 1);
    },
    leave(fieldId) {
      client.blur(fieldId);
      setRevision((it) => it + 1);
    },
    suggest: props.suggest,
  };

  return (
    <FormContext.Provider value={binding}>
      <KompotNode component={props.screen} />
      {props.children}
    </FormContext.Provider>
  );
}

/** A component bound to a field that the engine says is hidden draws nothing at all (§9.4). */
function useField(fieldId: string): { binding: FormBinding; visible: boolean; error?: string } | null {
  const binding = useForm();
  if (!binding) return null;
  const visible = binding.client.visibleFields().includes(fieldId);
  return { binding, visible, error: binding.client.errors()[fieldId] };
}

function Field(props: { fieldId: string; label: string; children: (bound: FormBinding) => ReactNode }): ReactNode {
  const bound = useField(props.fieldId);
  const { theme } = useKompot();
  if (!bound || !bound.visible) return null;
  return (
    <label data-kompot-field={props.fieldId} style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
      <span style={theme.typography("label")}>{props.label}</span>
      {props.children(bound.binding)}
      {bound.error !== undefined && (
        <span data-kompot-error={props.fieldId} style={{ ...theme.typography("body_small"), color: theme.color("error") }}>
          {bound.error}
        </span>
      )}
    </label>
  );
}

/**
 * Suggestions come from the host, because the data source is a server endpoint and this package has
 * no HTTP in it. Without one the input is disabled and says why: a field that silently disappeared
 * would leave a form nobody can complete and nothing on screen to explain it.
 */
function Autocomplete(props: {
  field: { fieldId: string; label: string; dataSourceId: string; placeholder?: string | null };
  binding: FormBinding;
}): ReactNode {
  const { field, binding } = props;
  const [query, setQuery] = useState("");
  const [options, setOptions] = useState<SelectOption[]>([]);
  const current = binding.client.value(field.fieldId);
  const chosen = current?.type === "entity_value" ? String((current as { title?: unknown }).title ?? "") : "";

  if (!binding.suggest) {
    return (
      <span data-kompot-degraded="autocomplete_input">
        <input disabled value={chosen} placeholder={field.placeholder ?? undefined} />
        <span>no suggestions are available here</span>
      </span>
    );
  }

  return (
    <span data-kompot="autocomplete">
      <input
        value={chosen || query}
        placeholder={field.placeholder ?? undefined}
        onBlur={() => binding.leave(field.fieldId)}
        onChange={(event) => {
          setQuery(event.target.value);
          binding.change(field.fieldId, undefined);
          void binding.suggest?.(field.dataSourceId, event.target.value).then(setOptions);
        }}
      />
      {options.map((option) => (
        <button
          type="button"
          key={option.id}
          onClick={() => {
            // Resolved to an entity, never left as the string that was typed (§9.7).
            binding.change(field.fieldId, {
              type: "entity_value",
              id: option.id,
              title: option.label,
              rawMetadata: option.rawMetadata ?? undefined,
            } as FieldValue);
            binding.leave(field.fieldId);
            setOptions([]);
          }}
        >
          {option.label}
        </button>
      ))}
    </span>
  );
}

const text = (value: FieldValue | undefined): string =>
  value && value.type === "text_value" ? String((value as { text?: unknown }).text ?? "") : "";

export const formRenderers: Record<string, (component: AnyComponent) => ReactNode> = {
  text_input: (raw) => {
    const c = raw as unknown as { fieldId: string; label: string; placeholder?: string | null; multiline?: boolean; secret?: boolean; uppercase?: boolean };
    return (
      <Field fieldId={c.fieldId} label={c.label}>
        {(binding) => {
          const value = text(binding.client.value(c.fieldId));
          const common = {
            value,
            placeholder: c.placeholder ?? undefined,
            onBlur: () => binding.leave(c.fieldId),
            onChange: (event: { target: { value: string } }) =>
              binding.change(c.fieldId, {
                type: "text_value",
                text: c.uppercase ? event.target.value.toUpperCase() : event.target.value,
              } as FieldValue),
          };
          return c.multiline ? <textarea {...common} /> : <input type={c.secret ? "password" : "text"} {...common} />;
        }}
      </Field>
    );
  },

  checkbox_input: (raw) => {
    const c = raw as unknown as { fieldId: string; label: string };
    return (
      <Field fieldId={c.fieldId} label={c.label}>
        {(binding) => {
          const current = binding.client.value(c.fieldId);
          const checked = current?.type === "boolean_value" && (current as { value?: unknown }).value === true;
          return (
            <input
              type="checkbox"
              checked={checked}
              onChange={(event) => {
                binding.change(c.fieldId, { type: "boolean_value", value: event.target.checked } as FieldValue);
                binding.leave(c.fieldId);
              }}
            />
          );
        }}
      </Field>
    );
  },

  select_input: (raw) => {
    const c = raw as unknown as { fieldId: string; label: string; options: SelectOption[]; placeholder?: string | null };
    return (
      <Field fieldId={c.fieldId} label={c.label}>
        {(binding) => {
          const current = binding.client.value(c.fieldId);
          const selected = current?.type === "entity_value" ? String((current as { id?: unknown }).id ?? "") : "";
          return (
            <select
              value={selected}
              onChange={(event) => {
                const option = c.options.find((it) => it.id === event.target.value);
                // A choice resolves to an entity value and not to a string, so the metadata a rule
                // reads locally — a currency, a balance — travels with it (§9.7).
                binding.change(
                  c.fieldId,
                  option
                    ? ({ type: "entity_value", id: option.id, title: option.label, rawMetadata: option.rawMetadata ?? undefined } as FieldValue)
                    : undefined,
                );
                binding.leave(c.fieldId);
              }}
            >
              <option value="">{c.placeholder ?? ""}</option>
              {c.options.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </select>
          );
        }}
      </Field>
    );
  },

  radio_group: (raw) => {
    const c = raw as unknown as { fieldId: string; label: string; options: SelectOption[] };
    return (
      <Field fieldId={c.fieldId} label={c.label}>
        {(binding) => {
          const current = binding.client.value(c.fieldId);
          const selected = current?.type === "entity_value" ? String((current as { id?: unknown }).id ?? "") : "";
          return (
            <span role="radiogroup">
              {c.options.map((option) => (
                <label key={option.id}>
                  <input
                    type="radio"
                    name={c.fieldId}
                    value={option.id}
                    checked={selected === option.id}
                    onChange={() => {
                      binding.change(c.fieldId, {
                        type: "entity_value",
                        id: option.id,
                        title: option.label,
                        rawMetadata: option.rawMetadata ?? undefined,
                      } as FieldValue);
                      binding.leave(c.fieldId);
                    }}
                  />
                  {option.label}
                </label>
              ))}
            </span>
          );
        }}
      </Field>
    );
  },

  amount_input: (raw) => {
    const c = raw as unknown as {
      fieldId: string;
      label: string;
      currencySuffix?: string | null;
      currencyPrefix?: string | null;
      currencyFromField?: string | null;
      currencySpaced?: boolean;
    };
    // The side is the component's to name, whichever place the symbol itself came from (§9.7.10); a
    // component that names both draws the suffix, because a client older than the prefix does
    // (§9.7.11); and the gap is a third thing the currency says, a space unless told otherwise
    // (§9.7.12). Neither field named keeps the suffix side, which is where a value's currency was
    // always drawn.
    const before = c.currencySuffix == null && c.currencyPrefix != null;
    const spaced = c.currencySpaced !== false;
    return (
      <Field fieldId={c.fieldId} label={c.label}>
        {(binding) => {
          const current = binding.client.value(c.fieldId);
          const amount = current?.type === "amount_value" ? (current as { long?: number }).long : undefined;
          // The currency lives in the value; the component's symbol is the fallback, and there is no
          // third place (§9.7).
          const fromField = c.currencyFromField ? binding.client.value(c.currencyFromField) : undefined;
          const carried =
            fromField?.type === "entity_value"
              ? (fromField as { rawMetadata?: Record<string, string> }).rawMetadata?.currency
              : undefined;
          const currency =
            (current?.type === "amount_value" ? (current as { currency?: string | null }).currency : null) ??
            carried ??
            c.currencySuffix ??
            c.currencyPrefix ??
            "";
          const symbol = (
            <span data-kompot="currency" data-side={before ? "before" : "after"}>
              {currency}
            </span>
          );
          return (
            <span style={{ display: "inline-flex", gap: spaced ? "4px" : 0, alignItems: "baseline" }}>
              {before && symbol}
              <input
                inputMode="numeric"
                value={amount == null ? "" : String(amount)}
                onBlur={() => binding.leave(c.fieldId)}
                onChange={(event) => {
                  const digits = event.target.value.replace(/[^\d]/g, "");
                  binding.change(
                    c.fieldId,
                    digits === "" ? undefined : ({ type: "amount_value", long: Number(digits), currency: currency || null } as FieldValue),
                  );
                }}
              />
              {!before && symbol}
            </span>
          );
        }}
      </Field>
    );
  },

  autocomplete_input: (raw) => {
    const c = raw as unknown as { fieldId: string; label: string; dataSourceId: string; placeholder?: string | null };
    return (
      <Field fieldId={c.fieldId} label={c.label}>
        {(binding) => <Autocomplete field={c} binding={binding} />}
      </Field>
    );
  },

  read_only_field: (raw) => <ReadOnlyField component={raw as unknown as ReadOnly} />,
};

interface ReadOnly {
  label: string;
  value: string;
  helperText?: string | null;
  fieldId?: string | null;
}

/**
 * Without `fieldId` the server's text, never declared and never sent (§9.2). With one it is an
 * ordinary bound field that cannot be typed into: it follows `visibleIf`, takes a patch, and goes out
 * with the submit — the place a server-computed total lives (§9.6.5). Bound only when the server said
 * so: reading the engine for an unbound one would turn "no value" into an empty box where the
 * server's own text used to be.
 */
function ReadOnlyField(props: { component: ReadOnly }): ReactNode {
  const { component } = props;
  const binding = useForm();
  const fieldId = component.fieldId ?? undefined;
  if (fieldId !== undefined && binding && !binding.client.visibleFields().includes(fieldId)) return null;
  const bound = fieldId !== undefined && binding ? plainValue(binding.client.value(fieldId)) : undefined;
  return (
    <div data-kompot="read-only-field" data-kompot-field={fieldId}>
      <span>{component.label}</span>
      <span>{bound ?? component.value}</span>
      {component.helperText != null && <span>{component.helperText}</span>}
    </div>
  );
}

/**
 * The string a value reads as — what the Kotlin values call `plainValue`, kept identical so that one
 * response reads the same on both clients: an entity's id rather than its title, an amount without its
 * currency (the server sends formatted text as a `text_value` when it wants one, §9.6).
 */
function plainValue(value: FieldValue | undefined): string | undefined {
  if (value === undefined) return undefined;
  const v = value as { type: string; text?: unknown; id?: unknown; long?: unknown; value?: unknown };
  switch (v.type) {
    case "text_value":
      return String(v.text ?? "");
    case "entity_value":
      return String(v.id ?? "");
    case "amount_value":
      return String(v.long ?? "");
    case "boolean_value":
      return String(v.value);
    default:
      return undefined;
  }
}
