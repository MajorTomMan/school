package com.majortomman.school.data.curriculum

import android.content.Context
import androidx.room.withTransaction
import com.majortomman.school.data.MasteryStatus
import com.majortomman.school.data.local.SchoolDatabase
import com.majortomman.school.data.local.toDomain
import com.majortomman.school.data.local.toEntity
import com.majortomman.school.data.material.EducationStage
import com.majortomman.school.data.material.InstalledTextbook
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

    internal fun buildMaterialGraph(textbooks: List<InstalledTextbook>): MaterialGraph =
        MaterialCurriculumGraphBuilder.build(textbooks)

    private fun curriculumIdFor(subjectId: String, stage: EducationStage): String =
        "material:$subjectId:${stage.id}"

    private fun MasteryStatus.toCurriculumStatus(): CurriculumNodeProgressStatus = when (this) {
        MasteryStatus.NOT_STARTED -> CurriculumNodeProgressStatus.NOT_STARTED
        MasteryStatus.LEARNING -> CurriculumNodeProgressStatus.LEARNING
        MasteryStatus.MASTERED -> CurriculumNodeProgressStatus.MASTERED
        MasteryStatus.NEEDS_REVIEW -> CurriculumNodeProgressStatus.NEEDS_REVIEW
    }

    private companion object {
        const val ORIGIN_BUILTIN = "BUILTIN"
        const val ORIGIN_MATERIAL = "MATERIAL"
    }
}
