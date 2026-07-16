package com.majortomman.school.data.curriculum

import android.content.Context
import androidx.room.withTransaction
import com.majortomman.school.data.MasteryStatus
import com.majortomman.school.data.local.CurriculumNodeProgressEntity
import com.majortomman.school.data.local.SchoolDatabase
import com.majortomman.school.data.local.toDomain
import com.majortomman.school.data.local.toEntity
import com.majortomman.school.data.material.EducationStage
import com.majortomman.school.data.material.InstalledTextbook
import com.majortomman.school.data.material.gradeLabel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CurriculumRepository(
    context: Context,
) {
    private val database = SchoolDatabase.getInstance(context.applicationContext)
    private val dao = database.curriculumDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class StructureRows(
        val subjects: List<com.majortomman.school.data.local.SubjectEntity>,
        val systems: List<com.majortomman.school.data.local.LearningLevelSystemEntity>,
        val levels: List<com.majortomman.school.data.local.LearningLevelEntity>,
        val curricula: List<com.majortomman.school.data.local.CurriculumEntity>,
        val nodes: List<com.majortomman.school.data.local.CurriculumNodeEntity>,
    )

    private data class KnowledgeRows(
        val points: List<com.majortomman.school.data.local.KnowledgePointEntity>,
        val relations: List<com.majortomman.school.data.local.KnowledgeRelationEntity>,
        val refs: List<com.majortomman.school.data.local.NodeKnowledgeRefEntity>,
        val resources: List<com.majortomman.school.data.local.LearningResourceEntity>,
        val bindings: List<com.majortomman.school.data.local.ResourceBindingEntity>,
    )

    private val structureFlow = combine(
        dao.observeSubjects(),
        dao.observeLevelSystems(),
        dao.observeLevels(),
        dao.observeCurricula(),
        dao.observeNodes(),
    ) { subjects, systems, levels, curricula, nodes ->
        StructureRows(subjects, systems, levels, curricula, nodes)
    }

    private val knowledgeFlow = combine(
        dao.observeKnowledgePoints(),
        dao.observeKnowledgeRelations(),
        dao.observeNodeKnowledgeRefs(),
        dao.observeResources(),
        dao.observeResourceBindings(),
    ) { points, relations, refs, resources, bindings ->
        KnowledgeRows(points, relations, refs, resources, bindings)
    }

    val state: StateFlow<CurriculumSnapshot> = combine(structureFlow, knowledgeFlow) { structure, knowledge ->
        CurriculumSnapshot(
            subjects = structure.subjects.map { it.toDomain() },
            levelSystems = structure.systems.map { it.toDomain() },
            levels = structure.levels.map { it.toDomain() },
            curricula = structure.curricula.map { it.toDomain() },
            nodes = structure.nodes.map { it.toDomain() },
            knowledgePoints = knowledge.points.map { it.toDomain() },
            knowledgeRelations = knowledge.relations.map { it.toDomain() },
            nodeKnowledgeRefs = knowledge.refs.map { it.toDomain() },
            resources = knowledge.resources.map { it.toDomain() },
            resourceBindings = knowledge.bindings.map { it.toDomain() },
        )
    }.stateIn(scope, SharingStarted.Eagerly, CurriculumSnapshot())

    val nodeProgress: StateFlow<Map<String, CurriculumNodeProgress>> = dao.observeNodeProgress()
        .map { rows -> rows.associate { it.nodeId to it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        scope.launch { seedBuiltins() }
    }

    suspend fun seedBuiltins() {
        database.withTransaction {
            dao.upsertSubjects(BuiltinCurriculumCatalog.subjects.map { it.toEntity() })
            dao.upsertLevelSystems(BuiltinCurriculumCatalog.levelSystems.map { it.toEntity() })
            dao.upsertLevels(BuiltinCurriculumCatalog.levels.map { it.toEntity() })
            dao.upsertKnowledgePoints(BuiltinCurriculumCatalog.knowledgePoints.map { it.toEntity() })
            dao.upsertKnowledgeRelations(
                BuiltinCurriculumCatalog.knowledgeRelations.map { it.toEntity(ORIGIN_BUILTIN) },
            )
        }
    }

    suspend fun synchronizeInstalledTextbooks(textbooks: List<InstalledTextbook>) {
        seedBuiltins()
        val graph = buildMaterialGraph(textbooks)
        val validationErrors = graph.snapshot.validate()
        require(validationErrors.isEmpty()) { validationErrors.joinToString("；") }

        database.withTransaction {
            dao.deleteMaterialResourceBindings()
            dao.deleteMaterialResources()
            dao.deleteMaterialNodeKnowledgeRefs()
            dao.deleteMaterialNodes()
            dao.deleteMaterialCurricula()
            dao.deleteMaterialKnowledgeRelations()

            graph.additionalSubjects.takeIf(List<SubjectDefinition>::isNotEmpty)
                ?.let { dao.upsertSubjects(it.map(SubjectDefinition::toEntity)) }
            graph.snapshot.curricula.takeIf(List<Curriculum>::isNotEmpty)
                ?.let { dao.upsertCurricula(it.map(Curriculum::toEntity)) }
            graph.snapshot.nodes.takeIf(List<CurriculumNode>::isNotEmpty)
                ?.let { dao.upsertNodes(it.map(CurriculumNode::toEntity)) }
            graph.generatedKnowledge.takeIf(List<KnowledgePoint>::isNotEmpty)
                ?.let { dao.upsertKnowledgePoints(it.map(KnowledgePoint::toEntity)) }
            graph.materialRelations.takeIf(List<KnowledgeRelation>::isNotEmpty)
                ?.let { dao.upsertKnowledgeRelations(it.map { relation -> relation.toEntity(ORIGIN_MATERIAL) }) }
            graph.snapshot.nodeKnowledgeRefs.takeIf(List<NodeKnowledgeRef>::isNotEmpty)
                ?.let { dao.upsertNodeKnowledgeRefs(it.map(NodeKnowledgeRef::toEntity)) }
            graph.snapshot.resources.takeIf(List<LearningResource>::isNotEmpty)
                ?.let { dao.upsertResources(it.map { resource -> resource.toEntity(ORIGIN_MATERIAL) }) }
            graph.snapshot.resourceBindings.takeIf(List<ResourceBinding>::isNotEmpty)
                ?.let { dao.upsertResourceBindings(it.map(ResourceBinding::toEntity)) }
            dao.deleteOrphanNodeProgress()
        }
    }

    fun observeMastery(subjectId: String): Flow<List<KnowledgeMastery>> =
        dao.observeKnowledgeMastery(subjectId).map { rows -> rows.map { it.toDomain() } }

    suspend fun getMastery(subjectId: String, knowledgePointId: String): KnowledgeMastery? =
        dao.getKnowledgeMastery(subjectId, knowledgePointId)?.toDomain()

    suspend fun upsertMastery(mastery: KnowledgeMastery) {
        dao.upsertKnowledgeMastery(mastery.toEntity())
    }

    suspend fun markLegacyLessonStatus(legacyLessonId: String, status: MasteryStatus) {
        val node = state.value.nodeForLegacyLesson(legacyLessonId)
            ?: dao.getNodeByLegacyLessonId(legacyLessonId)?.toDomain()
            ?: return
        markNodeStatus(node.id, status.toCurriculumStatus())
    }

    suspend fun markNodeStatus(nodeId: String, status: CurriculumNodeProgressStatus) {
        val now = System.currentTimeMillis()
        val previous = nodeProgress.value[nodeId]
        dao.upsertNodeProgress(
            CurriculumNodeProgress(
                nodeId = nodeId,
                status = status,
                lastVisitedAt = now,
                completedAt = when {
                    status == CurriculumNodeProgressStatus.MASTERED -> previous?.completedAt ?: now
                    else -> previous?.completedAt
                },
            ).toEntity(),
        )
    }

    suspend fun lessonTitle(legacyLessonId: String): String? = state.value
        .nodeForLegacyLesson(legacyLessonId)
        ?.title
        ?: dao.getNodeByLegacyLessonId(legacyLessonId)?.title

    fun curriculumIdFor(textbook: InstalledTextbook): String = curriculumIdFor(
        subjectId = textbook.slot.subjectId,
        stage = textbook.slot.stage,
    )

    fun curriculumIdForLegacyTextbook(textbookKey: String): String? =
        state.value.curriculumIdForLegacyTextbook(textbookKey)

    suspend fun clearLearningState() {
        dao.clearKnowledgeMastery()
        dao.clearNodeProgress()
    }

    internal data class MaterialGraph(
        val snapshot: CurriculumSnapshot,
        val additionalSubjects: List<SubjectDefinition>,
        val generatedKnowledge: List<KnowledgePoint>,
        val materialRelations: List<KnowledgeRelation>,
    )

    internal fun buildMaterialGraph(textbooks: List<InstalledTextbook>): MaterialGraph {
        val subjects = linkedMapOf<String, SubjectDefinition>()
        val curricula = linkedMapOf<String, Curriculum>()
        val nodes = linkedMapOf<String, CurriculumNode>()
        val knowledge = linkedMapOf<String, KnowledgePoint>()
        val refs = linkedMapOf<String, NodeKnowledgeRef>()
        val resources = linkedMapOf<String, LearningResource>()
        val bindings = linkedMapOf<String, ResourceBinding>()
        val relations = linkedMapOf<String, KnowledgeRelation>()

        textbooks.sortedWith(compareBy({ it.slot.subjectId }, { it.slot.stage.ordinal }, { it.slot.grade }, { it.slot.volume.id }))
            .forEachIndexed { textbookIndex, textbook ->
                val slot = textbook.slot
                val subject = BuiltinCurriculumCatalog.subject(slot.subjectId, slot.subjectTitle)
                if (slot.subjectId !in BuiltinCurriculumCatalog.subjectById) subjects[subject.id] = subject

                val curriculumId = curriculumIdFor(slot.subjectId, slot.stage)
                curricula.putIfAbsent(
                    curriculumId,
                    Curriculum(
                        id = curriculumId,
                        subjectId = slot.subjectId,
                        title = "${slot.stage.label}${subject.title}课程",
                        levelSystemId = if (slot.stage == EducationStage.UNIVERSITY) {
                            BuiltinCurriculumCatalog.UNIVERSITY_LEVEL_SYSTEM
                        } else {
                            BuiltinCurriculumCatalog.BASIC_EDUCATION_LEVEL_SYSTEM
                        },
                        standard = "School 可扩展课程树",
                        region = "CN",
                        version = MATERIAL_CURRICULUM_VERSION,
                        source = CurriculumSource.MATERIAL,
                        orderIndex = subject.orderIndex,
                    ),
                )

                val rootId = "$curriculumId:root"
                nodes.putIfAbsent(
                    rootId,
                    CurriculumNode(rootId, curriculumId, null, CurriculumNodeType.ROOT, curricula.getValue(curriculumId).title, 0),
                )
                val levelId = "$curriculumId:level:${slot.grade}"
                nodes.putIfAbsent(
                    levelId,
                    CurriculumNode(
                        id = levelId,
                        curriculumId = curriculumId,
                        parentId = rootId,
                        type = CurriculumNodeType.LEVEL,
                        title = gradeLabel(slot.grade),
                        orderIndex = slot.grade,
                        metadata = mapOf("legacyGrade" to slot.grade.toString(), "stage" to slot.stage.id),
                    ),
                )
                val termId = "$curriculumId:term:${slot.grade}:${slot.volume.id}"
                nodes.putIfAbsent(
                    termId,
                    CurriculumNode(
                        id = termId,
                        curriculumId = curriculumId,
                        parentId = levelId,
                        type = CurriculumNodeType.TERM,
                        title = slot.volumeLabel,
                        orderIndex = slot.volume.id,
                        metadata = mapOf("legacyVolume" to slot.volume.id.toString()),
                    ),
                )
                val courseId = "$curriculumId:course:${slot.key}"
                val pdfBound = textbook.pack.pdfFile.isFile
                nodes[courseId] = CurriculumNode(
                    id = courseId,
                    curriculumId = curriculumId,
                    parentId = termId,
                    type = CurriculumNodeType.COURSE,
                    title = textbook.pack.manifest.title,
                    orderIndex = textbookIndex,
                    metadata = mapOf(
                        "legacyTextbookKey" to slot.key,
                        "pdfBound" to pdfBound.toString(),
                        "pageCount" to textbook.pageCount.toString(),
                    ),
                )

                val resourceId = "material:${slot.key}"
                resources[resourceId] = LearningResource(
                    id = resourceId,
                    subjectId = slot.subjectId,
                    type = if (pdfBound) ResourceType.TEXTBOOK_PDF else ResourceType.PREBUILT_COURSE,
                    title = textbook.pack.manifest.title,
                    uri = textbook.pack.pdfFile.takeIf(File::isFile)?.toURI()?.toString(),
                    publisher = textbookPublisher(textbook),
                    edition = textbookEdition(textbook),
                    legacyTextbookKey = slot.key,
                    metadata = mapOf(
                        "version" to textbook.pack.manifest.version,
                        "sha256" to textbook.pack.manifest.pdf.sha256,
                        "pageIndexOffset" to textbook.pack.manifest.pdf.pageIndexOffset.toString(),
                    ),
                )
                bindings["$resourceId:$courseId"] = ResourceBinding(
                    id = "$resourceId:$courseId",
                    resourceId = resourceId,
                    nodeId = courseId,
                    knowledgePointId = null,
                    role = ResourceBindingRole.PRIMARY,
                    pageStart = null,
                    pageEnd = null,
                    orderIndex = 0,
                )

                var previousPoint: KnowledgePoint? = null
                textbook.lessons.forEachIndexed { lessonIndex, lesson ->
                    val lessonNodeId = "$curriculumId:lesson:${slot.key}:${BuiltinCurriculumCatalog.stableId(lesson.sourceId)}"
                    nodes[lessonNodeId] = CurriculumNode(
                        id = lessonNodeId,
                        curriculumId = curriculumId,
                        parentId = courseId,
                        type = CurriculumNodeType.LESSON,
                        title = lesson.title,
                        orderIndex = lessonIndex,
                        legacyLessonId = lesson.id,
                        metadata = mapOf(
                            "sourceId" to lesson.sourceId,
                            "pageStart" to lesson.pageStart.toString(),
                            "pageEnd" to lesson.pageEnd.toString(),
                            "estimatedMinutes" to lesson.estimatedMinutes.toString(),
                        ),
                    )
                    val point = BuiltinCurriculumCatalog.inferKnowledge(slot.subjectId, lesson.title)
                    if (point.id !in BuiltinCurriculumCatalog.knowledgeById) knowledge.putIfAbsent(point.id, point)
                    refs["$lessonNodeId:${point.id}"] = NodeKnowledgeRef(
                        nodeId = lessonNodeId,
                        knowledgePointId = point.id,
                        role = KnowledgeRole.PRIMARY,
                        orderIndex = 0,
                    )
                    bindings["$resourceId:$lessonNodeId"] = ResourceBinding(
                        id = "$resourceId:$lessonNodeId",
                        resourceId = resourceId,
                        nodeId = lessonNodeId,
                        knowledgePointId = point.id,
                        role = ResourceBindingRole.EVIDENCE,
                        pageStart = lesson.pageStart,
                        pageEnd = lesson.pageEnd,
                        orderIndex = lessonIndex,
                    )
                    previousPoint?.takeIf { it.id != point.id }?.let { previous ->
                        val key = "${previous.id}:${point.id}:EXTENDS"
                        relations.putIfAbsent(
                            key,
                            KnowledgeRelation(
                                fromKnowledgeId = previous.id,
                                toKnowledgeId = point.id,
                                type = KnowledgeRelationType.EXTENDS,
                                weight = 0.35,
                            ),
                        )
                    }
                    previousPoint = point
                }
            }

        val allKnowledge = (BuiltinCurriculumCatalog.knowledgePoints + knowledge.values).distinctBy(KnowledgePoint::id)
        val snapshot = CurriculumSnapshot(
            subjects = (BuiltinCurriculumCatalog.subjects + subjects.values).distinctBy(SubjectDefinition::id),
            levelSystems = BuiltinCurriculumCatalog.levelSystems,
            levels = BuiltinCurriculumCatalog.levels,
            curricula = curricula.values.toList(),
            nodes = nodes.values.toList(),
            knowledgePoints = allKnowledge,
            knowledgeRelations = (BuiltinCurriculumCatalog.knowledgeRelations + relations.values)
                .distinctBy { "${it.fromKnowledgeId}:${it.toKnowledgeId}:${it.type}" },
            nodeKnowledgeRefs = refs.values.toList(),
            resources = resources.values.toList(),
            resourceBindings = bindings.values.toList(),
        )
        return MaterialGraph(
            snapshot = snapshot,
            additionalSubjects = subjects.values.toList(),
            generatedKnowledge = knowledge.values.toList(),
            materialRelations = relations.values.toList(),
        )
    }

    private fun curriculumIdFor(subjectId: String, stage: EducationStage): String =
        "material:$subjectId:${stage.id}"

    private fun textbookPublisher(textbook: InstalledTextbook): String? = runCatching {
        val catalog = org.json.JSONObject(textbook.pack.catalogFile.readText(Charsets.UTF_8))
        catalog.optJSONObject("book")?.optString("publisher")?.takeIf(String::isNotBlank)
    }.getOrNull()

    private fun textbookEdition(textbook: InstalledTextbook): String? = runCatching {
        val catalog = org.json.JSONObject(textbook.pack.catalogFile.readText(Charsets.UTF_8))
        catalog.optJSONObject("book")?.optString("edition")?.takeIf(String::isNotBlank)
    }.getOrNull()

    private fun MasteryStatus.toCurriculumStatus(): CurriculumNodeProgressStatus = when (this) {
        MasteryStatus.NOT_STARTED -> CurriculumNodeProgressStatus.NOT_STARTED
        MasteryStatus.LEARNING -> CurriculumNodeProgressStatus.LEARNING
        MasteryStatus.MASTERED -> CurriculumNodeProgressStatus.MASTERED
        MasteryStatus.NEEDS_REVIEW -> CurriculumNodeProgressStatus.NEEDS_REVIEW
    }

    private companion object {
        const val ORIGIN_BUILTIN = "BUILTIN"
        const val ORIGIN_MATERIAL = "MATERIAL"
        const val MATERIAL_CURRICULUM_VERSION = "material-tree-v1"
    }
}
