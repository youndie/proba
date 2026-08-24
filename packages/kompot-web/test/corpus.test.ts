import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { createFormClient } from "../src/forms/controller";
import type { FieldValue, FormPatch, FormSchema } from "../src/generated/kompot";

/**
 * The conformance corpus of SPEC §9, run against this implementation.
 *
 * The cases are written by the other side and fetched from the published kompot-client-tck artefact,
 * not restated here. That is the whole value of them: a case I wrote would agree with whatever I
 * believed while writing the engine, and these can disagree.
 */
const corpus = join(dirname(fileURLToPath(import.meta.url)), "..", "corpus");

interface Case {
  id: string;
  clause: string;
  title: string;
  why?: string;
  form: FormSchema;
  initialValues?: Record<string, FieldValue>;
  steps: Array<Record<string, unknown>>;
  expect: Record<string, unknown>;
}

const index = JSON.parse(readFileSync(join(corpus, "index.json"), "utf8")) as { cases: string[] };
const cases = index.cases.map((name) => JSON.parse(readFileSync(join(corpus, name), "utf8")) as Case);

describe("the client conformance corpus", () => {
  it("has cases to run", () => {
    // A corpus that failed to arrive passes every case it contains. Nothing else here would say so.
    expect(cases.length).toBeGreaterThan(0);
    expect(cases.length).toBe(index.cases.length);
  });

  for (const item of cases) {
    it(`${item.clause} ${item.title}`, () => {
      const client = createFormClient(item.form, item.initialValues ?? {});
      let submission: ReturnType<typeof client.submit> | undefined;

      for (const step of item.steps) {
        switch (step.step) {
          case "set":
            client.setValue(String(step.fieldId), step.value as FieldValue);
            break;
          case "blur":
            client.blur(String(step.fieldId));
            break;
          case "patch":
            client.applyPatch(step.patch as FormPatch);
            break;
          case "submit":
            submission = client.submit();
            break;
          default:
            // Skipping it would let a clause this engine has never been held to pass quietly.
            throw new Error(`the runner does not know the step "${String(step.step)}" (case ${item.id})`);
        }
      }

      // Counted, because the first version of this runner read `noErrors` as a boolean while the
      // corpus writes it as a list of field ids. The condition was simply never true, the case
      // asserted nothing at all, and it reported itself green. Only a mutation — showing errors
      // before a field is left, which that case exists to forbid — revealed that it was not running.
      let asserted = 0;

      for (const [key, wanted] of Object.entries(item.expect)) {
        switch (key) {
          case "visibleFields":
            expect([...client.visibleFields()].sort()).toEqual([...(wanted as string[])].sort());
            asserted += 1;
            break;

          case "noErrors": {
            // A list of the fields that must be clean, not a flag over the whole form.
            const clean = wanted as string[];
            expect(Array.isArray(clean), `noErrors is a list of field ids, got ${JSON.stringify(wanted)}`).toBe(true);
            const errors = client.errors();
            for (const fieldId of clean) expect(errors[fieldId], `${fieldId} must have no error`).toBeUndefined();
            asserted += 1;
            break;
          }

          case "errors":
            expect(client.errors()).toEqual(wanted);
            asserted += 1;
            break;

          case "payloadBlocked":
            expect(submission, `${item.id} expects a submit step`).toBeDefined();
            expect(submission!.blocked).toBe(wanted);
            asserted += 1;
            break;

          case "payload":
            expect(submission, `${item.id} expects a submit step`).toBeDefined();
            expect(submission!.payload).toEqual(wanted);
            expect(submission!.blocked).toBe(false);
            asserted += 1;
            break;

          default:
            // An expectation this runner does not know is a clause it is not being held to, and
            // ignoring it looks exactly like passing it.
            throw new Error(`the runner does not know the expectation "${key}" (case ${item.id})`);
        }
      }

      expect(asserted, `${item.id} asserted nothing`).toBeGreaterThan(0);
    });
  }
});
