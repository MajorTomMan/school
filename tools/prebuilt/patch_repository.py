from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def patch(path, old, new, count=1):
    p=ROOT/path
    s=p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'pattern missing in {path}: {old[:100]!r}')
    p.write_text(s.replace(old,new,count),encoding='utf-8')

models=Path('app/src/main/java/com/majortomman/school/data/material/MaterialPackModels.kt')
patch(models, '''enum class TextbookVolume(
    val id: Int,
    val label: String,
) {
    FIRST(1, "上册"),
    SECOND(2, "下册");

    fun labelFor(stage: EducationStage): String = when (stage) {
        EducationStage.PRIMARY, EducationStage.JUNIOR_HIGH -> label
        EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY -> if (this == FIRST) "上学期" else "下学期"
    }
''', '''enum class TextbookVolume(
    val id: Int,
    val label: String,
) {
    FIRST(1, "上册"),
    SECOND(2, "下册"),
    THIRD(3, "第三册");

    fun labelFor(stage: EducationStage): String = when (stage) {
        EducationStage.PRIMARY, EducationStage.JUNIOR_HIGH -> label
        EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY -> when (this) {
            FIRST -> "上学期"
            SECOND -> "下学期"
            THIRD -> "第三册"
        }
    }
''')
patch(models, '''        SubjectTemplate("english", "英语", setOf(EducationStage.PRIMARY, EducationStage.JUNIOR_HIGH, EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY)),
        SubjectTemplate("science", "科学", setOf(EducationStage.PRIMARY)),
''', '''        SubjectTemplate("english", "英语", setOf(EducationStage.PRIMARY, EducationStage.JUNIOR_HIGH, EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY)),
        SubjectTemplate("japanese", "日语", setOf(EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY)),
        SubjectTemplate("science", "科学", setOf(EducationStage.PRIMARY)),
''')
patch(models, '''data class CatalogLesson(
    val id: String,
    val title: String,
    val pageStart: Int,
    val pageEnd: Int,
)
''', '''data class CatalogLesson(
    val id: String,
    val title: String,
    val pageStart: Int,
    val pageEnd: Int,
    val path: List<String> = emptyList(),
)
''')
patch(models, '''    val objectives: List<String>,
    val explanation: String,
    val commonMistake: String,
) {
''', '''    val objectives: List<String>,
    val explanation: String,
    val commonMistake: String,
    val path: List<String> = emptyList(),
) {
''')
patch(models, '''        .put("objectives", JSONArray(objectives))
        .put("explanation", explanation)
        .put("commonMistake", commonMistake)
''', '''        .put("objectives", JSONArray(objectives))
        .put("explanation", explanation)
        .put("commonMistake", commonMistake)
        .put("path", JSONArray(path))
''')
patch(models, '''            explanation = root.getString("explanation"),
            commonMistake = root.getString("commonMistake"),
        )
''', '''            explanation = root.getString("explanation"),
            commonMistake = root.getString("commonMistake"),
            path = root.optJSONArray("path").toStringList(),
        )
''')
patch(models, '''            for (chapterIndex in 0 until chapters.length()) {
                val lessonArray = chapters.getJSONObject(chapterIndex).optJSONArray("lessons") ?: continue
''', '''            for (chapterIndex in 0 until chapters.length()) {
                val chapter = chapters.getJSONObject(chapterIndex)
                val chapterTitle = chapter.optString("title").trim()
                val lessonArray = chapter.optJSONArray("lessons") ?: continue
''')
patch(models, '''                    add(CatalogLesson(id, title, start, end))
''', '''                    val path = lesson.optJSONArray("path").toStringList()
                        .ifEmpty { listOfNotNull(chapterTitle.takeIf(String::isNotBlank)) }
                    add(CatalogLesson(id, title, start, end, path))
''')
patch(models, '''                commonMistake = "不要只记结论。遇到不确定的条件时，先返回$pageLabel，确认教材原文和例题中的适用范围。",
            )
''', '''                commonMistake = "不要只记结论。遇到不确定的条件时，先返回$pageLabel，确认教材原文和例题中的适用范围。",
                path = source.path,
            )
''')

patch(Path('app/src/main/java/com/majortomman/school/data/material/DirectPdfImportScanner.kt'), '''                                        .put("title", lesson.title)
                                        .put("pages", JSONArray().put(lesson.pageStart).put(lesson.pageEnd)),
''', '''                                        .put("title", lesson.title)
                                        .put("pages", JSONArray().put(lesson.pageStart).put(lesson.pageEnd))
                                        .put("path", JSONArray(lesson.path)),
''')

p=ROOT/'app/src/main/java/com/majortomman/school/data/material/MaterialPackRepository.kt'
s=p.read_text(encoding='utf-8')
s=s.replace('BundledMathKnowledgePack.upgradeIfMatched', 'BundledTextbookKnowledgePack.upgradeIfMatched')
s=s.replace('PREBUILT_MATH_VERSION', 'BundledTextbookKnowledgePack.PACK_VERSION')
s=s.replace('''active.lessons.firstOrNull { it.sourceId == lessonSourceId }
            ?.let { lesson -> PrebuiltMathAnalysisFactory.create(active.slot, lesson) }
''','''active.lessons.firstOrNull { it.sourceId == lessonSourceId }
            ?.let { lesson ->
                if (active.slot.subjectId == "math") PrebuiltMathAnalysisFactory.create(active.slot, lesson)
                else PrebuiltSubjectAnalysisFactory.create(active.slot, lesson)
            }
''')
s=s.replace('''
    private companion object {
        const val BundledTextbookKnowledgePack.PACK_VERSION = "prebuilt-math-v1"
    }
''','\n')
p.write_text(s,encoding='utf-8')

repo=Path('app/src/main/java/com/majortomman/school/data/curriculum/CurriculumRepository.kt')
patch(repo, '''                var previousPoint: KnowledgePoint? = null
                textbook.lessons.forEachIndexed { lessonIndex, lesson ->
                    val lessonNodeId = "$curriculumId:lesson:${slot.key}:${BuiltinCurriculumCatalog.stableId(lesson.sourceId)}"
''', '''                var previousPoint: KnowledgePoint? = null
                val hierarchyNodes = mutableMapOf<String, String>()
                textbook.lessons.forEachIndexed { lessonIndex, lesson ->
                    var lessonParentId = courseId
                    lesson.path.forEachIndexed { depth, segment ->
                        val normalizedSegment = segment.trim()
                        if (normalizedSegment.isBlank()) return@forEachIndexed
                        val hierarchyKey = "$lessonParentId:${BuiltinCurriculumCatalog.stableId(normalizedSegment)}"
                        val hierarchyId = hierarchyNodes.getOrPut(hierarchyKey) {
                            "$curriculumId:group:${BuiltinCurriculumCatalog.stableId(slot.key)}:${BuiltinCurriculumCatalog.stableId(hierarchyKey)}"
                        }
                        nodes.putIfAbsent(
                            hierarchyId,
                            CurriculumNode(
                                id = hierarchyId,
                                curriculumId = curriculumId,
                                parentId = lessonParentId,
                                type = when (depth) {
                                    0 -> CurriculumNodeType.UNIT
                                    1 -> CurriculumNodeType.CHAPTER
                                    else -> CurriculumNodeType.MODULE
                                },
                                title = normalizedSegment,
                                orderIndex = lessonIndex,
                            ),
                        )
                        lessonParentId = hierarchyId
                    }
                    val lessonNodeId = "$curriculumId:lesson:${slot.key}:${BuiltinCurriculumCatalog.stableId(lesson.sourceId)}"
''')
patch(repo, '''                        parentId = courseId,
                        type = CurriculumNodeType.LESSON,
''', '''                        parentId = lessonParentId,
                        type = CurriculumNodeType.LESSON,
''')

p=ROOT/'app/build.gradle.kts'; s=p.read_text(encoding='utf-8')
s=s.replace('versionCode = 20','versionCode = 21').replace('versionName = "0.19.1"','versionName = "0.20.0"')
p.write_text(s,encoding='utf-8')

(ROOT/'.release-notes/current.md').write_text('''## 修改点

- 预制教材从十一册数学扩展到四十五册完整教材库，覆盖初中语文与数学，以及高中语文、数学 A 版、英语、日语、物理和化学。
- 新增三十四册非数学教材、共一千零二十七个目录学习节点；原十一册数学继续复用人工校验的二百八十个知识单元。
- APK 只携带教材身份、目录层级、页码范围、知识点、原创讲解和练习骨架，不打包约 1.1 GB 的教材 PDF。
- 未绑定 PDF 时全部课程可直接离线学习；绑定 SHA-256 或文件名匹配的原书后解锁教材原页。
- 教材预制安装器改为全学科统一实现，支持语文、英语、日语、物理、化学和数学。
- 语文使用阅读与表达分析骨架，英语和日语使用语境、词汇、语法与交际任务骨架，物理和化学使用模型、公式、实验与条件骨架。
- 课程目录保留教材书签层级，支持单元、章节、模块和具体课程节点，不再把所有课程压成一层列表。
- 高中教材兼容地址增加第三册，允许英语七册同时存在；必修和选择性必修使用稳定课程编号，不再互相覆盖。
- 旧教材槽位、导入任务和学习记录继续兼容。
- 版本升级到 0.20.0。

## 修复点

- 修复预制机制硬编码数学学科，其他教材即使目录完整也无法免 OCR 安装的问题。
- 修复高中教材只能使用上、下两个槽位，选择性必修会覆盖必修教材的问题。
- 修复教材书签中的单元和章节路径在课程同步时丢失的问题。
- 修复日语未出现在旧教材中心学科列表的问题。
''',encoding='utf-8')
print('repository patches applied')
