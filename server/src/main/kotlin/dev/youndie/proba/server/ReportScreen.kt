package dev.youndie.proba.server

import dev.youndie.proba.checks.Finding
import dev.youndie.proba.checks.Severity
import dev.youndie.proba.reader.Coordinate
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.SizeType
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.OpenUrlAction
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.kompot.standard.TextComponent

/**
 * The report, as a tree of standard components and token names.
 *
 * Nothing here is a colour or a pixel of styling: the server may only name a token, and what those
 * names are worth is the client's business. That constraint is the reason the vocabulary is
 * generated from `design/tokens.json` — the two ends of it are in different languages and must not
 * be two lists.
 */
object ReportScreen {

    private const val PAGE_PADDING = 32
    private const val CARD_PADDING = 20
    private const val MARK_SIZE = 12
    private const val RING = 2

    fun of(coordinate: Coordinate, findings: List<Finding>, deep: Boolean): KompotComponent =
        ColumnComponent(
            id = "report",
            modifiers = listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(Color.Surface),
                KompotModifierNode.Padding(all = PAGE_PADDING),
            ),
            spacing = 16,
            children = buildList {
                add(header(coordinate, findings, deep))
                if (findings.isEmpty()) add(nothingToReport()) else findings.forEachIndexed { at, it -> add(card(at, it)) }
                add(actions(coordinate))
            },
        )

    /** A coordinate that could not be read gets a screen saying so, not an empty one. */
    fun refusal(coordinate: Coordinate, reason: String, detail: List<String>): KompotComponent =
        ColumnComponent(
            id = "refusal",
            modifiers = listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(Color.Surface),
                KompotModifierNode.Padding(all = PAGE_PADDING),
            ),
            spacing = 16,
            children = listOf(
                ColumnComponent(
                    id = "refusal-head",
                    spacing = 4,
                    children = listOf(
                        TextComponent(id = "refusal-title", text = coordinate.toString(), style = Type.TitlePage),
                        TextComponent(id = "refusal-reason", text = reason, style = Type.Body),
                    ),
                ),
                evidence("refusal", detail),
            ),
        )

    private fun header(coordinate: Coordinate, findings: List<Finding>, deep: Boolean): KompotComponent =
        ColumnComponent(
            id = "header",
            spacing = 4,
            children = listOf(
                TextComponent(id = "title", text = coordinate.toString(), style = Type.TitlePage),
                TextComponent(
                    id = "subtitle",
                    text = "${findings.size} finding(s) from ${if (deep) "both tiers" else "the repository alone"}",
                    style = Type.BodySmall,
                ),
            ),
        )

    private fun nothingToReport(): KompotComponent =
        ColumnComponent(
            id = "clean",
            modifiers = listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(Color.SurfaceRaised),
                KompotModifierNode.Padding(all = CARD_PADDING),
            ),
            children = listOf(TextComponent(id = "clean-text", text = "Nothing to report.", style = Type.Body)),
        )

    private fun card(index: Int, finding: Finding): KompotComponent {
        val id = "f$index"
        val look = look(finding.severity)
        return ColumnComponent(
            id = id,
            spacing = 8,
            modifiers = listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(Color.SurfaceRaised),
                KompotModifierNode.Padding(all = CARD_PADDING),
            ),
            children = listOf(
                RowComponent(
                    id = "$id-head",
                    spacing = 8,
                    children = listOf(badge(id, look), TextComponent(id = "$id-check", text = finding.checkId, style = Type.Code)),
                ),
                TextComponent(id = "$id-subject", text = finding.subject, style = Type.TitleSection),
                TextComponent(id = "$id-message", text = finding.message, style = Type.Body),
                evidence(id, finding.evidence),
            ),
        )
    }

    /**
     * The mark and the word travel together. Either alone is a colour with no name, or a name with no
     * weight — and colour on its own is unreadable to a reader who cannot tell red from ochre, or to
     * anyone reading this pasted into an issue as text.
     */
    private fun badge(id: String, look: SeverityLook): KompotComponent =
        RowComponent(
            id = "$id-badge",
            spacing = 6,
            modifiers = listOf(KompotModifierNode.Background(look.surface), KompotModifierNode.Padding(all = 6)),
            children = listOf(mark("$id-mark", look), TextComponent(id = "$id-word", text = look.word, style = Type.Label)),
        )

    /**
     * Drawn out of the modifiers the protocol has. There is no border node, so a hollow square is a
     * coloured box with padding and a second box inside it, which the ordered chain turns into a ring.
     */
    private fun mark(id: String, look: SeverityLook): KompotComponent {
        val box = KompotModifierNode.Size(widthDp = MARK_SIZE, heightDp = MARK_SIZE)
        return when (look.shape) {
            "solid" -> ColumnComponent(id = id, children = emptyList(), modifiers = listOf(box, KompotModifierNode.Background(look.color)))

            "half" -> ColumnComponent(
                id = id,
                children = emptyList(),
                modifiers = listOf(box, KompotModifierNode.Gradient(listOf(look.color, look.surface))),
            )

            else -> ColumnComponent(
                id = id,
                modifiers = listOf(box, KompotModifierNode.Background(look.color), KompotModifierNode.Padding(all = RING)),
                children = listOf(
                    ColumnComponent(
                        id = "$id-hole",
                        children = emptyList(),
                        modifiers = listOf(
                            KompotModifierNode.Size(width = SizeType.Fill, height = SizeType.Fill),
                            KompotModifierNode.Background(look.surface),
                        ),
                    ),
                ),
            )
        }
    }

    private fun evidence(id: String, lines: List<String>): KompotComponent =
        ColumnComponent(
            id = "$id-evidence",
            spacing = 4,
            modifiers = listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(Color.SurfaceSunken),
                KompotModifierNode.Padding(all = 12),
            ),
            children = lines.mapIndexed { at, line ->
                TextComponent(id = "$id-e$at", text = line, style = Type.CodeSmall)
            },
        )

    private fun actions(coordinate: Coordinate): KompotComponent =
        RowComponent(
            id = "actions",
            spacing = 8,
            children = listOf(
                ButtonComponent(
                    id = "spec",
                    text = "What this checks",
                    action = OpenUrlAction("https://github.com/youndie/proba"),
                ),
                ButtonComponent(
                    id = "artefact",
                    text = "The artefact",
                    variant = "text",
                    action = OpenUrlAction("https://central.sonatype.com/artifact/${coordinate.group}/${coordinate.artifact}"),
                ),
            ),
        )

    private fun look(severity: Severity): SeverityLook = when (severity) {
        Severity.Defect -> SeverityLook.Defect
        Severity.Suspicion -> SeverityLook.Suspicion
        Severity.Undetermined -> SeverityLook.Undetermined
    }
}
