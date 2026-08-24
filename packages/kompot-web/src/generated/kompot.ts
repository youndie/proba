/* eslint-disable */
// Generated from kompot-core.schema.json, kompot-standard.schema.json, form-core.schema.json, form-standard.schema.json, kompot-forms.schema.json in kompot-spec 0.27.1.50.
// Do not edit. Regenerate with: pnpm schema

/**
 * An intent the server sends the client in response to an interaction. The hierarchy is OPEN on the same terms as KompotComponent.
 */
export interface KompotAction {
  /**
   * The variant discriminator. The closed list of values is in the build's profile
   */
  type: string;
  [k: string]: unknown;
}
/**
 * A node of the screen tree. The hierarchy is OPEN: a server may send a type the client does not know, and the client must degrade to a placeholder rather than fail (see SPEC.md, "Unknown types").
 */
export interface KompotComponent {
  /**
   * The variant discriminator. The closed list of values is in the build's profile
   */
  type: string;
  /**
   * Unique within one screen tree: point updates are addressed by it (see UpdateComponentMessage in :kompot-realtime)
   */
  id: string;
  /**
   * The order of nodes matters — they are applied left to right
   */
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  [k: string]: unknown;
}
export interface KompotModifierNodeBackground {
  type: "background";
  /**
   * An open design-system string key (io.github.youndie.kompot.ColorToken)
   */
  color: string;
  [k: string]: unknown;
}
export interface KompotModifierNodeGradient {
  type: "gradient";
  colors: string[];
  [k: string]: unknown;
}
export interface KompotModifierNodePadding {
  type: "padding";
  all?: number | null;
  top?: number | null;
  bottom?: number | null;
  start?: number | null;
  end?: number | null;
  [k: string]: unknown;
}
export interface KompotModifierNodeSize {
  type: "size";
  width?: ("Fill" | "Wrap") | null;
  height?: ("Fill" | "Wrap") | null;
  widthDp?: number | null;
  heightDp?: number | null;
  [k: string]: unknown;
}
export interface KompotModifierNodeWeight {
  type: "weight";
  value: number;
  [k: string]: unknown;
}
export interface KompotActionClose {
  type: "close";
  [k: string]: unknown;
}
export interface KompotActionCopyText {
  type: "copy_text";
  text: string;
  [k: string]: unknown;
}
export interface KompotActionLoadPage {
  type: "load_page";
  /**
   * The relative address of the next page
   */
  url: string;
  [k: string]: unknown;
}
export interface KompotActionNavigate {
  type: "navigate";
  /**
   * The identifier of an application screen: a URI with its own scheme, not a web address. Both the scheme and the set of values are the application's (see NavigationGraph in :kompot-navigation); a client must ignore an unknown deeplink
   */
  deeplink: string;
  [k: string]: unknown;
}
export interface KompotActionOpenUrl {
  type: "open_url";
  /**
   * An address OUTSIDE the application. navigate cannot carry one and must not (§12.2); this action exists so that leaving is explicit, and a client may put a confirmation or an allowlist in front of it
   */
  url: string;
  [k: string]: unknown;
}
export interface KompotComponentButton {
  type: "button";
  id: string;
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  text: string;
  action: KompotAction;
  variant?: string | null;
  [k: string]: unknown;
}
export interface KompotComponentColumn {
  type: "column";
  id: string;
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  children: KompotComponent[];
  spacing?: number;
  action?: KompotAction | null;
  [k: string]: unknown;
}
export interface KompotComponentPaginatedList {
  type: "paginated_list";
  id: string;
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  initialItems: KompotComponent[];
  loadMoreAction?: LoadPage | null;
  /**
   * The relative address of the first page; the form's field values go there as query parameters
   */
  reloadUrl?: string | null;
  emptyState?: KompotComponent | null;
  [k: string]: unknown;
}
export interface LoadPage {
  /**
   * The relative address of the next page
   */
  url: string;
  [k: string]: unknown;
}
export interface KompotComponentRow {
  type: "row";
  id: string;
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  children: KompotComponent[];
  spacing?: number;
  action?: KompotAction | null;
  [k: string]: unknown;
}
export interface KompotComponentTable {
  type: "table";
  id: string;
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  rows: TableRow[];
  [k: string]: unknown;
}
export interface TableRow {
  cells: string[];
  header?: boolean;
  [k: string]: unknown;
}
export interface KompotComponentText {
  type: "text";
  id: string;
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  text: string;
  style?: string | null;
  spans?: TextSpan[];
  maxLines?: number | null;
  ellipsis?: boolean;
  [k: string]: unknown;
}
export interface TextSpan {
  text: string;
  style?: string | null;
  action?: KompotAction | null;
  [k: string]: unknown;
}
export interface KompotPageResponse {
  items: KompotComponent[];
  nextLoadAction?: LoadPage | null;
  [k: string]: unknown;
}
/**
 * The value of a form field. It travels both ways: server -> client in a patch, client -> server on submit. No runtime fallback.
 */
export interface FieldValue {
  /**
   * The variant discriminator. The closed list of values is in the build's profile
   */
  type: string;
  [k: string]: unknown;
}
/**
 * A field's visibility condition (visibleIf). No runtime fallback.
 */
export interface FormCondition {
  /**
   * The variant discriminator. The closed list of values is in the build's profile
   */
  type: string;
  [k: string]: unknown;
}
/**
 * The definition of a form field: the data contract, not its presentation. The hierarchy is extended by plug-ins but has NO runtime fallback: an unknown type breaks the parse of the whole form schema.
 */
export interface FormFieldDefinition {
  /**
   * The variant discriminator. The closed list of values is in the build's profile
   */
  type: string;
  /**
   * Unique within a FormSchema; ties the definition to the UI component that refers to it
   */
  fieldId: string;
  rules?: ValidationRule[];
  /**
   * Evaluated by the client locally, with no round trip to the server
   */
  visibleIf?: FormCondition | null;
  /**
   * Changing the value requires asking the server for a patch
   */
  triggersPatch?: boolean;
  [k: string]: unknown;
}
/**
 * A client-side validation rule for a field. No runtime fallback (see FormFieldDefinition).
 */
export interface ValidationRule {
  /**
   * The variant discriminator. The closed list of values is in the build's profile
   */
  type: string;
  /**
   * Ready localised error text, not a translation key
   */
  errorMessage: string;
  [k: string]: unknown;
}
export interface FormPatch {
  updates?: {
    [k: string]: FieldValue;
  };
  clearFields?: string[];
  focusOn?: string | null;
  [k: string]: unknown;
}
export interface FormSchema {
  formId: string;
  fields: FormFieldDefinition[];
  [k: string]: unknown;
}
export interface FieldValueAmountValue {
  type: "amount_value";
  long: number;
  currency?: string | null;
  [k: string]: unknown;
}
export interface FieldValueBooleanValue {
  type: "boolean_value";
  value: boolean;
  [k: string]: unknown;
}
export interface FieldValueEntityValue {
  type: "entity_value";
  id: string;
  title: string;
  /**
   * Arbitrary metadata available to the client locally. Two keys are reserved by the protocol: "currency" is the currency amount_input.currencyFromField picks up, "balance" is the remaining amount max_amount_from_field reads. Every other key is a convention of the particular form
   */
  rawMetadata?: {
    [k: string]: string;
  } | null;
  [k: string]: unknown;
}
export interface FieldValueTextValue {
  type: "text_value";
  text: string;
  [k: string]: unknown;
}
export interface FormConditionEquals {
  type: "equals";
  fieldId: string;
  expectedValue: FieldValue;
  [k: string]: unknown;
}
export interface FormConditionNotEquals {
  type: "not_equals";
  fieldId: string;
  expectedValue: FieldValue;
  [k: string]: unknown;
}
export interface FormFieldDefinitionAmountField {
  type: "amount_field";
  fieldId: string;
  rules: ValidationRule[];
  visibleIf?: FormCondition | null;
  triggersPatch?: boolean;
  initialValue?: FieldValue | null;
  [k: string]: unknown;
}
export interface FormFieldDefinitionAutocompleteField {
  type: "autocomplete_field";
  fieldId: string;
  rules?: ValidationRule[];
  dataSourceId: string;
  visibleIf?: FormCondition | null;
  triggersPatch?: boolean;
  initialValue?: FieldValue | null;
  [k: string]: unknown;
}
export interface FormFieldDefinitionCheckboxField {
  type: "checkbox_field";
  fieldId: string;
  rules?: ValidationRule[];
  visibleIf?: FormCondition | null;
  triggersPatch?: boolean;
  initialValue?: FieldValue | null;
  [k: string]: unknown;
}
export interface FormFieldDefinitionSelectionField {
  type: "selection_field";
  fieldId: string;
  rules?: ValidationRule[];
  visibleIf?: FormCondition | null;
  triggersPatch?: boolean;
  initialValue?: FieldValue | null;
  [k: string]: unknown;
}
export interface FormFieldDefinitionTextField {
  type: "text_field";
  fieldId: string;
  rules: ValidationRule[];
  keyboardType?: "TEXT" | "NUMBER" | "EMAIL" | "PHONE";
  mask?: string | null;
  visibleIf?: FormCondition | null;
  triggersPatch?: boolean;
  initialValue?: FieldValue | null;
  [k: string]: unknown;
}
export interface ValidationRuleMaxAmountFromField {
  type: "max_amount_from_field";
  balanceFieldId: string;
  /**
   * The key in the chosen entity_value's rawMetadata the remaining amount is read from. Defaults to "balance"
   */
  balanceMetadataKey?: string;
  errorMessage: string;
  [k: string]: unknown;
}
export interface ValidationRuleRegex {
  type: "regex";
  pattern: string;
  errorMessage: string;
  [k: string]: unknown;
}
export interface ValidationRuleRequired {
  type: "required";
  errorMessage: string;
  [k: string]: unknown;
}
export interface ValidationRuleRequiredIf {
  type: "required_if";
  targetFieldId: string;
  expectedValue: FieldValue;
  errorMessage: string;
  [k: string]: unknown;
}
export interface FormPatchRequest {
  formId: string;
  fieldId: string;
  values: {
    [k: string]: FieldValue;
  };
  [k: string]: unknown;
}
export interface KompotActionSubmitForm {
  type: "submit_form";
  formId: string;
  [k: string]: unknown;
}
export interface KompotComponentAmountInput {
  type: "amount_input";
  id: string;
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  fieldId: string;
  label: string;
  currencySuffix?: string | null;
  currencyFromField?: string | null;
  [k: string]: unknown;
}
export interface KompotComponentAutocompleteInput {
  type: "autocomplete_input";
  id: string;
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  fieldId: string;
  label: string;
  dataSourceId: string;
  placeholder?: string | null;
  [k: string]: unknown;
}
export interface KompotComponentCheckboxInput {
  type: "checkbox_input";
  id: string;
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  fieldId: string;
  label: string;
  [k: string]: unknown;
}
export interface KompotComponentRadioGroup {
  type: "radio_group";
  id: string;
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  fieldId: string;
  label: string;
  options: SelectOption[];
  [k: string]: unknown;
}
export interface SelectOption {
  id: string;
  label: string;
  /**
   * Arbitrary metadata available to the client locally. Two keys are reserved by the protocol: "currency" is the currency amount_input.currencyFromField picks up, "balance" is the remaining amount max_amount_from_field reads. Every other key is a convention of the particular form
   */
  rawMetadata?: {
    [k: string]: string;
  } | null;
  [k: string]: unknown;
}
export interface KompotComponentReadOnlyField {
  type: "read_only_field";
  id: string;
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  label: string;
  value: string;
  helperText?: string | null;
  [k: string]: unknown;
}
export interface KompotComponentSelectInput {
  type: "select_input";
  id: string;
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  fieldId: string;
  label: string;
  options: SelectOption[];
  placeholder?: string | null;
  [k: string]: unknown;
}
export interface KompotComponentTextInput {
  type: "text_input";
  id: string;
  modifiers?: (
    | KompotModifierNodeBackground
    | KompotModifierNodeGradient
    | KompotModifierNodePadding
    | KompotModifierNodeSize
    | KompotModifierNodeWeight
  )[];
  fieldId: string;
  label: string;
  placeholder?: string | null;
  mask?: string | null;
  uppercase?: boolean;
  multiline?: boolean;
  secret?: boolean;
  [k: string]: unknown;
}
export interface KompotFormResponse {
  schema: FormSchema;
  screen: KompotComponent;
  /**
   * The live-update topic of this screen. The string is opaque to the client; a server must make it per-subject wherever the data is personal (see SPEC.md §10.4)
   */
  realtimeTopic?: string | null;
  [k: string]: unknown;
}
