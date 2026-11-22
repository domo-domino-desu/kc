# 架构说明

本项目采用 Android 官方推荐架构的口径：基础结构是 UI 层 + Data 层；Domain（网域层）不是默认必经层，只在业务链路复杂、足够独立、或需要被多个 UI 场景复用时引入。

默认依赖方向是：

```text
ui -> data

复杂/独立链路：
ui -> domain -> data
```

也就是说，`domain` 不是“简单页面的可选中间层”，而是“只有必要时才出现的业务层”。只涉及加载、刷新、分页、简单写操作的 FA 资源页面，UI 应直接依赖 `data` 的仓储。只有当功能包含复杂业务逻辑、跨资源编排、独立算法、可复用策略，或明显不属于单一资源获取时，才放入 `domain`。

参考：[Android 官方架构文档](https://developer.android.com/courses/pathways/android-architecture?hl=zh-cn)将应用分为界面层、数据层，以及可选的网域层；网域层用于封装复杂业务逻辑或多个 ViewModel 复用的逻辑。

各层职责：

- `ui`：Compose UI、Voyager route/screen、ScreenModel、导航、交互状态和一次性 UI effect。
- `data`：Pawchive transport/parser、repository、cache、Room/DataStore/KSafe、设置、翻译 provider 和应用数据模型。Data 不得依赖 UI。
- `domain`：可选层，只容纳跨资源编排、独立算法或多个 UI feature 复用的复杂业务规则。
- `utils`：少量真正跨层的通用能力，例如日志脱敏、协程结果处理。禁止成为 feature helper 杂物箱。
- `di`：Koin 装配边界，负责组合实现，因此允许引用各层。

## 当前目录布局

### 应用模块

- `androidApp`：Android application、manifest、launcher/shortcut/backup 资源和发布配置。
- `desktopApp`：Desktop application、窗口入口和桌面平台 DI。
- `shared`：KMP 共享业务、Compose UI、Android/Desktop actual、资源、schema 与测试。

### shared source sets

- `shared/src/commonMain`：跨平台 data、DI、UI、navigation、resources 和通用工具。
- `shared/src/androidMain`：Android Room/DataStore/KSafe、locale 和文件选择/写入 actual。
- `shared/src/desktopMain`：Desktop Room/DataStore/KSafe、locale 和文件写入 actual。
- `shared/src/commonTest`：跨平台单元测试和真实 Pawchive fixture。
- `shared/src/desktopTest`：依赖 JVM/MockEngine/文件系统的测试。

### commonMain 主要包

- `data/model`：共享资源模型、强类型 key、分页模型和查询结果。
- `data/local`：Room、历史仓储、DataStore/KSafe 设置与 secret contract。
- `data/remote`：Pawchive transport/parser、Room-backed SWR cache、远程资源仓储、媒体下载与 translation provider。
- `ui/app`：应用根节点、navigation、locale 与应用级 CompositionLocal。
- `ui/pages`：按 feature 组织的 route、ScreenModel、内容组件与局部 state。
- `ui/components`：跨 feature Compose 组件、分页/query state、图标与平台文件 contract。
- `ui/theme`：Material theme 与平台动态配色 contract。

不要仅为了“架构感”新增 Gradle module。先在 package 和依赖方向上形成稳定边界；只有边界成熟、编译隔离或所有权确有收益时再拆模块。

# 开发说明

## E2E

在 `nix develop` 中运行：

```bash
./gradlew :desktopApp:run
./gradlew :desktopApp:onlineE2e
# 单独验证一个场景：
./gradlew :desktopApp:onlineE2e -Pe2e.case=nav-creators
```

E2E 全部使用 Kotlin，在生产 Tao 窗口的同一个 ComposeScene 中执行，访问真实线上网站。
没有 fixture、MockEngine 或 AWT 测试窗口路径。普通 parser/repository 单元测试仍保留真实下载的 fixture。

每个一级导航（连同它的搜索、分页和列表返回恢复）各一个场景；艺术家页和投稿详情页各一个完整场景。
数字分页验证前进、后退、跳到第 3 页、再次后退；游标分页只验证真实支持的前进与后退。
滑动通过 Compose 指针事件执行，图片平移使用鼠标，Pager 使用触摸滑动。测试读取实际语义树和实际视口状态。

默认使用 `desktopApp/build/onlineE2e/profile` 隔离设置、历史、数据库和会话。
首次登# 开发说明

## 常用命令

按改动范围选择最小但充分的验证：

```bash
# shared Desktop 编译
./gradlew :shared:compileKotlinDesktop

# shared Desktop 测试
./gradlew :shared:desktopTest

# Android debug 编译与 Lint
./gradlew :androidApp:assembleDebug :androidApp:lintDebug

# 完整 shared 检查
./gradlew :shared:check
```

在 KDE 上，先准备第二个虚拟桌面。任务自动给专用的 `FA2 E2E` / `KC E2E` 窗口设置 KWin 规则：固定到最后一个桌面、禁止自动抢焦点。
也可用 `-Pe2e.desktopId=<UUID>` 选择桌面，或 `-Pe2e.kdeDesktop=false` 关闭该集成。
普通应用窗口不匹配这条规则；测试不会主动切换当前桌面。
专用测试窗口固定为 1280×900，保证翻页和滚动断言在一致的可视区域内执行。
当后台窗口被 compositor 节流时，测试只推进同一个真实场景的帧，不创建另一套测试 UI。
检测到人工验证后会放开该窗口的焦点限制；切到测试桌面，在真实 WebView 中操作即可。测试结束也会恢复焦点限制设置。

`TaoSceneAdapter` 仅存在于 `desktopTest`，当前适配 CMP 1.11.1 / Nucleus 2.4.7。
Nucleus 尚未暴露测试根注册接口，因此版本相关的根节点访问被集中在这个适配器；同时收集主窗口和 Popup/Dialog 的根。
升级这些库后先运行 `-Pe2e.case=tao-adapter-smoke`，确认真实弹窗输入、确认和分页链路

## 约定

- 使用 `nix develop` 运行 `adb` 等命令。
- 图标只用最合适的，不要只在已有图标里硬凑。请任意添加图标到 SymbolCraft。
- 不保留 Kemono、Coomer 或其他相似站点的历史平台抽象。
- `shared/src/commonTest/resources/fixture/` 下的网页 fixture 必须是从真实 Pawchive 网站下载的完整响应页面。
- 禁止手工编造、拼接、缩减或“修得更适合 parser”的 HTML。
- 仓库中的 `dummy.keystore` 是有意公开的 feature，不是泄露的 secret，也不得在清理中删除。
