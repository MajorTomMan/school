package com.majortomman.school.data.curriculum

enum class SubjectCategory {
    LANGUAGE,
    MATHEMATICS,
    NATURAL_SCIENCE,
    SOCIAL_SCIENCE,
    TECHNOLOGY,
    ARTS,
    HEALTH,
    PROFESSIONAL,
    OTHER,
}

enum class LearningCapability {
    TEXT_READING,
    TEXT_WRITING,
    VOCABULARY,
    GRAMMAR,
    AUDIO,
    PRONUNCIATION,
    NUMERIC_EVALUATION,
    EXPRESSION_EVALUATION,
    STEP_EVALUATION,
    GRAPH_RENDERING,
    GEOMETRY,
    FORMULA,
    EXPERIMENT,
    TIMELINE,
    CAUSAL_REASONING,
    MAP,
    ESSAY,
    PROGRAMMING,
}

data class SubjectDefinition(
    val id: String,
    val title: String,
    val category: SubjectCategory,
    val description: String,
    val capabilityIds: Set<LearningCapability>,
    val stageIds: Set<String> = emptySet(),
    val orderIndex: Int = 0,
)

data class LearningLevelSystem(
    val id: String,
    val title: String,
    val description: String,
)

data class LearningLevel(
    val id: String,
    val systemId: String,
    val parentId: String?,
    val title: String,
    val orderIndex: Int,
    val legacyGrade: Int? = null,
)

data class Curriculum(
    val id: String,
    val subjectId: String,
    val title: String,
    val levelSystemId: String?,
    val standard: String?,
    val region: String?,
    val version: String,
    val source: CurriculumSource,
    val orderIndex: Int = 0,
)

enum class CurriculumSource {
    BUILTIN,
    MATERIAL,
    USER,
}

enum class CurriculumNodeType {
    ROOT,
    STAGE,
    LEVEL,
    TERM,
    COURSE,
    MODULE,
    UNIT,
    CHAPTER,
    LESSON,
    TOPIC,
}

data class CurriculumNode(
    val id: String,
    val curriculumId: String,
    val parentId: String?,
    val type: CurriculumNodeType,
    val title: String,
    val orderIndex: Int,
    val legacyLessonId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

enum class KnowledgeKind {
    CONCEPT,
    FACT,
    PROCEDURE,
    METHOD,
    SKILL,
    STRATEGY,
    PRINCIPLE,
}

data class KnowledgePoint(
    val id: String,
    val subjectId: String,
    val title: String,
    val description: String,
    val kind: KnowledgeKind,
    val evaluatorId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

enum class KnowledgeRelationType {
    PREREQUISITE,
    PART_OF,
    RELATED,
    EQUIVALENT,
    EXTENDS,
}

data class KnowledgeRelation(
    val fromKnowledgeId: String,
    val toKnowledgeId: String,
    val type: KnowledgeRelationType,
    val weight: Double = 1.0,
)

enum class KnowledgeRole {
    PRIMARY,
    SUPPORTING,
    REVIEW,
}

data class NodeKnowledgeRef(
    val nodeId: String,
    val knowledgePointId: String,
    val role: KnowledgeRole,
    val orderIndex: Int,
)

enum class ResourceType {
    TEXTBOOK_PDF,
    PREBUILT_COURSE,
    DOCUMENT,
    IMAGE,
    AUDIO,
    VIDEO,
    QUESTION_BANK,
    WEB,
}

data class LearningResource(
    val id: String,
    val subjectId: String,
    val type: ResourceType,
    val title: String,
    val uri: String?,
    val publisher: String?,
    val edition: String?,
    val legacyTextbookKey: String?,
    val metadata: Map<String, String> = emptyMap(),
)

enum class ResourceBindingRole {
    PRIMARY,
    REFERENCE,
    EVIDENCE,
    PRACTICE,
}

data class ResourceBinding(
    val id: String,
    val resourceId: String,
    val nodeId: String?,
    val knowledgePointId: String?,
    val role: ResourceBindingRole,
    val pageStart: Int?,
    val pageEnd: Int?,
    val orderIndex: Int,
)

data class KnowledgeMastery(
    val subjectId: String,
    val knowledgePointId: String,
    val score: Double,
    val attempts: Int,
    val correctStreak: Int,
    val wrongStreak: Int,
    val lastCorrect: Boolean,
    val dueAt: Long,
    val updatedAt: Long,
)

enum class CurriculumNodeProgressStatus {
    NOT_STARTED,
    LEARNING,
    MASTERED,
    NEEDS_REVIEW,
}

data class CurriculumNodeProgress(
    val nodeId: String,
    val status: CurriculumNodeProgressStatus,
    val lastVisitedAt: Long,
    val completedAt: Long?,
)

data class CurriculumTreeRow(
    val node: CurriculumNode,
    val depth: Int,
)

data class CurriculumSnapshot(
    val subjects: List<SubjectDefinition> = emptyList(),
    val levelSystems: List<LearningLevelSystem> = emptyList(),
    val levels: List<LearningLevel> = emptyList(),
    val curricula: List<Curriculum> = emptyList(),
    val nodes: List<CurriculumNode> = emptyList(),
    val knowledgePoints: List<KnowledgePoint> = emptyList(),
    val knowledgeRelations: List<KnowledgeRelation> = emptyList(),
    val nodeKnowledgeRefs: List<NodeKnowledgeRef> = emptyList(),
    val resources: List<LearningResource> = emptyList(),
    val resourceBindings: List<ResourceBinding> = emptyList(),
) {
    val subjectById: Map<String, SubjectDefinition> by lazy { subjects.associateBy(SubjectDefinition::id) }
    val curriculumById: Map<String, Curriculum> by lazy { curricula.associateBy(Curriculum::id) }
    val nodeById: Map<String, CurriculumNode> by lazy { nodes.associateBy(CurriculumNode::id) }
    val knowledgeById: Map<String, KnowledgePoint> by lazy { knowledgePoints.associateBy(KnowledgePoint::id) }
    val resourceById: Map<String, LearningResource> by lazy { resources.associateBy(LearningResource::id) }
    val childrenByParent: Map<String?, List<CurriculumNode>> by lazy {
        nodes.groupBy(CurriculumNode::parentId).mapValues { (_, value) ->
            value.sortedWith(compareBy(CurriculumNode::orderIndex, CurriculumNode::title))
        }
    }
    val knowledgeRefsByNode: Map<String, List<NodeKnowledgeRef>> by lazy {
        nodeKnowledgeRefs.groupBy(NodeKnowledgeRef::nodeId).mapValues { (_, value) ->
            value.sortedBy(NodeKnowledgeRef::orderIndex)
        }
    }
    val bindingsByNode: Map<String, List<ResourceBinding>> by lazy {
        resourceBindings.filter { it.nodeId != null }.groupBy { requireNotNull(it.nodeId) }
    }

    fun roots(curriculumId: String): List<CurriculumNode> = nodes
        .filter { it.curriculumId == curriculumId && it.parentId == null }
        .sortedWith(compareBy(CurriculumNode::orderIndex, CurriculumNode::title))

    fun children(nodeId: String): List<CurriculumNode> = childrenByParent[nodeId].orEmpty()

    fun ancestors(nodeId: String): List<CurriculumNode> {
        val result = mutableListOf<CurriculumNode>()
        val visited = mutableSetOf<String>()
        var current = nodeById[nodeId]?.parentId
        while (current != null && visited.add(current)) {
            val node = nodeById[current] ?: break
            result += node
            current = node.parentId
        }
        return result.asReversed()
    }

    fun descendants(nodeId: String): List<CurriculumNode> = buildList {
        val queue = ArrayDeque(children(nodeId))
        val visited = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!visited.add(node.id)) continue
            add(node)
            children(node.id).forEach(queue::addLast)
        }
    }

    fun prerequisites(knowledgePointId: String): List<KnowledgePoint> = knowledgeRelations
        .filter { it.type == KnowledgeRelationType.PREREQUISITE && it.toKnowledgeId == knowledgePointId }
        .sortedByDescending(KnowledgeRelation::weight)
        .mapNotNull { knowledgeById[it.fromKnowledgeId] }

    fun knowledgeForNode(nodeId: String): List<KnowledgePoint> = knowledgeRefsByNode[nodeId]
        .orEmpty()
        .mapNotNull { knowledgeById[it.knowledgePointId] }

    fun resourcesForNode(nodeId: String): List<Pair<LearningResource, ResourceBinding>> = bindingsByNode[nodeId]
        .orEmpty()
        .sortedBy(ResourceBinding::orderIndex)
        .mapNotNull { binding -> resourceById[binding.resourceId]?.let { it to binding } }

    fun nodeForLegacyLesson(legacyLessonId: String): CurriculumNode? =
        nodes.firstOrNull { it.legacyLessonId == legacyLessonId }

    fun curriculumIdForLegacyTextbook(legacyTextbookKey: String): String? = resources
        .firstOrNull { it.legacyTextbookKey == legacyTextbookKey }
        ?.let { resource -> resourceBindings.firstOrNull { it.resourceId == resource.id }?.nodeId }
        ?.let(nodeById::get)
        ?.curriculumId

    fun flattenedTree(curriculumId: String): List<CurriculumTreeRow> = buildList {
        val visited = mutableSetOf<String>()
        fun append(node: CurriculumNode, depth: Int) {
            if (!visited.add(node.id)) return
            add(CurriculumTreeRow(node, depth))
            children(node.id).forEach { append(it, depth + 1) }
        }
        roots(curriculumId).forEach { append(it, 0) }
    }

    fun validate(): List<String> = buildList {
        if (subjects.map { it.id }.toSet().size != subjects.size) add("学科 ID 重复")
        if (curricula.map { it.id }.toSet().size != curricula.size) add("课程体系 ID 重复")
        if (nodes.map { it.id }.toSet().size != nodes.size) add("课程节点 ID 重复")
        if (knowledgePoints.map { it.id }.toSet().size != knowledgePoints.size) add("知识点 ID 重复")
        nodes.forEach { node ->
            if (node.curriculumId !in curriculumById) add("节点 ${node.id} 缺少课程体系")
            if (node.parentId != null && node.parentId !in nodeById) add("节点 ${node.id} 缺少父节点")
            if (node.parentId != null && nodeById[node.parentId]?.curriculumId != node.curriculumId) {
                add("节点 ${node.id} 与父节点不属于同一课程体系")
            }
            val ancestors = mutableSetOf<String>()
            var cursor: CurriculumNode? = node
            while (cursor?.parentId != null) {
                val parentId = cursor.parentId ?: break
                if (!ancestors.add(parentId)) {
                    add("课程树存在循环：${node.id}")
                    break
                }
                cursor = nodeById[parentId]
            }
        }
        knowledgeRelations.forEach { relation ->
            if (relation.fromKnowledgeId !in knowledgeById || relation.toKnowledgeId !in knowledgeById) {
                add("知识关系引用不存在的知识点")
            }
        }
        nodeKnowledgeRefs.forEach { ref ->
            if (ref.nodeId !in nodeById || ref.knowledgePointId !in knowledgeById) {
                add("课程节点知识绑定无效")
            }
        }
    }.distinct()
}
