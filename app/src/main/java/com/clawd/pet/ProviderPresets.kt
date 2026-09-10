package com.clawd.pet

data class ProviderPreset(
    val id:String,
    val name:String,
    val apiUrl:String,
    val model:String,
    val note:String,
    val openAiCompatible:Boolean=true
)

object ProviderPresets {
    val chat=listOf(
        ProviderPreset("openai","OpenAI","https://api.openai.com/v1","gpt-4.1","OpenAI 官方 OpenAI-compatible Chat Completions"),
        ProviderPreset("gemini","Google Gemini","https://generativelanguage.googleapis.com/v1beta/openai/","gemini-3.8-flash","Google 官方 OpenAI compatibility",true),
        ProviderPreset("deepseek","DeepSeek","https://api.deepseek.com","deepseek-v4-flash","DeepSeek 官方 OpenAI-compatible API"),
        ProviderPreset("qwen","Qwen / 阿里云百炼","https://dashscope.aliyuncs.com/compatible-mode/v1","qwen-plus","阿里云百炼 OpenAI-compatible API"),
        ProviderPreset("zhipu","智谱 GLM","https://open.bigmodel.cn/api/paas/v4","GLM-4.5","智谱兼容接口；模型可按控制台修改"),
        ProviderPreset("moonshot","Moonshot / Kimi","https://api.moonshot.cn/v1","kimi-k2.5","Kimi OpenAI-compatible API"),
        ProviderPreset("openrouter","OpenRouter","https://openrouter.ai/api/v1","openai/gpt-4.1","多供应商路由，模型 ID 按 OpenRouter 控制台填写"),
        ProviderPreset("siliconflow","SiliconFlow","https://api.siliconflow.cn/v1","deepseek-ai/DeepSeek-V4","OpenAI-compatible 聚合服务"),
        ProviderPreset("minimax","MiniMax","https://api.minimaxi.com/v1","MiniMax-M2.7","MiniMax OpenAI-compatible 路线；具体模型以账号可用列表为准"),
        ProviderPreset("custom","自定义 / OpenAI Compatible","","","手动填写 Base URL、Key 和 Model")
    )

    val voice=listOf(
        ProviderPreset("openai_tts","OpenAI TTS","https://api.openai.com/v1","gpt-4o-mini-tts","通用 OpenAI-compatible /audio/speech；voice_id 填声音名或服务支持的 voice",true),
        ProviderPreset("elevenlabs","ElevenLabs","https://api.elevenlabs.io/v1","eleven_multilingual_v2","需要 voice_id；官方接口把 voice_id 放在路径中",false),
        ProviderPreset("minimax_tts","MiniMax Voice","https://api.minimaxi.com","speech-2.8-hd","MiniMax 语音接口；不同地区/版本的 endpoint 可能不同",false),
        ProviderPreset("custom_tts","自定义 TTS","","","自定义 Voice API；保留 voice_id 字段",true)
    )
}
