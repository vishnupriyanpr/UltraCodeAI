#  ULTRACODEAI -  IntelliJ AI Assistant (Ultra-fast, Local + Cloud LLMs)          

<div align="center">
    
_*An IDE-first AI copilot for IntelliJ IDEA with chat, diagnostics, and project-aware insights.*_ <br></br>
   [![Built on - Python](https://img.shields.io/badge/Platform-IntelliJ--Idea-blue)](#)
   [![Built with Java17](https://img.shields.io/badge/Built-with--Java--17-red)](#)
   ![UI](https://img.shields.io/badge/UI-Swing%20%7C%20JetBrains%20UI-8A2BE2)
   ![License](https://img.shields.io/badge/License-MIT-2F2F2F)

</div>

---

## 🚀 Overview

UltraCodeAI is an IntelliJ IDEA plugin that embeds an AI coding copilot directly in the IDE:
- Multi-model chat with streaming responses
- Real‑time Diagnostics tab powered by IntelliJ inspections
- Project‑aware Insights (RAG-ready structure)
- One clean, native, tabbed tool window: Chat • Diagnostics • Insights

It works with both local models (privacy-first) and cloud models (frontier capabilities), and is built 100% in Java for deep IntelliJ integration.

---
## 🌟 Key Features

| Feature                       | Description                                                                                                                              |
| ----------------------------- | -----------------------------------------------------------------------------------------------------------------------------------------|
| 🤖 **Unified Chat UI**        | A single, tabbed interface for AI chat, code diagnostics, and project insights. No more cluttered windows.                               |
| 🔌 **Multi-LLM Support**      | Connect to **local LLMs** for privacy or **cloud LLMs** for power. Switch between them effortlessly.                                     |
| ⚡ **Streaming Responses**    | Get token-by-token responses for a real-time, interactive chat experience.                                                               |
| 🔍 **Live Code Diagnostics**  | The "Diagnostics" tab automatically analyzes your open file, providing AI-powered explanations and fixes for errors and warnings.        |
| 💡 **Hover-to-Explain**       | Simply hover over any piece of code—a variable, function, or class—to get an instant AI-generated explanation.                           |
| 🧠 **Context-Aware Prompts**  | Easily add files, directories, or code snippets to your prompt context. The AI knows what you're working on.                             |
| 🛠️ **RAG-Enhanced Insights**  | Built with a Retrieval-Augmented Generation architecture to provide project-aware insights and more accurate answers.                    |
| 🎨 **Native Look & Feel**     | A beautiful UI built with JetBrains Swing components that respects your IDE theme and feels like a part of IntelliJ.                     |


---

## Folder hints (typical layout):
```bash
UltraCodeAI 📂/
├── .gradle 📂/                  
├── .idea 📂/                    
├── gradle 📂/                   
├── src 📂/
│   ├── main 📂/
│   │   ├── java 📂/
│   │   │   └── com 📂/
│   │   │       └── ultracodeai/
│   │   │           ├── action/             # IntelliJ actions (e.g., menu items, toolbar buttons)
│   │   │           ├── ui/
│   │   │           │   ├── toolwindow/     # UI for tool windows
│   │   │           │   ├── panel/          # Reusable UI panels
│   │   │           │   ├── settings/       # Settings UI
│   │   │           │   └── component/      # Reusable UI components
│   │   │           ├── service/            # Core business logic
│   │   │           │   ├── chatmodel/      # Abstraction for AI providers (e.g., OpenAI, Ollama)
│   │   │           │   ├── prompt/         # Prompt generation & execution
│   │   │           │   ├── diagnostics/    # Live analysis services
│   │   │           │   ├── rag/            # Retrieval Augmented Generation services
│   │   │           │   └── util/           # General utility classes
│   │   │           ├── listener/           # Event listeners
│   │   │           └── model/              # Data models
│   │   └── resources 📂/
│   │       ├── META-INF/
│   │       │   └── plugin.xml          # Plugin descriptor file
│   │       └── icons/                  # Plugin icons
│   └── test 📂/
│       └── java/
│           └── com/
│               └── ultracodeai/
├── build.gradle              # Gradle build script
└── README.md                 # Project README file
```

---
## 📦 Installation
From source:
```bash
  git clone [https://github.com/vishnupriyanpr/UltraCodeAI.git](https://github.com/vishnupriyanpr/UltraCodeAI.git)
  cd UltraCodeAI
  ./gradlew buildPlugin
```
Install into IDE:
- Open IntelliJ → Settings → Plugins → Install from Disk
- Select zip from `build/distributions/`
Dev run:
```bash
  ./gradlew clean
  ./gradlew buildPlugin
```
Requirements:
- IntelliJ IDEA 2023.3.4+ recommended
- Java 17+
---
## 🚀 **Features & Tech**
---
### Configuration & Setup 🛠️

- **Settings**: UltraCodeAI → provider selection (local/cloud) → API keys → appearance/cost/context preferences → MCP/Web Search/RAG toggles  
- **Access:** : View → Tool Windows → UltraCodeAI
---
### Core Usage 🎯
>**Chat:** Open tool window → type prompt + code context → real-time streaming  
>**Context:** Right-click files → "Add To Conversation" → directory/token calculation  
>**Diagnostics:** Auto-populate from IntelliJ analyzer → click navigation  
>**Insights:** RAG-powered contextual recommendations

---
### Architecture Components 🏢
- `UltraCodeAIToolWindowFactory`:** 3-tab UI + Chat send-button → `PromptExecutionService.executePrompt()`
- `PromptExecutionService`: Command processing → strategy selection → execution → cancellation → cleanup
- `ResponseListener`:** `onTokenReceived` + `onComplete` interface
- `UltraCodeAIDiagnosticsService`: IntelliJ daemon analyzer → Diagnostics tab callback
---
### Extension Points 🔗
**Providers:** Custom local/cloud services (`chatmodel/`) -  **Strategies:** New `PromptExecutionStrategy` variants -  **RAG:** Indexer/retriever for Insights -  **WebView:** JCEF/HTML+Prism rendering -  **Actions:** Editor/project context integration

---
### Roadmap 🗺️

  Click-navigate diagnostics -  AI fix suggestions -  Rich JCEF chat rendering -  Provider presets -  Smart model recommendations -  Full RAG + local embeddings

---

## 🤝 Contributing
PRs welcome! Suggested flow:
1) Fork and branch: `feature/<name>`
2) Code + tests
3) `./gradlew buildPlugin`
4) Open PR with a clear description and screenshots/GIFs for UI changes
shrink it to 3topics and give me it into 3 topiccs
---

## 📜 License

MitLicense-2.0 (see LICENSE in the repo) 

---

## 🙌 Acknowledgments & Core Team

This project is crafted with passion by **MeshMinds**. We are deeply grateful to our core contributors who poured their expertise and dedication into building UltraCodeAI from the ground up.

<table align="center">
  <tr>
    <td align="center">
      <a href="https://github.com/vishnupriyanpr">
        <img src="https://github.com/vishnupriyanpr.png?size=120" width="120px;" alt="Vishnupriyan P R"/>
        <br />
        <sub><b>Vishnupriyan P R</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/Sanjit-123">
        <img src="https://github.com/Sanjit-123.png?size=120" width="120px;" alt="Sanjit M"/>
        <br />
        <sub><b>Sanjit M</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/Vivek-The-Creator">
        <img src="https://github.com/Vivek-The-Creator.png?size=120" width="120px;" alt="Vivek K K"/>
        <br />
        <sub><b>Vivek K K</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/Akshaya1215">
        <img src="https://github.com/Akshaya1215.png?size=120" width="120px;" alt="Akshaya K"/>
        <br />
        <sub><b>Akshaya K</b></sub>
      </a>
    </td>
  </tr>
</table>

---
<div align="center">
  <p><i></> Crafted with passion and Java by the MeshMinds team </></i></p>
</div>


