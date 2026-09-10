# Clawd Android 1.2

本版重点：多 AI / 多 TTS 供应商预设。

## 聊天供应商
内置预设：
- OpenAI
- Google Gemini
- DeepSeek
- Qwen / 阿里云百炼
- 智谱 GLM
- Moonshot / Kimi
- OpenRouter
- SiliconFlow
- MiniMax
- 自定义 OpenAI Compatible

选择供应商后会自动填入推荐 Base URL 和示例模型，仍然可以手动修改。

## 语音供应商
内置：
- OpenAI TTS
- ElevenLabs
- MiniMax Voice
- 自定义 TTS

`voice_id` 永远保留为独立字段。ElevenLabs 会把 voice_id 放入路径并使用 `xi-api-key`，其他通用供应商使用 Bearer API Key。

注意：不同 TTS 供应商的请求体、模型名、voice_id 格式和 endpoint 可能不同；预设是为了减少配置工作，自定义模式仍然可用。

## 依据的官方兼容能力
Google Gemini 提供 OpenAI compatibility；DeepSeek 提供 OpenAI/Anthropic-compatible API；Qwen/阿里云百炼提供 OpenAI-compatible Chat Completions；ElevenLabs 的 TTS 接口使用 voice_id 路径参数和 xi-api-key。
