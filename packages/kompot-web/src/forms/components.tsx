import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import { KompotNode, useKompot } from "../render";
import type { AnyComponent } from "../types";
import type { FieldValue, FormSchema, SelectOption } from "../generated/kompot";
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

const FormContext = createContext<FormBinding | null>(null);

export function useForm(): FormBinding | null {
  return useContext(FormContext);
}

export function KompotForm(props: {
  schema: FormSchema;
  screen: AnyComponent;
  draft?: Record<string, FieldValue>;
  suggest?: Suggest;
  onSubmit?: (payload: Record<string, FieldValue>) => void;
  children?: ReactNode;
}): ReactNode {
  const client = useMemo(() => createFormClient(props.schema, props.draft ?? {}), [props.schema, props.draft]);
  const [revision, setRevision] = useState(0);

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
    const c = raw as unknown as { fieldId: string; label: string; currencySuffix?: string | null; currencyFromField?: string | null };
    return (
      <Field fieldId={c.fieldId} label={c.label}>
        {(binding) => {
          const current = binding.client.value(c.fieldId);
          const amount = current?.type === "amount_value" ? (current as { long?: number }).long : undefined;
          // The currency lives in the value; the component's suffix is the fallback, and there is no
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
            "";
          return (
            <span style={{ display: "inline-flex", gap: "4px", alignItems: "baseline" }}>
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
              <span data-kompot="currency">{currency}</span>
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

  read_only_field: (raw) => {
    // No fieldId, not declared in the schema, and never part of a payload (§9.2).
    const c = raw as unknown as { label: string; value: string; helperText?: string | null };
    return (
      <div data-kompot="read-only-field">
        <span>{c.label}</span>
        <span>{c.value}</span>
        {c.helperText != null && <span>{c.helperText}</span>}
      </div>
    );
  },
};
