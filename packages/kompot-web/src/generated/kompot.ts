/* eslint-disable */
// Generated from kompot-core.schema.json, kompot-standard.schema.json in kompot-spec 0.27.0.46.
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
