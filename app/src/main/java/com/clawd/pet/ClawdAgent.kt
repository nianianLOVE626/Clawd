package com.clawd.pet

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

object ClawdAgent {
    private val io = Executors.newCachedThreadPool()

    fun discover(): Result<List<McpTool>> = runCatching {
        OmbreMcpClient.initialize().getOrThrow()
        OmbreMcpClient.listTools().getOrThrow()
    }

    fun useOmbreTool(name: String, args: JSONObject, callback: (Result<String>) -> Unit) {
        io.execute { callback(OmbreMcpClient.callTool(name, args)) }
    }

    /**
     * 工具匹配策略：先精确匹配已知 MCP 记忆服务的工具名，
     * 匹配不到再回退到通用模糊匹配，兼容未知的第三方记忆服务。
     */
    private fun findMemoryTool(tools: List<McpTool>, write: Boolean): McpTool? {
        val names = tools.map { it.name }

        // 第一优先级：已知服务的精确工具名
        val knownExact = if (write)
            listOf("hold", "grow", "comment_bucket")        // Ombre Brain
        else
            listOf("breath", "read_bucket", "pulse")         // Ombre Brain

        knownExact.firstNotNullOfOrNull { key ->
            tools.firstOrNull { it.name == key }
        }?.let { return it }

        // 第二优先级：通用记忆服务的常见命名
        val genericExact = if (write)
            listOf("memory_write", "remember", "save_memory", "memory_save", "write_memory", "store_memory", "add_memory")
        else
            listOf("memory_search", "search_memory", "memory_read", "recall", "retrieve_memory", "find_memory")

        genericExact.firstNotNullOfOrNull { key ->
            tools.firstOrNull { it.name.equals(key, true) }
        }?.let { return it }

        // 第三优先级：模糊匹配兜底
        return tools.firstOrNull {
            val n = it.name.lowercase()
            if (write) (n.contains("memory") && (n.contains("write") || n.contains("save") || n.contains("add") || n.contains("remember") || n.contains("store")))
            else (n.contains("memory") && (n.contains("search") || n.contains("read") || n.contains("recall") || n.contains("find") || n.contains("retrieve")))
        }
    }

    /**
     * 根据匹配到的工具自动构建参数。
     * 已知工具用精确参数名，未知工具回退到 schema 推断。
     */
    private fun buildArgs(tool: McpTool, text: String, write: Boolean): JSONObject {
        // Ombre Brain 精确参数
        when (tool.name) {
            "breath" -> return JSONObject().put("query", text)
            "hold" -> return JSONObject().put("content", text)
            "grow" -> return JSONObject().put("content", text)
            "read_bucket" -> return JSONObject().put("bucket_id", text)
            "comment_bucket" -> return JSONObject().put("content", text)
        }
        // 通用 schema 推断（原逻辑）
        val schema = tool.inputSchema
        val props = schema.optJSONObject("properties")
            ?: return JSONObject().put("text", text).put("content", text).put("query", text)
        val out = JSONObject()
        val keys = props.keys()
        while (keys.hasNext()) {
            when (val k = keys.next()) {
                "query", "q", "search", "text", "content", "memory", "message", "input", "value", "fact" -> out.put(k, text)
            }
        }
        if (out.length() == 0) {
            val required = schema.optJSONArray("required")
            if (required != null && required.length() > 0) out.put(required.optString(0), text)
            else out.put("text", text)
        }
        return out
    }

    fun searchMemory(query: String, callback: (Result<String>) -> Unit) {
        io.execute {
            val tools = discover().getOrElse { callback(Result.failure(it)); return@execute }
            val t = findMemoryTool(tools, false) ?: run {
                callback(Result.failure(IllegalStateException("MCP 服务中没有找到记忆搜索工具")))
                return@execute
            }
            callback(OmbreMcpClient.callTool(t.name, buildArgs(t, query, false)))
        }
    }

    fun remember(text: String, callback: (Result<String>) -> Unit) {
        io.execute {
            val tools = discover().getOrElse { callback(Result.failure(it)); return@execute }
            val t = findMemoryTool(tools, true) ?: run {
                callback(Result.failure(IllegalStateException("MCP 服务中没有找到记忆写入工具")))
                return@execute
            }
            callback(OmbreMcpClient.callTool(t.name, buildArgs(t, text, true)))
        }
    }

    fun toolCatalog(): Result<String> = runCatching {
        val tools = discover().getOrThrow()
        JSONArray().apply {
            tools.forEach {
                put(JSONObject()
                    .put("name", it.name)
                    .put("description", it.description)
                    .put("inputSchema", it.inputSchema))
            }
        }.toString()
    }
}