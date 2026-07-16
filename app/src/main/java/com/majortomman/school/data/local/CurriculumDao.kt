package com.majortomman.school.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CurriculumDao {
    @Query("SELECT * FROM subjects ORDER BY orderIndex, title")
    fun observeSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM learning_level_systems ORDER BY title")
    fun observeLevelSystems(): Flow<List<LearningLevelSystemEntity>>

    @Query("SELECT * FROM learning_levels ORDER BY systemId, orderIndex, title")
    fun observeLevels(): Flow<List<LearningLevelEntity>>

    @Query("SELECT * FROM curricula ORDER BY orderIndex, title")
    fun observeCurricula(): Flow<List<CurriculumEntity>>

    @Query("SELECT * FROM curriculum_nodes ORDER BY curriculumId, parentId, orderIndex, title")
    fun observeNodes(): Flow<List<CurriculumNodeEntity>>

    @Query("SELECT * FROM knowledge_points ORDER BY subjectId, title")
    fun observeKnowledgePoints(): Flow<List<KnowledgePointEntity>>

    @Query("SELECT * FROM knowledge_relations ORDER BY toKnowledgeId, weight DESC")
    fun observeKnowledgeRelations(): Flow<List<KnowledgeRelationEntity>>

    @Query("SELECT * FROM node_knowledge_refs ORDER BY nodeId, orderIndex")
    fun observeNodeKnowledgeRefs(): Flow<List<NodeKnowledgeRefEntity>>

    @Query("SELECT * FROM learning_resources ORDER BY subjectId, title")
    fun observeResources(): Flow<List<LearningResourceEntity>>

    @Query("SELECT * FROM resource_bindings ORDER BY resourceId, orderIndex")
    fun observeResourceBindings(): Flow<List<ResourceBindingEntity>>

    @Query("SELECT * FROM curriculum_node_progress")
    fun observeNodeProgress(): Flow<List<CurriculumNodeProgressEntity>>

    @Query("SELECT * FROM knowledge_mastery WHERE subjectId = :subjectId ORDER BY score, dueAt")
    fun observeKnowledgeMastery(subjectId: String): Flow<List<KnowledgeMasteryEntity>>

    @Query("SELECT * FROM knowledge_mastery ORDER BY subjectId, score, dueAt")
    fun observeAllKnowledgeMastery(): Flow<List<KnowledgeMasteryEntity>>

    @Query("SELECT * FROM knowledge_mastery WHERE subjectId = :subjectId AND knowledgePointId = :knowledgePointId LIMIT 1")
    suspend fun getKnowledgeMastery(subjectId: String, knowledgePointId: String): KnowledgeMasteryEntity?

    @Query("SELECT * FROM curriculum_nodes WHERE legacyLessonId = :legacyLessonId LIMIT 1")
    suspend fun getNodeByLegacyLessonId(legacyLessonId: String): CurriculumNodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubjects(items: List<SubjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLevelSystems(items: List<LearningLevelSystemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLevels(items: List<LearningLevelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCurricula(items: List<CurriculumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNodes(items: List<CurriculumNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnowledgePoints(items: List<KnowledgePointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnowledgeRelations(items: List<KnowledgeRelationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNodeKnowledgeRefs(items: List<NodeKnowledgeRefEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResources(items: List<LearningResourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResourceBindings(items: List<ResourceBindingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnowledgeMastery(item: KnowledgeMasteryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNodeProgress(item: CurriculumNodeProgressEntity)

    @Query("DELETE FROM resource_bindings WHERE resourceId IN (SELECT id FROM learning_resources WHERE origin = 'MATERIAL')")
    suspend fun deleteMaterialResourceBindings()

    @Query("DELETE FROM learning_resources WHERE origin = 'MATERIAL'")
    suspend fun deleteMaterialResources()

    @Query("DELETE FROM node_knowledge_refs WHERE nodeId IN (SELECT id FROM curriculum_nodes WHERE curriculumId IN (SELECT id FROM curricula WHERE source = 'MATERIAL'))")
    suspend fun deleteMaterialNodeKnowledgeRefs()

    @Query("DELETE FROM curriculum_nodes WHERE curriculumId IN (SELECT id FROM curricula WHERE source = 'MATERIAL')")
    suspend fun deleteMaterialNodes()

    @Query("DELETE FROM curricula WHERE source = 'MATERIAL'")
    suspend fun deleteMaterialCurricula()

    @Query("DELETE FROM knowledge_relations WHERE origin = 'MATERIAL'")
    suspend fun deleteMaterialKnowledgeRelations()

    @Query("DELETE FROM curriculum_node_progress WHERE nodeId NOT IN (SELECT id FROM curriculum_nodes)")
    suspend fun deleteOrphanNodeProgress()

    @Query("DELETE FROM knowledge_mastery")
    suspend fun clearKnowledgeMastery()

    @Query("DELETE FROM curriculum_node_progress")
    suspend fun clearNodeProgress()
}
