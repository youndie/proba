import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { KompotForm, KompotProvider, useForm } from "../src";
import type { AnyComponent } from "../src";
import type { FormSchema } from "../src/generated/kompot";

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
});

function Capture(props: { onRender: (value: unknown) => void }) {
  const binding = useForm();
  props.onRender(binding?.client.value("account"));
  return null;
}
