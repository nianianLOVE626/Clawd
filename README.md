# Clawd Android 2.0 — Operit MCP Body

这一版重点修复 MCP 地址“看起来像真的、实际上无法确认”的问题。

## MCP

- MCP Server 在手机上真实监听 `0.0.0.0:18765`（开启局域网访问时）或 `127.0.0.1:18765`（仅本机）。
- 地址由 Android 运行时读取真实 IPv4 网卡地址，不写死 `192.168.x.x`。
- 设置页会列出检测到的全部局域网 IPv4 地址。
- 增加真实 MCP 自测：`initialize` → `tools/list`。
- `/health` 会返回真实运行状态和地址列表。
- HTTP MCP 支持 CORS、Bearer Token、MCP 协议相关请求头。
- 保留旧版 SSE 兼容入口。

## 权限

仅声明本体需要的权限：悬浮窗、网络、通知、前台服务、Android 14+ specialUse 前台服务、通知读取、唤醒锁。

## 使用

1. 打开 Clawd。
2. 授予悬浮窗权限。
3. 开启“允许局域网访问”（如果 Operit 无法访问本机回环）。
4. 开启桌面 Clawd。
5. 在设置里点击“重新测试 MCP”。
6. 把实际显示的 MCP 地址复制到 Operit 的 Remote MCP。

注意：Clawd 自测只能证明手机自身可以访问该地址，不能百分之百证明 Operit 的 proot/VPN 网络环境可以访问；最终以 Operit 的连接测试为准。


## 2.0 pet/bubble movement
- The Clawd image, speech bubble, and status are children of one transparent overlay root.
- Dragging the character updates the root WindowManager position, so the speech bubble moves with the character.
- The speech bubble is no longer a second fixed-position WindowManager overlay.
- Visual refresh replaces only the character view and reattaches its drag listener.


## 2.1 新增
- 点击 Clawd 打开独立聊天框。
- 聊天框与 Clawd 共享同一悬浮容器，因此随 Clawd 一起移动。
- 聊天框尺寸独立于 Clawd 形象，可用 `− / ＋` 或双指缩放调整大小，不会改变形象大小。
- 新增 MCP 工具 `clawd_open_chat` 与 `clawd_get_last_user_message`，为 Operit 后续轮询用户消息、驱动同一 AI 回复预留接口。
