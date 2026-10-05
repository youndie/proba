import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { KompotForm, KompotProvider, useForm } from "../src";
import type { AnyComponent } from "../src";
import type { FormPatchRequest, FormSchema } from "../src/generated/kompot";

/**
 * The engine is held by somebody else's corpus; these hold the wiring between it and the DOM — that a
 * hidden field draws nothing, that an error appears only once the field has been left, and that a
 * choice becomes an entity rather than a string.
 */
const schema: FormSchema = {
  formId: "f",
  fields: [
    { type: "text_field", fieldId: "code", rules: [{ type: "regex", pattern: "^[A-Z]{3}$", errorMessage: "Three capitals" }] },
    {
      type: "text_field",
      fieldId: "reason",
      rules: [],
      visibleIf: { type: "equals", fieldId: "code", expectedValue: { type: "text_value", text: "ABC" } },
    },
  ],
} as unknown as FormSchema;

const screenTree = {
  type: "column",
  id: "form",
  children: [
    { type: "text_input", id: "i1", fieldId: "code", label: "Code" },
    { type: "text_input", id: "i2", fieldId: "reason", label: "Reason" },
  ],
} as unknown as AnyComponent;

const draw = () =>
  render(
    <KompotProvider>
      <KompotForm schema={schema} screen={screenTree} />
    </KompotProvider>,
  );

describe("a form on screen", () => {
  it("draws nothing for a field whose condition does not hold", () => {
    const { container } = draw();

    expect(container.querySelector('[data-kompot-field="code"]')).not.toBeNull();
    expect(container.querySelector('[data-kompot-field="reason"]')).toBeNull();
  });

  it("brings the field back when the condition starts holding", () => {
    const { container } = draw();

    fireEvent.change(container.querySelector("input")!, { target: { value: "ABC" } });

    expect(container.querySelector('[data-kompot-field="reason"]')).not.toBeNull();
  });

  it("says nothing while the field is still being typed into", () => {
    const { container } = draw();

    fireEvent.change(container.querySelector("input")!, { target: { value: "a" } });

    expect(container.querySelector('[data-kompot-error="code"]')).toBeNull();
  });

  it("shows the message the rule carries once the field is left", () => {
    const { container } = draw();
    const input = container.querySelector("input")!;

    fireEvent.change(input, { target: { value: "a" } });
    fireEvent.blur(input);

    expect(screen.getByText("Three capitals")).toBeDefined();
  });

  it("resolves a choice to an entity, keeping the metadata a rule reads locally", () => {
    const selectSchema = {
      formId: "f",
      fields: [{ type: "selection_field", fieldId: "account", rules: [] }],
    } as unknown as FormSchema;
    const tree = {
      type: "select_input",
      id: "s",
      fieldId: "account",
      label: "Account",
      options: [{ id: "acc-1", label: "Current", rawMetadata: { currency: "EUR", balance: "1200" } }],
    } as unknown as AnyComponent;

    let captured: unknown;
    const { container } = render(
      <KompotProvider>
        <KompotForm schema={selectSchema} screen={tree}>
          <Capture onRender={(value) => (captured = value)} />
        </KompotForm>
      </KompotProvider>,
    );

    fireEvent.change(container.querySelector("select")!, { target: { value: "acc-1" } });

    expect(captured).toEqual({
      type: "entity_value",
      id: "acc-1",
      title: "Current",
      rawMetadata: { currency: "EUR", balance: "1200" },
    });
  });

  it("sends a patch for a field that triggers one and draws what comes back", async () => {
    const patched = {
      formId: "p",
      fields: [
        { type: "text_field", fieldId: "code", rules: [], triggersPatch: true },
        { type: "text_field", fieldId: "total", rules: [] },
      ],
    } as unknown as FormSchema;
    const tree = {
      type: "column",
      id: "form",
      children: [
        { type: "text_input", id: "i1", fieldId: "code", label: "Code" },
        { type: "text_input", id: "i2", fieldId: "total", label: "Total" },
      ],
    } as unknown as AnyComponent;
    const sent: FormPatchRequest[] = [];
    const { container } = render(
      <KompotProvider>
        <KompotForm
          schema={patched}
          screen={tree}
          requestPatch={async (request) => {
            sent.push(request);
            return { updates: { total: { type: "text_value", text: "42" } } };
          }}
        />
      </KompotProvider>,
    );

    fireEvent.change(container.querySelectorAll("input")[0]!, { target: { value: "ABC" } });

    expect(sent).toEqual([{ formId: "p", fieldId: "code", values: { code: { type: "text_value", text: "ABC" } } }]);
    await waitFor(() => expect((container.querySelectorAll("input")[1] as HTMLInputElement).value).toBe("42"));
  });

  describe("an amount's currency (§9.7.10–12)", () => {
    const amountSchema = {
      formId: "a",
      fields: [{ type: "amount_field", fieldId: "sum", rules: [] }],
    } as unknown as FormSchema;
    const field = (extra: Record<string, unknown>, draft?: Record<string, unknown>) => {
      const { container } = render(
        <KompotProvider>
          <KompotForm
            schema={amountSchema}
            draft={draft as never}
            screen={{ type: "amount_input", id: "i", fieldId: "sum", label: "Sum", ...extra } as unknown as AnyComponent}
          />
        </KompotProvider>,
      );
      const wrapper = container.querySelector("input")!.parentElement!;
      const symbol = wrapper.querySelector<HTMLElement>('[data-kompot="currency"]')!;
      const before = wrapper.firstElementChild === symbol;
      return { wrapper, symbol, before };
    };

    it("puts a prefix in front and a suffix behind", () => {
      expect(field({ currencyPrefix: "$" }).before).toBe(true);
      expect(field({ currencySuffix: "€" }).before).toBe(false);
    });

    it("draws a currency from the value on the side the component named", () => {
      const drawn = field({ currencyPrefix: "$" }, { sum: { type: "amount_value", long: 5, currency: "¥" } });
      expect(drawn.symbol.textContent).toBe("¥");
      expect(drawn.before).toBe(true);
    });

    it("draws the suffix when a server named both, as an older client would", () => {
      const drawn = field({ currencyPrefix: "$", currencySuffix: "€" });
      expect(drawn.symbol.textContent).toBe("€");
      expect(drawn.before).toBe(false);
    });

    it("closes the gap only when told to", () => {
      expect(field({ currencyPrefix: "$" }).wrapper.style.gap).toBe("4px");
      expect(field({ currencyPrefix: "$", currencySpaced: false }).wrapper.style.gap).toBe("0");
    });
  });

  it("binds a read-only field that names a fieldId, and leaves one that does not alone (§9.2)", async () => {
    const bound = {
      formId: "r",
      fields: [
        { type: "text_field", fieldId: "code", rules: [], triggersPatch: true },
        { type: "text_field", fieldId: "total", rules: [] },
      ],
    } as unknown as FormSchema;
    const tree = {
      type: "column",
      id: "form",
      children: [
        { type: "text_input", id: "i1", fieldId: "code", label: "Code" },
        { type: "read_only_field", id: "t", fieldId: "total", label: "Total", value: "—" },
        { type: "read_only_field", id: "n", label: "Note", value: "server text" },
      ],
    } as unknown as AnyComponent;
    const { container } = render(
      <KompotProvider>
        <KompotForm
          schema={bound}
          screen={tree}
          requestPatch={async () => ({ updates: { total: { type: "text_value", text: "€42" } } })}
        />
      </KompotProvider>,
    );
    const shown = () => Array.from(container.querySelectorAll('[data-kompot="read-only-field"]')).map((it) => it.textContent);

    expect(shown()).toEqual(["Total—", "Noteserver text"]);
    fireEvent.change(container.querySelector("input")!, { target: { value: "A" } });
    await waitFor(() => expect(shown()).toEqual(["Total€42", "Noteserver text"]));
  });
});

function Capture(props: { onRender: (value: unknown) => void }) {
  const binding = useForm();
  props.onRender(binding?.client.value("account"));
  return null;
}
