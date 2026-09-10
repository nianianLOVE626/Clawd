import SwiftUI
import AVFoundation

struct Message: Identifiable { let id=UUID(); let role:String; let text:String; let voiceURL:URL?=nil }

final class Settings: ObservableObject {
    @Published var chatProvider="custom"
    @Published var apiURL=""; @Published var apiKey=""; @Published var model=""
    @Published var mcpURL=""; @Published var mcpToken=""; @Published var mcpTransport="http"; @Published var sseURL=""
    @Published var ttsProvider="custom_tts"; @Published var ttsURL=""; @Published var ttsKey=""; @Published var ttsModel="gpt-4o-mini-tts"; @Published var voiceID=""; @Published var ttsEnabled=false
}

struct ContentView: View {
    @StateObject private var s=Settings(); @State private var input=""
    @State private var messages:[Message]=[]; @State private var showingSettings=false
    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(colors:[Color(red:1,green:0.97,blue:0.99),Color(red:0.95,green:0.91,blue:0.97)],startPoint:.top,endPoint:.bottom).ignoresSafeArea()
                ScrollView { LazyVStack(alignment:.leading,spacing:12){ ForEach(messages){m in Bubble(message:m,settings:s) } }.padding() }
            }
            .safeAreaInset(edge:.bottom){ HStack{ TextField("和 Clawd 说点什么",text:$input).textFieldStyle(.roundedBorder); Button("发送"){send()} .buttonStyle(.borderedProminent) }.padding() }
            .toolbar{ToolbarItem(placement:.topBarTrailing){Button("设置"){showingSettings=true}}}
            .sheet(isPresented:$showingSettings){SettingsView(settings:s)}
            .navigationTitle("Clawd")
        }
    }
    func send(){ let t=input.trimmingCharacters(in:.whitespacesAndNewlines); guard !t.isEmpty else{return}; messages.append(Message(role:"user",text:t)); input=""; Task { let reply=(try? await ChatAPI.chat(settings:s,text:t)) ?? "我现在没连上 AI"; await MainActor.run{messages.append(Message(role:"assistant",text:reply))} } }
}

struct Bubble: View {
    let message:Message; @ObservedObject var settings:Settings
    @State private var audioURL:URL?; @State private var playing=false
    var body: some View { HStack{ if message.role=="assistant" { content; Spacer() } else { Spacer(); content } }.frame(maxWidth:.infinity) }
    @ViewBuilder var content: some View { VStack(alignment:.leading,spacing:6){ Text(message.role=="assistant" ? "Clawd":"你").font(.caption).foregroundStyle(.secondary); Text(message.text); if message.role=="assistant" && settings.ttsEnabled { Button(playing ? "播放中…":"语音气泡"){speak()} .buttonStyle(.bordered) } }.padding(14).background(.white.opacity(0.88),in:RoundedRectangle(cornerRadius:22)) }
    func speak(){ Task { if let data=try? await VoiceAPI.synthesize(settings:s,text:message.text){ let f=FileManager.default.temporaryDirectory.appendingPathComponent("clawd-\(UUID().uuidString).mp3"); try? data.write(to:f); await MainActor.run{audioURL=f; let p=try? AVAudioPlayer(contentsOf:f); p?.play(); playing=true; DispatchQueue.main.asyncAfter(deadline:.now()+30){playing=false} } } } }
}

struct SettingsView: View {
    @ObservedObject var settings:Settings
    var body: some View { Form {
        Section("AI API"){
            Picker("供应商",selection:$settings.chatProvider){
                Text("OpenAI").tag("openai"); Text("Google Gemini").tag("gemini"); Text("DeepSeek").tag("deepseek"); Text("Qwen / 百炼").tag("qwen"); Text("智谱 GLM").tag("zhipu"); Text("Moonshot / Kimi").tag("moonshot"); Text("OpenRouter").tag("openrouter"); Text("SiliconFlow").tag("siliconflow"); Text("MiniMax").tag("minimax"); Text("自定义").tag("custom")
            }
            TextField("API URL",text:$settings.apiURL);SecureField("API Key",text:$settings.apiKey);TextField("Model",text:$settings.model)
        }
        Section("Ombre Brain MCP"){Picker("连接方式",selection:$settings.mcpTransport){Text("HTTP").tag("http");Text("SSE").tag("sse")};TextField("MCP 地址",text:$settings.mcpURL);SecureField("Bearer Token",text:$settings.mcpToken);if settings.mcpTransport=="sse"{TextField("SSE 地址",text:$settings.sseURL)}}
        Section("语音 API"){
            Picker("供应商",selection:$settings.ttsProvider){Text("OpenAI TTS").tag("openai_tts");Text("ElevenLabs").tag("elevenlabs");Text("MiniMax Voice").tag("minimax_tts");Text("自定义 TTS").tag("custom_tts")}
            TextField("Voice API URL",text:$settings.ttsURL);SecureField("Voice API Key",text:$settings.ttsKey);TextField("Voice Model",text:$settings.ttsModel);TextField("voice_id",text:$settings.voiceID);Toggle("启用语音气泡",isOn:$settings.ttsEnabled);Text("voice_id 是必填项。不同 TTS 服务的字段/端点可能不同。",font:.caption).foregroundStyle(.secondary)
        }
        Section("iPhone 说明"){Text("iOS 不允许普通 App 像 Android 悬浮窗一样覆盖其他 App。这里保留完整聊天、MCP、语音气泡和通知能力；桌面悬浮 Clawd 只能在 Android 上实现。")}
    }.navigationTitle("Clawd 设置") }
}

struct ChatAPI {
    static func chat(settings:Settings,text:String) async throws -> String { var u=settings.apiURL; if !u.hasSuffix("/chat/completions"){u += "/chat/completions"}; var r=URLRequest(url:URL(string:u)!);r.httpMethod="POST";r.setValue("application/json",forHTTPHeaderField:"Content-Type");r.setValue("Bearer \(settings.apiKey)",forHTTPHeaderField:"Authorization");r.httpBody=try JSONSerialization.data(withJSONObject:["model":settings.model,"messages":[["role":"user","content":text]],"stream":false]);let(d,_)=try await URLSession.shared.data(for:r);let j=try JSONSerialization.jsonObject(with:d) as! [String:Any];let c=((j["choices"] as! [[String:Any]])[0]["message"] as! [String:Any])["content"] as! String;return c }
}

struct VoiceAPI {
    static func synthesize(settings:Settings,text:String) async throws -> Data {
        var u=settings.ttsURL
        var body:[String:Any]
        if settings.ttsProvider == "elevenlabs" {
            u += u.hasSuffix("/text-to-speech/") ? settings.voiceID : "/text-to-speech/\(settings.voiceID)"
            body=["text":text,"model_id":settings.ttsModel]
        } else {
            if !u.hasSuffix("/audio/speech"){u += "/audio/speech"}
            body=["model":settings.ttsModel,"voice":settings.voiceID,"input":text,"response_format":"mp3"]
        }
        var r=URLRequest(url:URL(string:u)!);r.httpMethod="POST";r.setValue("application/json",forHTTPHeaderField:"Content-Type")
        if !settings.ttsKey.isEmpty { if settings.ttsProvider == "elevenlabs" { r.setValue(settings.ttsKey,forHTTPHeaderField:"xi-api-key") } else { r.setValue("Bearer \(settings.ttsKey)",forHTTPHeaderField:"Authorization") } }
        r.httpBody=try JSONSerialization.data(withJSONObject:body)
        let(d,res)=try await URLSession.shared.data(for:r);guard (res as? HTTPURLResponse)?.statusCode ?? 500 < 300 else{throw URLError(.badServerResponse)};return d
    }
}
