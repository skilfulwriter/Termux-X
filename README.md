# Termux-X

**基于 ZeroTermux 的深度定制与增强版终端模拟器**

## 📖 项目简介

**Termux-X** 是一款基于 **ZeroTermux** 进行二次开发（二开）的增强版终端模拟器应用。它保留了 ZeroTermux 的强大功能，并在此基础上进行了深度定制与优化，旨在为移动端渗透测试人员和极客提供更便捷、更强大的操作环境。

### 🌟 核心亮点

*   **一键免 Root 运行 Kali NetHunter**：这是 Termux-X 最具杀伤力的功能。用户无需对手机进行 Root 操作，即可通过内置的一键脚本/功能，快速启动并运行完整的 Kali NetHunter 渗透测试环境，极大降低了移动安全审计的门槛。
*   **深度定制 UI**：全新的 Material Design 风格图标与界面设计，不仅美观，更符合操作直觉。
*   **AI 智能助手集成**：内置 AI 辅助功能，支持自然语言转 Shell 命令，不仅能解释报错，还能辅助生成 Payload 和进行代码审计。
*   **丰富工具箱**：预装并集成了 Metasploit, Sqlmap, Seeker 等常用黑客工具的快捷入口。

---

## 🚀 近期更新与修改内容

针对 **Termux-X** 客户端应用进行了深度的功能增强与体验优化：

### 🛠️ 品牌与界面重塑
*   **全局品牌更名**：应用名称正式变更为 **Termux-X**。已完成启动页、设置菜单、关于页面及主界面标题的文本替换。
*   **UI 布局调整**：
    *   **版块重组**：调整了主页功能版块的顺序，将 **X11 功能区** 移动至 **Kali NetHunter** 版块下方，**常用功能**上方。
    *   **界面精简**：优化了 X11 功能区按钮布局，移除了冗余按钮。

### 🖥️ 桌面环境支持
*   **Termux 原生图形化界面**：
    *   **一键启动**：深度集成了 **Termux-X11** 与 **XFCE4** 桌面环境。
    *   **智能联动**：自动唤起 Termux-X11 应用，实现从命令行到图形界面的无缝切换。
*   **Kali NetHunter 图形化桌面**：
    *   **一键 KeX 连接**：内置对 **NetHunter KeX** 的支持，点击即可启动服务并跳转客户端。
    *   **依赖检测**：智能检测 NetHunter KeX 客户端安装状态。

### ⚡ 稳定性与功能增强
*   **会话管理升级**：重构了“会话”按钮交互，支持快捷选择“新建 Termux Shell”或“Kali Shell”。
*   **X11 桌面启动优化**：修复启动脚本竞态条件，增加等待机制；优化命令连接符提高容错率。
*   **进程管理修复**：使用 `pkill` 精准查杀桌面进程，解决残留问题。
*   **崩溃修复**：修复主页初始化空指针异常。

---

## ℹ️ 关于 ZeroTermux (本项目基础)

**ZeroTermux** 是根据 Termux 二次开发的一个非盈利性的软件。Termux-X 继承了 ZeroTermux 的优秀特性。
(ZeroTermux is a non-profit software developed based on Termux.)

### 功能区别 (相比官方 Termux)
1.  **备份恢复**：支持快速备份和恢复容器数据。
2.  **容器切换**：多容器管理功能。
3.  **Linux 发行版**：内置 Ubuntu, Kali 等发行版的一键安装。
4.  **源管理**：内置清华源与北京源切换（针对国内网络环境优化）。

### ⚠️ 免责声明 (Statement)

**ZeroTermux 及 Termux-X 所有功能只能用于个人学习交流使用，不得用于商业用途及非法用途！**

1.  本软件为开源软件，遵循 GPL v2.0 协议。
2.  软件内涉及的图标及字体均来源于互联网（如阿里巴巴矢量图标库），版权归原作者所有。
3.  **风险提示**：软件内使用的恢复包、数据包、工具脚本等均来源于互联网或社区贡献，作者无法控制其内容。使用本软件可能会直接或间接对您的设备造成损害（如数据丢失），请用户自行承担风险和法律责任。
4.  如有侵权请联系删除。

### 🔗 原项目与相关链接
*   **Termux 官方**: [GitHub](https://github.com/termux/termux-app)
*   **ZeroTermux 签名文件**: [GitHub Link](https://github.com/hanxinhao000/Termux-app-UpgradedVersion/tree/master/%E7%AD%BE%E5%90%8D%E6%96%87%E4%BB%B6)
*   **ZeroTermux 下载 (旧版存档)**: [链接](https://od.ixcmstudio.cn/repository/main/ZeroTermux/)

### 🤝 联系方式 (ZeroTermux 社区)
*   潜水群: 248022558 (推荐)
*   ①群: 1062337587
*   ②群: 885832352

---

## 📚 引用与致谢 (Credits)

本项目（及 ZeroTermux）引用了以下优秀的开源项目，感谢所有作者的贡献：

*   [termux-app](https://github.com/termux/termux-app)
*   [termux-tasker](https://github.com/termux/termux-tasker)
*   [termux-api](https://github.com/termux/termux-api)
*   [termux-styling](https://github.com/termux/termux-styling)
*   [termux-packages](https://github.com/termux/termux-packages)
*   [ImagePicker](https://github.com/Lichenwei-Dev/ImagePicker)
*   [android-vshell](https://github.com/BryleHelll/android-vshell)
*   [AgentWeb](https://github.com/Justson/AgentWeb)
*   [XXPermissions](https://github.com/getActivity/XXPermissions)
*   [libaums](https://github.com/magnusja/libaums)
*   [ColorSeekBar](https://github.com/rtugeek/ColorSeekBar)
*   [Glide](https://github.com/bumptech/glide)
*   [ttyd](https://github.com/tsl0922/ttyd)
*   [filebrowser](https://github.com/filebrowser/filebrowser)
*   [ImmersionBar](https://github.com/gyf-dev/ImmersionBar)
*   [FNetServer](https://github.com/570622566/FNetServer)
*   [codeeditor](https://github.com/testica/codeeditor)

*(列表可能不完全，如有遗漏请见谅)*

---

**如果项目对你有用，请支持一下原作者和 Termux 社区！**
