package com.majortomman.school.learning.language

import java.text.Normalizer

object JapaneseSentenceAnalyzer {
    private val morphologicalAnalyzer: JapaneseMorphologicalAnalyzer = LuceneKuromojiMorphologicalAnalyzer()
    private val terminalPunctuation = setOf('。', '！', '？', '.', '!', '?')
    private val movementPredicates = setOf("行く", "来る", "帰る", "向かう")
    private val copulas = setOf("です", "だ")
    private val complexConnectors = setOf("ので", "のに", "って", "ながら", "けれど", "けど")

    fun analyze(sentence: String): FreeSentenceAnalysis {
        val normalized = Normalizer.normalize(sentence, Normalizer.Form.NFKC).trim()
        if (normalized.isEmpty()) return unsupported(sentence, normalized, "日本語の文を入力してください。")
        if (normalized.length > 500) return unsupported(sentence, normalized, "文が 500 文字を超え、端末内解析の範囲外です。")
        if (normalized.none { it in '\u3040'..'\u30ff' || it in '\u4e00'..'\u9fff' }) {
            return unsupported(sentence, normalized, "日本語の文字を確認できません。")
        }

        val morphemes = morphologicalAnalyzer.analyze(normalized)
        if (morphemes.isEmpty()) return unsupported(sentence, normalized, "Kuromoji が形態素を返しませんでした。")

        val lexical = morphemes.filterNot(::isPunctuation)
        val issues = mutableListOf<SentenceIssue>()
        if (normalized.lastOrNull() !in terminalPunctuation) {
            issues += SentenceIssue("terminal_punctuation", "文末記号がありません。", "$normalized。")
        }

        val predicateStart = findPredicateStart(lexical)
        val predicateEnd = predicateEndIndex(lexical, predicateStart)
        if (predicateStart < 0) {
            issues += SentenceIssue(
                "predicate_missing",
                "Kuromoji の品詞・活用情報から述語を確認できません。",
                "動詞、形容詞、または「です／だ」を含む述語を確認してください。",
            )
        }

        detectIncompatibleAuxiliaries(lexical, predicateStart, predicateEnd, issues)
        detectMovementParticleIssue(lexical, predicateStart, issues)
        detectCopulaObjectIssue(lexical, predicateStart, issues)

        val hasComplexConnector = lexical.any {
            it.surface in complexConnectors ||
                it.partOfSpeech.orEmpty().contains("接続助詞") ||
                it.partOfSpeech.orEmpty().startsWith("接続詞")
        }
        val segments = buildGrammarSegments(morphemes, lexical, predicateStart, predicateEnd)
        val judgement = when {
            predicateStart < 0 -> SentenceJudgement.AMBIGUOUS
            issues.any { it.code != "terminal_punctuation" } -> SentenceJudgement.SUPPORTED_ISSUES
            hasComplexConnector -> SentenceJudgement.AMBIGUOUS
            issues.isNotEmpty() -> SentenceJudgement.SUPPORTED_ISSUES
            else -> SentenceJudgement.SUPPORTED_CORRECT
        }
        val summary = when (judgement) {
            SentenceJudgement.SUPPORTED_CORRECT -> "Kuromoji の形態素・品詞・原形・活用情報に基づき、基本構造に明確な問題は見つかりませんでした。"
            SentenceJudgement.SUPPORTED_ISSUES -> "${issues.size} 件の確認点があります。Kuromoji の解析結果と教材文型を照合してください。"
            SentenceJudgement.AMBIGUOUS -> "形態素解析は完了しましたが、複文、省略または文脈依存のため構文判断を確定できません。"
            SentenceJudgement.UNSUPPORTED -> "現在の端末内解析では扱えません。"
        }
        return FreeSentenceAnalysis(
            language = FreeLanguage.JAPANESE,
            original = sentence,
            normalized = normalized,
            judgement = judgement,
            segments = segments,
            issues = issues,
            summary = summary,
            limitation = JAPANESE_LIMITATION,
        )
    }

    private fun findPredicateStart(tokens: List<JapaneseMorpheme>): Int {
        var cursor = tokens.lastIndex
        while (cursor >= 0 && isAuxiliary(tokens[cursor])) cursor--
        if (cursor >= 0 && isLexicalPredicate(tokens[cursor])) return cursor
        return tokens.indexOfLast { (it.baseForm ?: it.surface) in copulas }
    }

    private fun predicateEndIndex(tokens: List<JapaneseMorpheme>, predicateStart: Int): Int {
        if (predicateStart !in tokens.indices) return -1
        var end = predicateStart
        while (end + 1 < tokens.size && isAuxiliary(tokens[end + 1])) end++
        return end
    }

    private fun detectIncompatibleAuxiliaries(
        tokens: List<JapaneseMorpheme>,
        predicateStart: Int,
        predicateEnd: Int,
        issues: MutableList<SentenceIssue>,
    ) {
        if (predicateStart !in tokens.indices || predicateEnd !in tokens.indices) return
        val predicate = tokens.subList(predicateStart, predicateEnd + 1)
        val incompatible = predicate.zipWithNext().firstOrNull { (left, right) ->
            val leftBase = left.baseForm ?: left.surface
            val rightBase = right.baseForm ?: right.surface
            leftBase == "です" && rightBase == "ます" ||
                leftBase == "ます" && rightBase == "だ"
        } ?: return
        issues += SentenceIssue(
            "mixed_ending",
            "助動詞「${incompatible.first.surface}${incompatible.second.surface}」の接続を確認してください。",
            "Kuromoji が分けた助動詞の活用と接続を一つの文体にそろえてください。",
        )
    }

    private fun detectMovementParticleIssue(
        tokens: List<JapaneseMorpheme>,
        predicateStart: Int,
        issues: MutableList<SentenceIssue>,
    ) {
        if (predicateStart !in tokens.indices) return
        val predicateBase = tokens[predicateStart].baseForm ?: tokens[predicateStart].surface
        if (predicateBase !in movementPredicates) return
        val beforePredicate = tokens.take(predicateStart)
        val hasObjectParticle = beforePredicate.any { it.surface == "を" && isParticle(it) }
        val hasDestinationParticle = beforePredicate.any { it.surface in setOf("に", "へ") && isParticle(it) }
        if (hasObjectParticle && !hasDestinationParticle) {
            issues += SentenceIssue(
                "movement_particle",
                "移動先を表す文型として解析する場合、「を」ではなく「に／へ」が必要な可能性があります。",
                "学校に行きます／学校へ行きます、のように到達点または方向を確認してください。",
            )
        }
    }

    private fun detectCopulaObjectIssue(
        tokens: List<JapaneseMorpheme>,
        predicateStart: Int,
        issues: MutableList<SentenceIssue>,
    ) {
        if (predicateStart !in tokens.indices) return
        val predicateBase = tokens[predicateStart].baseForm ?: tokens[predicateStart].surface
        if (predicateBase !in copulas) return
        if (tokens.take(predicateStart).any { it.surface == "を" && isParticle(it) }) {
            issues += SentenceIssue(
                "copula_object",
                "名詞述語「です／だ」の前に目的格「を」があります。",
                "主題の「は」や主格の「が」が必要か、または実義動詞が欠けていないか確認してください。",
            )
        }
    }

    private fun buildGrammarSegments(
        all: List<JapaneseMorpheme>,
        lexical: List<JapaneseMorpheme>,
        predicateStart: Int,
        predicateEnd: Int,
    ): List<GrammarSegment> {
        val predicateStartOffset = lexical.getOrNull(predicateStart)?.startOffset ?: -1
        val predicateEndOffset = lexical.getOrNull(predicateEnd)?.endOffset ?: -1
        val result = mutableListOf<GrammarSegment>()

        all.forEachIndexed { index, token ->
            if (isPunctuation(token)) {
                result += GrammarSegment(token.surface, GrammarRole.PUNCTUATION, morphologyExplanation(token, "文の区切りを示す記号。"))
                return@forEachIndexed
            }
            val inPredicate = predicateStartOffset >= 0 &&
                token.startOffset >= predicateStartOffset && token.endOffset <= predicateEndOffset
            if (inPredicate) {
                val role = if (isAuxiliary(token)) GrammarRole.AUXILIARY else GrammarRole.PREDICATE
                val base = token.baseForm?.takeIf { it != token.surface }?.let { "原形 $it。" }.orEmpty()
                val purpose = if (role == GrammarRole.AUXILIARY) "時制、否定、丁寧さなどを担う助動成分。" else "文の動作、状態または判断を担う述語。"
                result += GrammarSegment(token.surface, role, morphologyExplanation(token, "$purpose$base"))
                return@forEachIndexed
            }
            if (isParticle(token)) {
                result += GrammarSegment(token.surface, GrammarRole.PARTICLE, morphologyExplanation(token, particleExplanation(token.surface)))
                return@forEachIndexed
            }

            val next = all.drop(index + 1).firstOrNull { !isPunctuation(it) }
            val role = when {
                next != null && isParticle(next) -> roleForParticle(next.surface)
                token.partOfSpeech.orEmpty().startsWith("接続詞") -> GrammarRole.CONNECTOR
                token.partOfSpeech.orEmpty().startsWith("副詞") -> GrammarRole.MODIFIER
                token.partOfSpeech.orEmpty().startsWith("連体詞") -> GrammarRole.MODIFIER
                token.partOfSpeech.orEmpty().startsWith("形容詞") -> GrammarRole.MODIFIER
                else -> GrammarRole.UNKNOWN
            }
            val explanation = when (role) {
                GrammarRole.TOPIC -> "後続の助詞によって話題として提示される成分。"
                GrammarRole.SUBJECT -> "後続の助詞によって動作・状態の主体として示される成分。"
                GrammarRole.OBJECT -> "後続の助詞によって動作が及ぶ対象として示される成分。"
                GrammarRole.MODIFIER -> "時間、場所、方向、状態または名詞を修飾する成分。"
                GrammarRole.CONNECTOR -> "前後の節や語句を接続する成分。"
                else -> "Kuromoji は形態素を識別しましたが、助詞や述語との関係だけでは役割を確定できません。"
            }
            result += GrammarSegment(token.surface, role, morphologyExplanation(token, explanation))
        }
        return result
    }

    private fun morphologyExplanation(token: JapaneseMorpheme, purpose: String): String {
        val details = buildList {
            token.partOfSpeech?.takeIf(String::isNotBlank)?.let { add("品詞: $it") }
            token.baseForm?.takeIf { it.isNotBlank() && it != token.surface }?.let { add("原形: $it") }
            token.reading?.takeIf(String::isNotBlank)?.let { add("読み: $it") }
            token.inflectionType?.takeIf(String::isNotBlank)?.let { add("活用型: $it") }
            token.inflectionForm?.takeIf(String::isNotBlank)?.let { add("活用形: $it") }
        }
        return if (details.isEmpty()) purpose else "$purpose ${details.joinToString("、")}。"
    }

    private fun roleForParticle(particle: String): GrammarRole = when (particle) {
        "は", "も" -> GrammarRole.TOPIC
        "が" -> GrammarRole.SUBJECT
        "を" -> GrammarRole.OBJECT
        "に", "へ", "で", "から", "まで", "より", "の" -> GrammarRole.MODIFIER
        "と", "や" -> GrammarRole.CONNECTOR
        else -> GrammarRole.UNKNOWN
    }

    private fun particleExplanation(particle: String): String = when (particle) {
        "は" -> "話題を示す係助詞。通常は「わ」と読みます。"
        "が" -> "主体や新情報を示す格助詞。"
        "を" -> "動作が直接及ぶ対象を示す格助詞。"
        "に" -> "到達点、時点、存在場所、相手などを示す格助詞。"
        "へ" -> "移動方向を示す格助詞。通常は「え」と読みます。"
        "で" -> "動作場所、手段、材料などを示す格助詞。"
        "の" -> "所属、修飾、名詞同士の関係を示す助詞。"
        else -> "語句同士の文法関係を示す助詞。"
    }

    private fun isLexicalPredicate(token: JapaneseMorpheme): Boolean {
        val partOfSpeech = token.partOfSpeech.orEmpty()
        return partOfSpeech.startsWith("動詞") || partOfSpeech.startsWith("形容詞")
    }

    private fun isParticle(token: JapaneseMorpheme): Boolean = token.partOfSpeech.orEmpty().startsWith("助詞")

    private fun isAuxiliary(token: JapaneseMorpheme): Boolean = token.partOfSpeech.orEmpty().startsWith("助動詞")

    private fun isPunctuation(token: JapaneseMorpheme): Boolean =
        token.partOfSpeech.orEmpty().startsWith("記号") || token.surface.all { it in terminalPunctuation || it in setOf('、', ',', ';', ':') }

    private fun unsupported(original: String, normalized: String, message: String) = FreeSentenceAnalysis(
        language = FreeLanguage.JAPANESE,
        original = original,
        normalized = normalized,
        judgement = SentenceJudgement.UNSUPPORTED,
        segments = emptyList(),
        issues = listOf(SentenceIssue("unsupported", message)),
        summary = message,
        limitation = JAPANESE_LIMITATION,
    )

    private const val JAPANESE_LIMITATION = "分词、词性、原形、读音和活用全部来自 Apache Lucene Kuromoji。School 只依据这些形态信息解释基础句子结构；省略主语、复杂从句、连体修饰、惯用表达、语义自然度和强上下文依赖仍不能仅靠形态分析确定。"
}
