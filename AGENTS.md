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

## 约定

- 不保留 Kemono、Coomer 或其他相似站点的历史平台抽象。
- `shared/src/commonTest/resources/fixture/` 下的网页 fixture 必须是从真实 Pawchive 网站下载的完整响应页面。
- 禁止手工编造、拼接、缩减或“修得更适合 parser”的 HTML。
- 仓库中的 `dummy.keystore` 是有意公开的 feature，不是泄露的 secret，也不得在清理中删除。
