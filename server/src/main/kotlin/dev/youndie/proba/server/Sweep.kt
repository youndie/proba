package dev.youndie.proba.server

import dev.youndie.proba.checks.CheckContext
import dev.youndie.proba.checks.Checks
import dev.youndie.proba.checks.Finding
import dev.youndie.proba.checks.Severity
import dev.youndie.proba.reader.Coordinate
import dev.youndie.proba.reader.MavenRepository
import dev.youndie.proba.reader.Publication
import dev.youndie.proba.reader.PublicationReader
import dev.youndie.proba.reader.ReadOutcome
import dev.youndie.proba.reader.RepositoryIndex
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.SizeType
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.realtime.server.KompotUpdateBroadcaster
import io.github.youndie.kompot.realtime.server.broadcast
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * A run across every module of one group.
 *
 * One coordinate answers inside a request; a whole repository does not, which is the only reason the
 * live-update channel exists here. A screen that appears when the last module is done is a screen
 * nobody watches.
 */
class Sweep(
    val id: String,
    val group: String,
    val repository: MavenRepository,
    val modules: List<String>,
) {
    private val results = LinkedHashMap<String, ModuleResult>()
    private val mutex = Mutex()

    /**
     * Frames handed to a subscriber of this topic, counted at the moment of sending.
     *
     * The number exists so that a channel nobody is listening to is a fact rather than a silence. A
     * broadcaster with no subscribers succeeds at everything it is asked to do, and the screen simply
     * never changes — which is indistinguishable from a run that found nothing to say.
     */
    var framesDelivered: Int = 0
        private set

    val topic: String get() = "sweep:$id"

    suspend fun snapshot(): Map<String, ModuleResult> = mutex.withLock { LinkedHashMap(results) }

    suspend fun record(module: String, result: ModuleResult) = mutex.withLock { results[module] = result }

    suspend fun countDelivery(subscribers: Int) = mutex.withLock { framesDelivered += subscribers }
}

sealed interface ModuleResult {
    data object Pending : ModuleResult
    data class Checked(val coordinate: Coordinate, val findings: List<Finding>) : ModuleResult
    data class Refused(val module: String, val reason: String) : ModuleResult

    /**
     * A per-target coordinate of another module, not a library of its own.
     *
     * A multiplatform publication puts `kompot-core-jvm`, `kompot-core-iosarm64` and the rest beside
     * `kompot-core`, and a group listing cannot tell them apart by name without guessing. Its own
     * document can: a target module's `component` block names the coordinate that owns it and carries
     * a url back to it. Checking it separately would report the same publication a dozen times.
     */
    data class PartOf(val owner: Coordinate) : ModuleResult
}

private const val CONCURRENCY = 8

class SweepRunner(
    private val reader: PublicationReader,
    private val broadcaster: KompotUpdateBroadcaster,
    private val json: Json,
) {
    private val sweeps = mutableMapOf<String, Sweep>()
    private val mutex = Mutex()

    suspend fun get(id: String): Sweep? = mutex.withLock { sweeps[id] }

    /**
     * Null when the repository cannot be enumerated at all — an answer, not an empty group.
     *
     * An existing sweep is returned rather than restarted unless [fresh] is asked for. The channel
     * has no replay and must not have one, so a client that arrives after a module finished can only
     * learn about it from the screen it is handed; restarting on every fetch would mean the screen
     * always says "waiting" for whatever completed before the reader got there.
     */
    suspend fun start(
        scope: CoroutineScope,
        group: String,
        repository: MavenRepository,
        index: RepositoryIndex?,
        fresh: Boolean = false,
    ): Sweep? {
        if (index == null) return null
        if (!fresh) mutex.withLock { sweeps.values.firstOrNull { it.group == group } }?.let { return it }
        val modules = index.modules(group).sorted()
        // The group and nothing else: an address that changed when the group gained a module would not be
        // an address.
        val sweep = Sweep(id = group.replace('.', '_'), group = group, repository = repository, modules = modules)
        modules.forEach { sweep.record(it, ModuleResult.Pending) }
        mutex.withLock { sweeps[sweep.id] = sweep }

        // Concurrent and bounded. A group is hundreds of coordinates; done one at a time the run
        // outlives anybody's patience, and done all at once it is a burst of hundreds of requests at
        // somebody else's repository.
        val gate = Semaphore(CONCURRENCY)
        scope.launch {
            modules.mapIndexed { position, module ->
                async {
                    val result = gate.withPermit { check(group, module, repository, index) }
                    sweep.record(module, result)
                    push(sweep, SweepScreen.moduleId(position), SweepScreen.moduleCard(position, module, result))
                    push(sweep, SweepScreen.STATUS_ID, SweepScreen.status(sweep.snapshot()))
                }
            }.awaitAll()
        }
        return sweep
    }

    private suspend fun push(sweep: Sweep, componentId: String, component: KompotComponent) {
        // Counted before the send and from this instance's own subscriber list: what matters for the
        // gate is whether anything was actually on the other end, not whether the call returned.
        sweep.countDelivery(broadcaster.localSubscriberCount(sweep.topic))
        broadcaster.broadcast(sweep.topic, json, UpdateComponentMessage(componentId = componentId, component = component))
    }

    private suspend fun check(
        group: String,
        module: String,
        repository: MavenRepository,
        index: RepositoryIndex,
    ): ModuleResult {
        val advertised = index.versions(group, module)
        // The newest release, and a snapshot only when there is no release: a snapshot beside a
        // release is not a later version of it, whatever order the metadata happens to list them in.
        val version = advertised.lastOrNull { !it.endsWith("-SNAPSHOT") }
            ?: advertised.lastOrNull()
            ?: return ModuleResult.Refused(module, "the repository advertises no version of it")
        val coordinate = Coordinate(group, module, version)
        return when (val outcome = reader.read(coordinate, repository)) {
            is ReadOutcome.Read -> {
                val component = outcome.publication.component
                if (component != null && component.isBackReference) {
                    ModuleResult.PartOf(component.coordinate)
                } else {
                    ModuleResult.Checked(coordinate, Checks.runAll(context(outcome.publication, repository)))
                }
            }
            is ReadOutcome.NotFound -> ModuleResult.Refused(module, "$version is advertised but nothing is published at it")
            is ReadOutcome.WithoutModuleMetadata -> ModuleResult.Refused(module, "$version has no Gradle module metadata")
            is ReadOutcome.Unreadable -> ModuleResult.Refused(module, "$version: ${outcome.reason}")
            is ReadOutcome.UnsupportedLayout -> ModuleResult.Refused(module, outcome.reason)
        }
    }

    private fun context(publication: Publication, repository: MavenRepository): CheckContext {
        val cache = mutableMapOf<Coordinate, Publication?>()
        return CheckContext(
            publication = publication,
            lookup = { wanted -> cache.getOrPut(wanted) { (reader.read(wanted, repository) as? ReadOutcome.Read)?.publication } },
        )
    }
}

/** The sweep screen: a status line and one card per module, each replaced by a frame as it finishes. */
object SweepScreen {

    const val STATUS_ID = "sweep-status"

    fun moduleId(position: Int) = "m$position"

    fun of(sweep: Sweep, results: Map<String, ModuleResult>): KompotComponent =
        ColumnComponent(
            id = "sweep",
            spacing = 12,
            modifiers = listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(Color.Surface),
                KompotModifierNode.Padding(all = 32),
            ),
            children = buildList {
                add(TextComponent(id = "sweep-title", text = sweep.group, style = Type.TitlePage))
                add(status(results))
                sweep.modules.forEachIndexed { position, module ->
                    add(moduleCard(position, module, results[module] ?: ModuleResult.Pending))
                }
            },
        )

    fun status(results: Map<String, ModuleResult>): KompotComponent {
        val done = results.values.count { it !is ModuleResult.Pending }
        val findings = results.values.filterIsInstance<ModuleResult.Checked>().sumOf { it.findings.size }
        val targets = results.values.count { it is ModuleResult.PartOf }
        return TextComponent(
            id = STATUS_ID,
            text = "$done of ${results.size} read — $findings finding(s) so far; $targets of the modules are targets of another",
            style = Type.BodySmall,
        )
    }

    fun moduleCard(position: Int, module: String, result: ModuleResult): KompotComponent {
        val id = moduleId(position)
        val body: List<KompotComponent> = when (result) {
            is ModuleResult.Pending -> listOf(TextComponent(id = "$id-state", text = "waiting", style = Type.BodySmall))

            is ModuleResult.Refused -> listOf(TextComponent(id = "$id-state", text = result.reason, style = Type.BodySmall))

            is ModuleResult.PartOf -> listOf(
                TextComponent(id = "$id-state", text = "a target of ${result.owner.artifact}", style = Type.BodySmall),
            )

            is ModuleResult.Checked -> listOf(
                TextComponent(id = "$id-version", text = result.coordinate.version, style = Type.CodeSmall),
                counts(id, result.findings),
            )
        }
        return ColumnComponent(
            id = id,
            spacing = 6,
            modifiers = listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(
                    if (result is ModuleResult.Pending || result is ModuleResult.PartOf) Color.SurfaceSunken else Color.SurfaceRaised,
                ),
                KompotModifierNode.Padding(all = 16),
            ),
            children = listOf(TextComponent(id = "$id-name", text = module, style = Type.TitleSection)) + body,
        )
    }

    private fun counts(id: String, findings: List<Finding>): KompotComponent {
        if (findings.isEmpty()) {
            return TextComponent(id = "$id-clean", text = "nothing to report", style = Type.BodySmall)
        }
        val bySeverity = findings.groupingBy { it.severity }.eachCount()
        return RowComponent(
            id = "$id-counts",
            spacing = 8,
            children = Severity.entries.filter { countOf(bySeverity, it) > 0 }.map { severity ->
                val look = when (severity) {
                    Severity.Defect -> SeverityLook.Defect
                    Severity.Suspicion -> SeverityLook.Suspicion
                    Severity.Undetermined -> SeverityLook.Undetermined
                }
                RowComponent(
                    id = "$id-${look.word}",
                    spacing = 6,
                    modifiers = listOf(KompotModifierNode.Background(look.surface), KompotModifierNode.Padding(all = 6)),
                    children = listOf(
                        TextComponent(
                            id = "$id-${look.word}-text",
                            text = "${countOf(bySeverity, severity)} ${look.word}",
                            style = Type.Label,
                        ),
                    ),
                )
            },
        )
    }

    private fun countOf(bySeverity: Map<Severity, Int>, severity: Severity) = bySeverity[severity] ?: 0
}
