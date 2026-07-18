package com.majortomman.school.learning.language

import java.text.Normalizer
import java.util.Locale

enum class FreeLanguage {
    ENGLISH,
    JAPANESE,
}

enum class SentenceJudgement {
    SUPPORTED_CORRECT,
    SUPPORTED_ISSUES,
    AMBIGUOUS,
    UNSUPPORTED,
}

enum class GrammarRole(val label: String) {
    SUBJECT("主语"),
    TOPIC("主题"),
    PREDICATE("谓语"),
    OBJECT("宾语"),
    COMPLEMENT("补语"),
    MODIFIER("修饰语"),
    PARTICLE("助词"),
    AUXILIARY("助动词"),
    CONNECTOR("连接成分"),
    PUNCTUATION("标点"),
    UNKNOWN("待分析"),
}

data class GrammarSegment(
    val text: String,
    val role: GrammarRole,
    val explanation: String,
)

data class SentenceIssue(
    val code: String,
    val message: String,
    val suggestion: String? = null,
)

data class FreeSentenceAnalysis(
    val language: FreeLanguage,
    val original: String,
    val normalized: String,
    val judgement: SentenceJudgement,
    val segments: List<GrammarSegment>,
    val issues: List<SentenceIssue>,
    val summary: String,
    val limitation: String,
)

object FreeSentenceAnalyzer {
    fun analyze(language: FreeLanguage, sentence: String): FreeSentenceAnalysis = when (language) {
        FreeLanguage.ENGLISH -> EnglishSentenceAnalyzer.analyze(sentence)
        FreeLanguage.JAPANESE -> JapaneseSentenceAnalyzer.analyze(sentence)
    }
}

object EnglishSentenceAnalyzer {
    private val tokenRegex = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?|\\d+(?:\\.\\d+)?|[^\\s]")
    private val punctuation = setOf(".", "!", "?", ",", ";", ":")
    private val terminalPunctuation = setOf(".", "!", "?")
    private val subjectPronouns = setOf("i", "you", "he", "she", "it", "we", "they", "this", "that", "these", "those", "there")
    private val thirdSingularSubjects = setOf("he", "she", "it", "this", "that")
    private val pluralSubjects = setOf("we", "you", "they", "these", "those")
    private val beForms = setOf("am", "is", "are", "was", "were", "be", "been", "being")
    private val auxiliaries = setOf(
        "am", "is", "are", "was", "were", "be", "been", "being",
        "do", "does", "did", "have", "has", "had",
        "can", "could", "may", "might", "must", "shall", "should", "will", "would",
    )
    private val modals = setOf("can", "could", "may", "might", "must", "shall", "should", "will", "would")
    private val determiners = setOf("a", "an", "the", "this", "that", "these", "those", "my", "your", "his", "her", "its", "our", "their", "some", "any", "each", "every")
    private val prepositions = setOf("in", "on", "at", "to", "from", "with", "for", "of", "by", "after", "before", "under", "over", "through", "during", "into", "about", "between")
    private val conjunctions = setOf("and", "but", "or", "because", "although", "if", "when", "while", "so", "that")
    private val commonBaseVerbs = setOf(
        "be", "have", "do", "go", "come", "play", "study", "work", "live", "like", "love", "want", "need", "know", "think", "say", "tell", "see", "look", "watch", "read", "write", "speak", "learn", "teach", "make", "take", "give", "get", "eat", "drink", "run", "walk", "sleep", "sit", "stand", "open", "close", "use", "help", "start", "finish", "become", "feel", "seem", "mean", "keep", "move", "change", "show", "find", "ask", "answer", "call", "try", "leave", "arrive", "build", "buy", "bring", "choose", "draw", "drive", "fall", "hear", "hold", "meet", "pay", "put", "send", "sing", "swim", "wear", "win",
    )
    private val irregularPast = setOf("was", "were", "had", "did", "went", "came", "saw", "said", "told", "made", "took", "gave", "got", "ate", "drank", "ran", "slept", "sat", "stood", "wrote", "spoke", "taught", "felt", "became", "left", "bought", "brought", "chose", "drew", "drove", "fell", "heard", "held", "met", "paid", "put", "sent", "sang", "swam", "wore", "won")
    private val irregularThird = mapOf("have" to "has", "do" to "does", "go" to "goes", "be" to "is")

    fun analyze(sentence: String): FreeSentenceAnalysis {
        val normalized = Normalizer.normalize(sentence, Normalizer.Form.NFKC).replace('’', '\'').trim()
        if (normalized.isEmpty()) return unsupported(sentence, normalized, "请输入英语句子。")
        if (normalized.length > 500) return unsupported(sentence, normalized, "句子超过 500 个字符，超出本地分析器范围。")

        val tokens = tokenRegex.findAll(normalized).map { it.value }.toList()
        if (tokens.none { it.any(Char::isLetter) }) return unsupported(sentence, normalized, "没有识别到英语单词。")
        val words = tokens.filterNot { it in punctuation }
        val lower = words.map { it.lowercase(Locale.ROOT) }
        val issues = mutableListOf<SentenceIssue>()

        val firstLetter = normalized.firstOrNull(Char::isLetter)
        if (firstLetter != null && firstLetter.isLowerCase()) {
            issues += SentenceIssue("capitalization", "英语句首通常需要大写。", normalized.replaceFirstChar { it.uppercase() })
        }
        if (tokens.lastOrNull() !in terminalPunctuation) {
            issues += SentenceIssue("terminal_punctuation", "完整陈述句或问句通常需要句末标点。", "$normalized.")
        }

        val predicateWordIndex = findPredicateIndex(lower)
        if (predicateWordIndex < 0) {
            return FreeSentenceAnalysis(
                language = FreeLanguage.ENGLISH,
                original = sentence,
                normalized = normalized,
                judgement = SentenceJudgement.AMBIGUOUS,
                segments = words.map { GrammarSegment(it, GrammarRole.UNKNOWN, "未能可靠确定该词在句中的作用。") },
                issues = issues + SentenceIssue("predicate_missing", "没有识别到可确定的谓语。", "检查是否缺少 be、助动词或实义动词。"),
                summary = "已完成分词，但句法中心不明确。",
                limitation = ENGLISH_LIMITATION,
            )
        }

        val questionAuxiliaryFirst = lower.firstOrNull() in auxiliaries && predicateWordIndex == 0 && lower.size > 1
        val imperative = predicateWordIndex == 0 && lower.firstOrNull() in commonBaseVerbs
        val subjectRange = when {
            questionAuxiliaryFirst -> 1 until findQuestionMainVerb(lower).coerceAtLeast(2)
            imperative -> IntRange.EMPTY
            else -> 0 until predicateWordIndex
        }
        val subjectWords = subjectRange.mapNotNull(words::getOrNull).filterNot { it.lowercase(Locale.ROOT) in conjunctions }
        val subjectHead = subjectWords.firstOrNull()?.lowercase(Locale.ROOT)

        checkBeAgreement(lower, subjectHead, issues)
        checkDoSupport(lower, issues)
        checkSimplePresentAgreement(lower, predicateWordIndex, subjectHead, issues)
        checkArticles(lower, words, issues)

        val segments = buildEnglishSegments(words, lower, predicateWordIndex, questionAuxiliaryFirst, imperative)
        val hasComplexConnector = lower.any { it in setOf("although", "unless", "whereas", "whenever", "who", "which", "whose") }
        val judgement = when {
            issues.isNotEmpty() -> SentenceJudgement.SUPPORTED_ISSUES
            hasComplexConnector -> SentenceJudgement.AMBIGUOUS
            else -> SentenceJudgement.SUPPORTED_CORRECT
        }
        val summary = when (judgement) {
            SentenceJudgement.SUPPORTED_CORRECT -> "在当前规则覆盖的句型中，未发现明确的结构、词序或一致性错误。"
            SentenceJudgement.SUPPORTED_ISSUES -> "识别到 ${issues.size} 个可解释问题；请按问题逐项修改。"
            SentenceJudgement.AMBIGUOUS -> "识别到基本结构，但复杂从句需要更强语法模型或教材规则确认。"
            SentenceJudgement.UNSUPPORTED -> "当前句子超出本地分析器范围。"
        }
        return FreeSentenceAnalysis(
            language = FreeLanguage.ENGLISH,
            original = sentence,
            normalized = normalized,
            judgement = judgement,
            segments = segments,
            issues = issues,
            summary = summary,
            limitation = ENGLISH_LIMITATION,
        )
    }

    private fun findPredicateIndex(words: List<String>): Int {
        words.forEachIndexed { index, word ->
            if (word in auxiliaries) return index
            if (isVerbForm(word)) return index
        }
        return -1
    }

    private fun findQuestionMainVerb(words: List<String>): Int =
        words.indices.drop(1).firstOrNull { isVerbForm(words[it]) || words[it] in auxiliaries } ?: 2

    private fun isVerbForm(word: String): Boolean {
        if (word in commonBaseVerbs || word in irregularPast || word in irregularThird.values) return true
        if (word.endsWith("ing") && word.length > 4) return true
        if (word.endsWith("ed") && word.length > 3) return true
        if (word.endsWith("ies") && word.length > 4) return true
        return commonBaseVerbs.any { base -> thirdPerson(base) == word || regularPast(base) == word }
    }

    private fun checkBeAgreement(words: List<String>, subject: String?, issues: MutableList<SentenceIssue>) {
        val be = words.firstOrNull { it in setOf("am", "is", "are") } ?: return
        val expected = when (subject) {
            "i" -> "am"
            in thirdSingularSubjects -> "is"
            in pluralSubjects -> "are"
            else -> null
        }
        if (expected != null && be != expected) {
            issues += SentenceIssue("be_agreement", "主语 $subject 与 be 动词 $be 不一致。", "这里通常使用 $expected。")
        }
    }

    private fun checkDoSupport(words: List<String>, issues: MutableList<SentenceIssue>) {
        words.forEachIndexed { index, word ->
            if (word !in setOf("do", "does", "did")) return@forEachIndexed
            val next = words.drop(index + 1).firstOrNull { it !in subjectPronouns && it !in determiners } ?: return@forEachIndexed
            if (next in irregularPast || next.endsWith("ed") || next.endsWith("s") && next !in setOf("is", "was")) {
                issues += SentenceIssue("do_base_form", "do/does/did 后的实义动词应使用原形。", "把 $next 改为对应动词原形。")
            }
        }
    }

    private fun checkSimplePresentAgreement(
        words: List<String>,
        predicateIndex: Int,
        subject: String?,
        issues: MutableList<SentenceIssue>,
    ) {
        if (predicateIndex !in words.indices) return
        if (words.take(predicateIndex).any { it in auxiliaries }) return
        val verb = words[predicateIndex]
        val base = baseForm(verb) ?: return
        if (subject in thirdSingularSubjects && verb == base) {
            issues += SentenceIssue("third_person_singular", "第三人称单数主语在一般现在时通常需要动词变化。", "可检查是否应写 ${thirdPerson(base)}。")
        }
        if (subject in pluralSubjects && verb == thirdPerson(base)) {
            issues += SentenceIssue("plural_subject_verb", "复数或 you 主语后的一般现在时动词通常使用原形。", "可改为 $base。")
        }
    }

    private fun checkArticles(words: List<String>, originalWords: List<String>, issues: MutableList<SentenceIssue>) {
        words.forEachIndexed { index, word ->
            if (word !in setOf("a", "an") || index + 1 >= words.size) return@forEachIndexed
            val next = words[index + 1]
            val vowelSoundApproximation = next.firstOrNull() in setOf('a', 'e', 'i', 'o', 'u')
            if (word == "a" && vowelSoundApproximation) {
                issues += SentenceIssue("article_a_an", "a 后面的词以元音字母开头。", "可检查是否应使用 an ${originalWords[index + 1]}。")
            }
            if (word == "an" && !vowelSoundApproximation) {
                issues += SentenceIssue("article_a_an", "an 后面的词未以元音字母开头。", "可检查是否应使用 a ${originalWords[index + 1]}。")
            }
        }
    }

    private fun buildEnglishSegments(
        words: List<String>,
        lower: List<String>,
        predicateIndex: Int,
        question: Boolean,
        imperative: Boolean,
    ): List<GrammarSegment> {
        val result = mutableListOf<GrammarSegment>()
        if (question) {
            result += GrammarSegment(words.first(), GrammarRole.AUXILIARY, "问句前置的助动词或 be 动词。")
            val mainVerb = findQuestionMainVerb(lower)
            if (mainVerb > 1) result += GrammarSegment(words.subList(1, mainVerb).joinToString(" "), GrammarRole.SUBJECT, "问句中的主语。")
            if (mainVerb in words.indices) result += GrammarSegment(words[mainVerb], GrammarRole.PREDICATE, "问句的主要谓语。")
            appendEnglishTail(words, lower, mainVerb + 1, result)
            return result
        }
        if (!imperative && predicateIndex > 0) {
            result += GrammarSegment(words.take(predicateIndex).joinToString(" "), GrammarRole.SUBJECT, "谓语之前的主语或主语短语。")
        } else if (imperative) {
            result += GrammarSegment("(you)", GrammarRole.SUBJECT, "祈使句通常省略主语 you。")
        }
        result += GrammarSegment(words[predicateIndex], if (lower[predicateIndex] in auxiliaries) GrammarRole.AUXILIARY else GrammarRole.PREDICATE, if (lower[predicateIndex] in auxiliaries) "限定时态、语气或否定的助动成分。" else "句子的主要动作或状态。")
        var tailStart = predicateIndex + 1
        if (lower[predicateIndex] in auxiliaries && tailStart < words.size && isVerbForm(lower[tailStart])) {
            result += GrammarSegment(words[tailStart], GrammarRole.PREDICATE, "助动词后的主要谓语。")
            tailStart++
        }
        appendEnglishTail(words, lower, tailStart, result)
        return result
    }

    private fun appendEnglishTail(
        words: List<String>,
        lower: List<String>,
        start: Int,
        result: MutableList<GrammarSegment>,
    ) {
        if (start >= words.size) return
        val firstPreposition = (start until words.size).firstOrNull { lower[it] in prepositions }
        if (firstPreposition == null) {
            result += GrammarSegment(words.drop(start).joinToString(" "), GrammarRole.OBJECT, "谓语后的宾语、表语或补充信息。")
            return
        }
        if (firstPreposition > start) {
            result += GrammarSegment(words.subList(start, firstPreposition).joinToString(" "), GrammarRole.OBJECT, "谓语直接涉及的对象或补语。")
        }
        result += GrammarSegment(words.drop(firstPreposition).joinToString(" "), GrammarRole.MODIFIER, "由介词引导的时间、地点、方向或其他修饰信息。")
    }

    private fun thirdPerson(base: String): String = irregularThird[base] ?: when {
        base.endsWith("y") && base.length > 1 && base[base.lastIndex - 1] !in "aeiou" -> base.dropLast(1) + "ies"
        base.endsWith("s") || base.endsWith("x") || base.endsWith("z") || base.endsWith("ch") || base.endsWith("sh") -> base + "es"
        else -> base + "s"
    }

    private fun regularPast(base: String): String = when {
        base.endsWith("e") -> base + "d"
        base.endsWith("y") && base.length > 1 && base[base.lastIndex - 1] !in "aeiou" -> base.dropLast(1) + "ied"
        else -> base + "ed"
    }

    private fun baseForm(word: String): String? {
        if (word in commonBaseVerbs) return word
        irregularThird.entries.firstOrNull { it.value == word }?.let { return it.key }
        commonBaseVerbs.firstOrNull { thirdPerson(it) == word || regularPast(it) == word }?.let { return it }
        return null
    }

    private fun unsupported(original: String, normalized: String, message: String) = FreeSentenceAnalysis(
        language = FreeLanguage.ENGLISH,
        original = original,
        normalized = normalized,
        judgement = SentenceJudgement.UNSUPPORTED,
        segments = emptyList(),
        issues = listOf(SentenceIssue("unsupported", message)),
        summary = message,
        limitation = ENGLISH_LIMITATION,
    )

    private const val ENGLISH_LIMITATION = "本地规则能够解释常见陈述句、问句、祈使句、be/助动词和基础主谓一致；复杂从句、习语、省略、语义选择和全部不规则词形可能需要教材规则或更强语言模型确认。"
}

object JapaneseSentenceAnalyzer {
    private val terminalPunctuation = setOf('。', '！', '？', '.', '!', '?')
    private val particles = listOf("から", "まで", "より", "ので", "のに", "だけ", "しか", "って", "は", "が", "を", "に", "へ", "で", "と", "も", "の", "や")
    private val politeEndings = listOf("ませんでした", "ましょう", "ました", "ません", "でしょう", "ください", "あります", "います", "です", "ます")
    private val casualEndings = listOf("ではない", "じゃない", "だった", "っている", "ている", "ない", "した", "する", "くる", "来る", "行く", "帰る", "だ", "る", "う", "く", "す", "つ", "ぬ", "ぶ", "む", "ぐ", "た")
    private val movementPredicates = listOf("行きます", "行く", "来ます", "来る", "帰ります", "帰る", "向かいます", "向かう")

    fun analyze(sentence: String): FreeSentenceAnalysis {
        val normalized = Normalizer.normalize(sentence, Normalizer.Form.NFKC).trim()
        if (normalized.isEmpty()) return unsupported(sentence, normalized, "日本語の文を入力してください。")
        if (normalized.length > 500) return unsupported(sentence, normalized, "文が 500 文字を超え、端末内解析の範囲外です。")
        if (normalized.none { it in '\u3040'..'\u30ff' || it in '\u4e00'..'\u9fff' }) {
            return unsupported(sentence, normalized, "日本語の文字を確認できません。")
        }

        val issues = mutableListOf<SentenceIssue>()
        if (normalized.lastOrNull() !in terminalPunctuation) {
            issues += SentenceIssue("terminal_punctuation", "文末記号がありません。", "$normalized。")
        }
        val core = normalized.trimEnd(*terminalPunctuation.toCharArray())
        val rawSegments = segmentJapanese(core)
        val predicateText = rawSegments.lastOrNull { !it.isParticle }?.text.orEmpty()
        val predicateRecognized = isPredicate(predicateText)
        if (!predicateRecognized) {
            issues += SentenceIssue("predicate_missing", "文末に確実な述語を確認できません。", "動詞・形容詞・です／だ等の述語を確認してください。")
        }

        val polite = politeEndings.any(predicateText::endsWith)
        val casual = casualEndings.any(predicateText::endsWith)
        if (predicateText.contains("ですます") || predicateText.contains("ますだ") || predicateText.contains("でしただ")) {
            issues += SentenceIssue("mixed_ending", "丁寧体と普通体の語尾が重なっています。", "文末を一つの活用形にしてください。")
        } else if (polite && casual && !predicateText.endsWith("です")) {
            issues += SentenceIssue("mixed_register", "一つの述語で丁寧体と普通体が混在している可能性があります。", "です・ます体か普通体のどちらかにそろえてください。")
        }

        val badParticleSequences = listOf("をは", "がを", "へを", "でを", "をが")
        badParticleSequences.firstOrNull(core::contains)?.let { sequence ->
            issues += SentenceIssue("particle_sequence", "助詞「$sequence」の並びは現在の規則では不自然です。", "各語が担う関係を確認してください。")
        }
        val movement = movementPredicates.any(predicateText::endsWith)
        if (movement && rawSegments.any { it.isParticle && it.text == "を" }) {
            issues += SentenceIssue("movement_particle", "移動先を表す基本文型では「を」より「に／へ」が適切なことがあります。", "学校に行きます／学校へ行きます、のような関係を確認してください。")
        }
        if ((predicateText.endsWith("です") || predicateText.endsWith("だ")) && rawSegments.any { it.isParticle && it.text == "を" }) {
            issues += SentenceIssue("copula_object", "名詞述語「です／だ」の直前に目的格「を」があるため、文型を確認してください。", "主題の「は」や主格の「が」が必要か確認してください。")
        }

        val segments = buildJapaneseGrammarSegments(rawSegments)
        val hasConnector = rawSegments.any { it.isParticle && it.text in setOf("ので", "のに", "って") }
        val judgement = when {
            issues.any { it.code !in setOf("terminal_punctuation") } -> SentenceJudgement.SUPPORTED_ISSUES
            !predicateRecognized -> SentenceJudgement.AMBIGUOUS
            hasConnector -> SentenceJudgement.AMBIGUOUS
            issues.isNotEmpty() -> SentenceJudgement.SUPPORTED_ISSUES
            else -> SentenceJudgement.SUPPORTED_CORRECT
        }
        val summary = when (judgement) {
            SentenceJudgement.SUPPORTED_CORRECT -> "現在の規則が扱う基本文型では、明確な助詞・述語・文体の問題は見つかりませんでした。"
            SentenceJudgement.SUPPORTED_ISSUES -> "${issues.size} 件の確認点があります。助詞、活用、文体または文末を見直してください。"
            SentenceJudgement.AMBIGUOUS -> "基本要素は分解できましたが、複文や省略を含むため断定できません。"
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

    private data class RawJapaneseSegment(val text: String, val isParticle: Boolean)

    private fun segmentJapanese(text: String): List<RawJapaneseSegment> {
        val result = mutableListOf<RawJapaneseSegment>()
        val buffer = StringBuilder()
        var index = 0
        while (index < text.length) {
            val particle = particles.firstOrNull { text.startsWith(it, index) }
            if (particle != null) {
                if (buffer.isNotEmpty()) {
                    result += RawJapaneseSegment(buffer.toString(), false)
                    buffer.clear()
                }
                result += RawJapaneseSegment(particle, true)
                index += particle.length
            } else {
                buffer.append(text[index])
                index++
            }
        }
        if (buffer.isNotEmpty()) result += RawJapaneseSegment(buffer.toString(), false)
        return result
    }

    private fun isPredicate(text: String): Boolean {
        if (text.isBlank()) return false
        if (politeEndings.any(text::endsWith)) return true
        if (casualEndings.any(text::endsWith)) return true
        if (text.endsWith("い") && text.length >= 2) return true
        return false
    }

    private fun buildJapaneseGrammarSegments(raw: List<RawJapaneseSegment>): List<GrammarSegment> {
        val result = mutableListOf<GrammarSegment>()
        var index = 0
        while (index < raw.size) {
            val current = raw[index]
            if (!current.isParticle && index + 1 < raw.size && raw[index + 1].isParticle) {
                val particle = raw[index + 1].text
                val role = when (particle) {
                    "は", "も" -> GrammarRole.TOPIC
                    "が" -> GrammarRole.SUBJECT
                    "を" -> GrammarRole.OBJECT
                    "に", "へ", "で", "から", "まで", "より" -> GrammarRole.MODIFIER
                    "の" -> GrammarRole.MODIFIER
                    "と", "や" -> GrammarRole.CONNECTOR
                    else -> GrammarRole.UNKNOWN
                }
                val explanation = when (particle) {
                    "は" -> "話題として提示される成分。助詞の「は」は通常「わ」と読みます。"
                    "が" -> "動作・状態の主体や新情報を示す成分。"
                    "を" -> "動作が直接及ぶ対象を示す成分。"
                    "に" -> "到達点、時点、存在場所、相手などを示す成分。"
                    "へ" -> "移動の方向を示す成分。助詞の「へ」は通常「え」と読みます。"
                    "で" -> "動作の場所、手段、材料などを示す成分。"
                    "の" -> "所属、修飾、名詞同士の関係を示す成分。"
                    else -> "文中の語句同士の関係を示す成分。"
                }
                result += GrammarSegment(current.text, role, explanation)
                result += GrammarSegment(particle, GrammarRole.PARTICLE, "直前の語句と後続成分の関係を示す助詞。")
                index += 2
                continue
            }
            if (current.isParticle) {
                result += GrammarSegment(current.text, GrammarRole.PARTICLE, "前後の語句の関係を示す助詞。")
            } else {
                val lastContent = raw.drop(index + 1).none { !it.isParticle }
                result += GrammarSegment(
                    current.text,
                    if (lastContent) GrammarRole.PREDICATE else GrammarRole.UNKNOWN,
                    if (lastContent) "文末で動作、状態、判断を表す述語候補。" else "助詞との対応から役割を確定できない成分。",
                )
            }
            index++
        }
        return result
    }

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

    private const val JAPANESE_LIMITATION = "端末内規則は基本的な助詞、述語、丁寧体／普通体、移動文型と語順を説明します。省略された主語、複雑な連体修飾、慣用表現、文脈依存の助詞選択、全ての活用と意味上の自然さは教材規則またはより強い言語モデルで確認する必要があります。"
}
