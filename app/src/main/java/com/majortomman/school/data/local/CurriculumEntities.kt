package com.majortomman.school.data.local

import androidx.room.Entity
import androidx.room.Index
import com.majortomman.school.data.curriculum.Curriculum
import com.majortomman.school.data.curriculum.CurriculumNode
import com.majortomman.school.data.curriculum.CurriculumNodeProgress
import com.majortomman.school.data.curriculum.CurriculumNodeProgressStatus
import com.majortomman.school.data.curriculum.CurriculumNodeType
import com.majortomman.school.data.curriculum.CurriculumSource
import com.majortomman.school.data.curriculum.KnowledgeKind
import com.majortomman.school.data.curriculum.KnowledgeMastery
import com.majortomman.school.data.curriculum.KnowledgePoint
import com.majortomman.school.data.curriculum.KnowledgeRelation
import com.majortomman.school.data.curriculum.KnowledgeRelationType
import com.majortomman.school.data.curriculum.KnowledgeRole
import com.majortomman.school.data.curriculum.LearningCapability
import com.majortomman.school.data.curriculum.LearningLevel
import com.majortomman.school.data.curriculum.LearningLevelSystem
import com.majortomman.school.data.curriculum.LearningResource
import com.majortomman.school.data.curriculum.NodeKnowledgeRef
import com.majortomman.school.data.curriculum.ResourceBinding
import com.majortomman.school.data.curriculum.ResourceBindingRole
import com.majortomman.school.data.curriculum.ResourceType
import com.majortomman.school.data.curriculum.SubjectCategory
import com.majortomman.school.data.curriculum.SubjectDefinition
import org.json.JSONObject

@Entity(tableName = "subjects", indices = [Index(value = ["orderIndex"])])
data class SubjectEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val category: String,
    val description: String,
    val capabilityIds: String,
    val stageIds: String,
    val orderIndex: Int,
)

@Entity(tableName = "learning_level_systems")
data class LearningLevelSystemEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val description: String,
)

@Entity(
    tableName = "learning_levels",
    indices = [Index(value = ["systemId"]), Index(value = ["parentId"]), Index(value = ["legacyGrade"])],
)
data class LearningLevelEntity(
    @androidx.room.PrimaryKey val id: String,
    val systemId: String,
    val parentId: String?,
    val title: String,
    val orderIndex: Int,
    val legacyGrade: Int?,
)

@Entity(
    tableName = "curricula",
    indices = [Index(value = ["subjectId"]), Index(value = ["source"]), Index(value = ["orderIndex"])],
)
data class CurriculumEntity(
    @androidx.room.PrimaryKey val id: String,
    val subjectId: String,
    val title: String,
    val levelSystemId: String?,
    val standard: String?,
    val region: String?,
    val version: String,
    val source: String,
    val orderIndex: Int,
)

@Entity(
    tableName = "curriculum_nodes",
    indices = [
        Index(value = ["curriculumId"]),
        Index(value = ["parentId"]),
        Index(value = ["legacyLessonId"]),
        Index(value = ["curriculumId", "parentId", "orderIndex"]),
    ],
)
data class CurriculumNodeEntity(
    @androidx.room.PrimaryKey val id: String,
    val curriculumId: String,
    val parentId: String?,
    val type: String,
    val title: String,
    val orderIndex: Int,
    val legacyLessonId: String?,
    val metadataJson: String,
)

@Entity(
    tableName = "knowledge_points",
    indices = [Index(value = ["subjectId"]), Index(value = ["kind"])],
)
data class KnowledgePointEntity(
    @androidx.room.PrimaryKey val id: String,
    val subjectId: String,
    val title: String,
    val description: String,
    val kind: String,
    val evaluatorId: String?,
    val metadataJson: String,
)

@Entity(
    tableName = "knowledge_relations",
    primaryKeys = ["fromKnowledgeId", "toKnowledgeId", "type"],
    indices = [Index(value = ["toKnowledgeId"]), Index(value = ["origin"])],
)
data class KnowledgeRelationEntity(
    val fromKnowledgeId: String,
    val toKnowledgeId: String,
    val type: String,
    val weight: Double,
    val origin: String,
)

@Entity(
    tableName = "node_knowledge_refs",
    primaryKeys = ["nodeId", "knowledgePointId", "role"],
    indices = [Index(value = ["knowledgePointId"])],
)
data class NodeKnowledgeRefEntity(
    val nodeId: String,
    val knowledgePointId: String,
    val role: String,
    val orderIndex: Int,
)

@Entity(
    tableName = "learning_resources",
    indices = [Index(value = ["subjectId"]), Index(value = ["type"]), Index(value = ["legacyTextbookKey"]), Index(value = ["origin"])],
)
data class LearningResourceEntity(
    @androidx.room.PrimaryKey val id: String,
    val subjectId: String,
    val type: String,
    val title: String,
    val uri: String?,
    val publisher: String?,
    val edition: String?,
    val legacyTextbookKey: String?,
    val metadataJson: String,
    val origin: String,
)

@Entity(
    tableName = "resource_bindings",
    indices = [Index(value = ["resourceId"]), Index(value = ["nodeId"]), Index(value = ["knowledgePointId"])],
)
data class ResourceBindingEntity(
    @androidx.room.PrimaryKey val id: String,
    val resourceId: String,
    val nodeId: String?,
    val knowledgePointId: String?,
    val role: String,
    val pageStart: Int?,
    val pageEnd: Int?,
    val orderIndex: Int,
)

@Entity(
    tableName = "knowledge_mastery",
    primaryKeys = ["subjectId", "knowledgePointId"],
    indices = [Index(value = ["dueAt"]), Index(value = ["updatedAt"])],
)
data class KnowledgeMasteryEntity(
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

@Entity(
    tableName = "curriculum_node_progress",
    indices = [Index(value = ["status"]), Index(value = ["lastVisitedAt"])],
)
data class CurriculumNodeProgressEntity(
    @androidx.room.PrimaryKey val nodeId: String,
    val status: String,
    val lastVisitedAt: Long,
    val completedAt: Long?,
)

internal fun SubjectDefinition.toEntity(): SubjectEntity = SubjectEntity(
    id = id,
    title = title,
    category = category.name,
    description = description,
    capabilityIds = capabilityIds.joinToString(",") { it.name },
    stageIds = stageIds.joinToString(","),
    orderIndex = orderIndex,
)

internal fun SubjectEntity.toDomain(): SubjectDefinition = SubjectDefinition(
    id = id,
    title = title,
    category = enumValueOrDefault(category, SubjectCategory.OTHER),
    description = description,
    capabilityIds = capabilityIds.csv().mapNotNull { enumValueOrNull<LearningCapability>(it) }.toSet(),
    stageIds = stageIds.csv().toSet(),
    orderIndex = orderIndex,
)

internal fun LearningLevelSystem.toEntity() = LearningLevelSystemEntity(id, title, description)
internal fun LearningLevelSystemEntity.toDomain() = LearningLevelSystem(id, title, description)

internal fun LearningLevel.toEntity() = LearningLevelEntity(id, systemId, parentId, title, orderIndex, legacyGrade)
internal fun LearningLevelEntity.toDomain() = LearningLevel(id, systemId, parentId, title, orderIndex, legacyGrade)

internal fun Curriculum.toEntity() = CurriculumEntity(
    id, subjectId, title, levelSystemId, standard, region, version, source.name, orderIndex,
)

internal fun CurriculumEntity.toDomain() = Curriculum(
    id, subjectId, title, levelSystemId, standard, region, version,
    enumValueOrDefault(source, CurriculumSource.USER), orderIndex,
)

internal fun CurriculumNode.toEntity() = CurriculumNodeEntity(
    id, curriculumId, parentId, type.name, title, orderIndex, legacyLessonId, metadata.toJsonString(),
)

internal fun CurriculumNodeEntity.toDomain() = CurriculumNode(
    id, curriculumId, parentId, enumValueOrDefault(type, CurriculumNodeType.TOPIC), title,
    orderIndex, legacyLessonId, metadataJson.toStringMap(),
)

internal fun KnowledgePoint.toEntity() = KnowledgePointEntity(
    id, subjectId, title, description, kind.name, evaluatorId, metadata.toJsonString(),
)

internal fun KnowledgePointEntity.toDomain() = KnowledgePoint(
    id, subjectId, title, description, enumValueOrDefault(kind, KnowledgeKind.CONCEPT), evaluatorId,
    metadataJson.toStringMap(),
)

internal fun KnowledgeRelation.toEntity(origin: String) = KnowledgeRelationEntity(
    fromKnowledgeId, toKnowledgeId, type.name, weight, origin,
)

internal fun KnowledgeRelationEntity.toDomain() = KnowledgeRelation(
    fromKnowledgeId, toKnowledgeId, enumValueOrDefault(type, KnowledgeRelationType.RELATED), weight,
)

internal fun NodeKnowledgeRef.toEntity() = NodeKnowledgeRefEntity(nodeId, knowledgePointId, role.name, orderIndex)
internal fun NodeKnowledgeRefEntity.toDomain() = NodeKnowledgeRef(
    nodeId, knowledgePointId, enumValueOrDefault(role, KnowledgeRole.PRIMARY), orderIndex,
)

internal fun LearningResource.toEntity(origin: String) = LearningResourceEntity(
    id, subjectId, type.name, title, uri, publisher, edition, legacyTextbookKey, metadata.toJsonString(), origin,
)

internal fun LearningResourceEntity.toDomain() = LearningResource(
    id, subjectId, enumValueOrDefault(type, ResourceType.DOCUMENT), title, uri, publisher, edition,
    legacyTextbookKey, metadataJson.toStringMap(),
)

internal fun ResourceBinding.toEntity() = ResourceBindingEntity(
    id, resourceId, nodeId, knowledgePointId, role.name, pageStart, pageEnd, orderIndex,
)

internal fun ResourceBindingEntity.toDomain() = ResourceBinding(
    id, resourceId, nodeId, knowledgePointId, enumValueOrDefault(role, ResourceBindingRole.REFERENCE),
    pageStart, pageEnd, orderIndex,
)

internal fun KnowledgeMastery.toEntity() = KnowledgeMasteryEntity(
    subjectId, knowledgePointId, score, attempts, correctStreak, wrongStreak, lastCorrect, dueAt, updatedAt,
)

internal fun KnowledgeMasteryEntity.toDomain() = KnowledgeMastery(
    subjectId, knowledgePointId, score, attempts, correctStreak, wrongStreak, lastCorrect, dueAt, updatedAt,
)

internal fun CurriculumNodeProgress.toEntity() = CurriculumNodeProgressEntity(
    nodeId, status.name, lastVisitedAt, completedAt,
)

internal fun CurriculumNodeProgressEntity.toDomain() = CurriculumNodeProgress(
    nodeId, enumValueOrDefault(status, CurriculumNodeProgressStatus.NOT_STARTED), lastVisitedAt, completedAt,
)

private fun Map<String, String>.toJsonString(): String = JSONObject().apply {
    forEach { (key, value) -> put(key, value) }
}.toString()

private fun String.toStringMap(): Map<String, String> = runCatching {
    val root = JSONObject(this)
    buildMap {
        root.keys().forEach { key -> put(key, root.optString(key)) }
    }
}.getOrDefault(emptyMap())

private fun String.csv(): List<String> = split(',').map(String::trim).filter(String::isNotEmpty)

private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String): T? =
    runCatching { enumValueOf<T>(raw) }.getOrNull()

private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
    enumValueOrNull<T>(raw) ?: fallback
