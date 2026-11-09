import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { createFormClient } from "../src/forms/controller";
import { applyUpdateAction, findNode, type UpdateAction, type UpdateAddress } from "../src/realtime/updates";
import type { AnyComponent } from "../src/types";
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
  holds?: string[];
  title: string;
  why?: string;
  form?: FormSchema;
  screen?: AnyComponent;
  steps?: Array<Record<string, unknown>>;
  expect: Record<string, unknown>;
}

const index = JSON.parse(readFileSync(join(corpus, "index.json"), "utf8")) as { schema?: string; cases: string[] };
const cases = index.cases.map((name) => JSON.parse(readFileSync(join(corpus, name), "utf8")) as Case);

// The keys a case may carry, read from the format that travels with the corpus rather than typed here.
// SPEC.md §17: a runner stops on a key it does not know, because in a corpus an unfamiliar key is a
// rule nobody checked — the opposite of the wire, where it is a newer sender.
const format = JSON.parse(readFileSync(join(corpus, index.schema ?? "client-corpus.schema.json"), "utf8")) as {
  $defs: Record<string, { properties?: Record<string, unknown> }>;
};
const caseKeys = Object.keys(format.$defs.ClientCase?.properties ?? {});

describe("the client conformance corpus", () => {
  it("reads the format the cases are written in", () => {
    // Without it every case would pass the key check below by having nothing to compare against.
    expect(caseKeys).toContain("expect");
  });

  it("has cases to run", () => {
    // A corpus that failed to arrive passes every case it contains. Nothing else here would say so.
    expect(cases.length).toBeGreaterThan(0);
    expect(cases.length).toBe(index.cases.length);
  });

  for (const item of cases) {
    it(`${item.clause} ${item.title}`, () => {
      for (const key of Object.keys(item)) {
        if (!caseKeys.includes(key)) throw new Error(`the runner does not know the case key "${key}" (case ${item.id})`);
      }
      // A case is about a form or about a screen (§16.4 answers), and a form expectation on a case
      // without a form is the runner misreading it, not a pass.
      const formClient = item.form ? createFormClient(item.form) : undefined;
      const form = () => {
        if (!formClient) throw new Error(`case ${item.id} has no form for a form step or expectation`);
        return formClient;
      };
      let screen = item.screen;
      const addresses: UpdateAddress[] = [];
      let submission: ReturnType<ReturnType<typeof createFormClient>["submit"]> | undefined;

      for (const step of item.steps ?? []) {
        switch (step.step) {
          case "answer": {
            // The answer of a perform or a load, run as an action. `update` is the only one with a
            // clause in the corpus; another type here would be one this runner has not been held to.
            const action = step.action as { type?: unknown };
            if (action.type !== "update") throw new Error(`the runner does not know the answer "${String(action.type)}" (case ${item.id})`);
            if (!screen) throw new Error(`case ${item.id} answers an update without a screen`);
            const applied = applyUpdateAction(screen, action as UpdateAction);
            screen = applied.tree;
            if (applied.address) addresses.push(applied.address);
            break;
          }
          case "set":
            form().setValue(String(step.fieldId), step.value as FieldValue);
            break;
          case "blur":
            form().blur(String(step.fieldId));
            break;
          case "patch":
            form().applyPatch(step.patch as FormPatch);
            break;
          case "submit":
            submission = form().submit();
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
            expect([...form().visibleFields()].sort()).toEqual([...(wanted as string[])].sort());
            asserted += 1;
            break;

          case "noErrors": {
            // A list of the fields that must be clean, not a flag over the whole form.
            const clean = wanted as string[];
            expect(Array.isArray(clean), `noErrors is a list of field ids, got ${JSON.stringify(wanted)}`).toBe(true);
            const errors = form().errors();
            for (const fieldId of clean) expect(errors[fieldId], `${fieldId} must have no error`).toBeUndefined();
            asserted += 1;
            break;
          }

          case "errors":
            expect(form().errors()).toEqual(wanted);
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

          case "requests":
            // What the client sent, in the corpus's own shape: the kind, the field that changed, and
            // the form as it was at that moment (§9.6). An empty list is an assertion too — that a
            // field without triggersPatch sent nothing.
            expect(
              form().requests().map((request) => ({ kind: "patch", fieldId: request.fieldId, values: request.values })),
            ).toEqual(wanted);
            asserted += 1;
            break;

          case "nodes":
            // Each named node is on the screen and says at least this. The rest of the node — its
            // id, fields the case does not mention — is not what the clause is about.
            for (const [componentId, node] of Object.entries(wanted as Record<string, Record<string, unknown>>)) {
              const found = screen && findNode(screen, componentId);
              expect(found, `${componentId} must be on the screen`).toBeDefined();
              expect(found).toMatchObject(node);
            }
            asserted += 1;
            break;

          case "absent":
            for (const componentId of wanted as string[]) {
              expect(screen && findNode(screen, componentId), `${componentId} must not be on the screen`).toBeUndefined();
            }
            asserted += 1;
            break;

          case "addresses":
            // An empty list is an assertion too: an update without a deeplink hands over no address.
            expect(addresses).toEqual(wanted);
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
