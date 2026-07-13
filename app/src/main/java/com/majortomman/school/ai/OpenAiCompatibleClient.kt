package com.majortomman.school.ai

import com.majortomman.school.data.AiSettings
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class OpenAiCompatibleClient(
    private val settings: AiSettings,
) : AiProvider {
    suspend fun testConnection(): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val connection = openConnection("models", method = "GET")
            val response = connection.readResponse()
            val root = JSONObject(response)
            val models = root.optJSONArray("data")
            val firstModel = models?.optJSONObject(0)?.optString("id").orEmpty()
            if (firstModel.isBlank()) "连接成功" else "连接成功 · $firstModel"
        }
    }

    override suspend fun explain(concept: String, learnerQuestion: String): String = chat(
        system = "你是一个耐心的个人数学老师。先判断学习者卡住的环节，再用直觉、例子和最少必要步骤解释，不要堆砌术语。",
        user = "知识点：$concept\n学习者的问题：$learnerQuestion",
    )

    override suspend fun giveHint(question: String, learnerAnswer: String, level: Int): String = chat(
        system = "你是数学学习应用的提示器。只给第 $level 级提示，不要直接公布完整答案；提示应推动学习者自己完成下一步。",
        user = "题目：$question\n学习者当前答案：$learnerAnswer",
    )

    override suspend fun evaluateAnswer(
        question: String,
        learnerAnswer: String,
    ): AnswerEvaluation {
        val raw = chat(
            system = """
                你负责批改初中数学答案。返回一个 JSON 对象，不要使用 Markdown：
                {"correct":true或false,"feedback":"简洁、具体的中文反馈","mistake_type":"概念不懂/步骤错误/计算错误/表达不完整/无"}
                即使答案错误，也先指出已经做对的部分，再给下一步提示，不要直接给完整答案。
            """.trimIndent(),
            user = "题目：$question\n学习者答案：$learnerAnswer",
        )

        return parseEvaluation(raw)
    }

    private suspend fun chat(system: String, user: String): String = withContext(Dispatchers.IO) {
        require(settings.model.isNotBlank()) { "模型名称不能为空" }

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        val request = JSONObject()
            .put("model", settings.model)
            .put("messages", messages)
            .put("temperature", 0.2)
            .put("stream", false)

        val connection = openConnection("chat/completions", method = "POST")
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(request.toString())
        }
        val response = JSONObject(connection.readResponse())
        response.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    private fun parseEvaluation(raw: String): AnswerEvaluation {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) {
            return AnswerEvaluation(
                correct = false,
                feedback = raw.ifBlank { "模型没有返回可读的批改结果。" },
                mistakeType = null,
            )
        }

        return runCatching {
            val json = JSONObject(raw.substring(start, end + 1))
            AnswerEvaluation(
                correct = json.optBoolean("correct", false),
                feedback = json.optString("feedback", "模型未提供反馈。"),
                mistakeType = json.optString("mistake_type").takeIf { it.isNotBlank() && it != "无" },
            )
        }.getOrElse {
            AnswerEvaluation(correct = false, feedback = raw, mistakeType = null)
        }
    }

    private fun openConnection(path: String, method: String): HttpURLConnection {
        val endpoint = settings.endpoint.trim().trimEnd('/')
        require(endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            "接口地址必须以 http:// 或 https:// 开头"
        }
        val apiBase = if (endpoint.endsWith("/v1")) endpoint else "$endpoint/v1"
        return (URL("$apiBase/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 90_000
            setRequestProperty("Accept", "application/json")
            if (method == "POST") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (settings.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            }
        }
    }

    private fun HttpURLConnection.readResponse(): String {
        val status = responseCode
        val stream = if (status in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            throw IOException("HTTP $status: ${body.take(400).ifBlank { responseMessage }}")
        }
        return body
    }
}
