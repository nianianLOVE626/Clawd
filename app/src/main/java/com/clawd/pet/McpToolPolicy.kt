package com.clawd.pet

/**
 * MCP 工具权限策略。三层判断：
 * 1. 已知服务（Ombre Brain）的安全工具白名单
 * 2. 通用记忆类工具名模糊匹配
 * 3. 其余工具默认拒绝，需要用户手动开启
 */
object McpToolPolicy {
    // Ombre Brain 桌宠安全工具
    private val OMBRE_SAFE = setOf(
        "breath", "hold", "grow", "read_bucket", "comment_bucket",
        "pulse", "introspection", "list_buckets_light",
        "reminder_list", "reminder_create", "reminder_update"
    )

    fun isMemoryTool(name: String): Boolean {
        // 已知服务精确匹配
        if (name in OMBRE_SAFE) return true
        // 通用模糊匹配
        val n = name.lowercase()
        return n.contains("memory") || n.contains("remember") || n.contains("recall")
    }

    fun allowedByDefault(name: String) = isMemoryTool(name)
}