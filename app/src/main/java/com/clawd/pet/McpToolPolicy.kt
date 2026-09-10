package com.clawd.pet

/** Default-safe policy: memory tools may be used by the companion; other MCP tools are opt-in. */
object McpToolPolicy {
    fun isMemoryTool(name:String):Boolean{
        val n=name.lowercase()
        return n.contains("memory") || n.contains("remember") || n.contains("recall")
    }
    fun allowedByDefault(name:String)=isMemoryTool(name)
}
