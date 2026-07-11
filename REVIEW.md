# KC 代码库防腐审查

审查基线：`9240243`（工作区另有用户已有的 `gradle.properties` 修改，本审查未改动它）  
审查日期：2026-07-10  
范围：244 个受版本控制文件；其中 184 个 Kotlin 文件、构建/CI/资源配置、Room schema、测试 fixture 与二进制资产均纳入清单。生成目录和 `build/` 产物不作为手写代码审查对象。

## 修复实施状态（2026-07-11）

本报告下方保留最初审查证据，便于追溯问题为什么成立；这些描述不是当前代码状态。当前修复遵循以下最终约束：产品只支持 Pawchive；公开 keystore 是开源分发 feature；项目处于 pre-alpha，Room 直接维护当前 schema version 1，不提供旧版本 migration、自动删库或清 cache 兼容逻辑；release 工作流只构建和发布，不运行测试。

| 问题 | 处理结果 |
|---|---|
| P1-01 | 设置持久化改为单个 `AppPreferences`、单次 `DataStore.edit`；保存编排移入 `SettingsScreenModel`。 |
| P1-02 | 搜索、分页、收藏及详情资源加入结构化取消、request generation 或按资源 token；旧响应不能覆盖新请求。 |
| P1-03 | 引入 `CreatorKey`、`PostKey`、`DmKey`，状态、去重、缓存和 Compose identity 不再使用裸 id。 |
| P1-04 | 删除 Store5/ChunkedCache 双层方案，统一为 Room stale-while-revalidate cache；坏条目自愈、fresh 状态正确、同 key single-flight。 |
| P1-05 | history DB 使用平台标准持久数据目录；坏历史行会清理，DB IO 使用 IO dispatcher。pre-alpha 不保留旧 cache DB 搬运代码。 |
| P1-06 | 应用在 settings `Ready` 后才组成主界面，首屏不再读取默认值后与初始化竞态。 |
| P1-07 | session 与 OpenAI API key 进入 `SecretStore`；Android 禁止 backup；网络、翻译、cache 和图片日志统一脱敏。 |
| P1-08 | route 只保存资源键或临时 window id；Post/Creator 可用资源键恢复，临时列表/图片过期时显示可恢复 UI 而非崩溃。 |
| P1-09 | suspend 路径统一使用会重抛 `CancellationException` 的 `resultOfSuspend`；剩余 `runCatching` 只包同步解析/关闭操作。 |
| P2-01 | 删除旧 `Platform` 模型、active-platform 设置、shortcut manager、序列化 expect/actual 和无效参数。 |
| P2-02 | 删除 Discord、旧 Login、recommended 等不可达空能力；服务端错误不再伪装为空成功。 |
| P2-03 | 网络拆为 `PawchiveHttpGateway`、公开内容 API、`PawchiveAccountApi`、HTML parser 和 `MediaDownloader`。 |
| P2-04/P2-05 | cache 所有权收敛到 `RawBodyQueryStore`；并发、损坏、自愈和一致性在单一实现处理。 |
| P2-06 | 翻译对齐 fail-closed，marker 不匹配产生显式 failure，不再猜测均分译文。 |
| P2-07 | 分页共享 reducer；错误改为 typed `QueryError` 并在 UI 资源层本地化；History/Settings/Session 使用 ScreenModel 边界。 |
| P2-08 | Post/Creator 巨型 route 拆为 route wiring、detail content 和 components；Tab 使用各自 Navigator，不再直接调用 `Screen.Content()`。 |
| P2-09 | about metadata 只从 Compose resource 加载，缺失/空内容显式失败；构建提供 metadata 校验。 |
| P2-10 | Pawchive URL 只接受无 path/query/fragment 的 HTTPS origin，UI 与持久化层均验证。 |
| P2-11 | locale 全局副作用移出 composition，并在 desktop disposal 时恢复。 |
| P3-01 | 删除审查列出的死文件、空兼容文件和无调用模型。 |
| P3-02 | data/domain 不再携带面向用户的异常 message；设置和查询错误通过中英文资源映射。 |
| P3-03 | 删除错误的全局 `Accept: text/css` 与 Ktor body logging。 |
| P3-04 | 下载相对路径使用 `SafeRelativePath`；desktop canonical 校验并原子替换，Android 不再先删旧文档。 |
| P3-05 | hook 不再自动修改 version code，文件名管道和空输入得到安全处理。 |
| P3-06 | minSdk 保持 29，删除无用 legacy launcher PNG，只保留 adaptive/monochrome icon；manifest backup/App Link/resource 声明已清理。 |

Fixture 额外约束记录在 `AGENTS.md` 与 `shared/src/commonTest/resources/fixture/SOURCES.md`：HTML 必须是 Pawchive 真实完整页面快照，禁止手工构造。人工编写的 `pawchive.st__dms__nonempty.html` 已删除。GitHub release 已与 `check.yml` 分离，release 本身不执行任何测试或 Lint。

最终验证结果将在所有修改完成后统一更新到“已验证事项”，不会用修改过程中的局部编译代替最终验收。

## 结论先行

代码能编译、现有测试和 Android Lint 均能跑通，但“能跑”主要由当前单一 Pawchive 场景、happy-path fixture 和大量空结果兜底共同维持。当前最大问题不是格式或命名，而是模型仍保留上一代多站点产品的形状，运行时却已经只剩 Pawchive；这使得无效 `Platform` 参数、硬编码 `pawchive:` cache key、空实现 API 和平台分支同时存在。它们既没有形成可扩展架构，也没有形成简单的单站点架构。

建议不要继续修补这层伪抽象。目标架构应明确单 Pawchive：删除站点 `Platform` 维度，以 Pawchive 资源键、Pawchive gateway 和能力表述真实业务；如果未来再次支持别的站点，再在真实的第二个实现出现时抽象。

本轮共记录：

- P1（高优先级）9 项：会导致数据错位、请求竞态、持久化不完整、隐私泄露或历史数据丢失。
- P2（中优先级）11 项：架构持续腐化、功能假成功、合法性信息静默消失或维护成本显著偏高。
- P3（清洁度）6 项：死代码、错误边界、国际化和工具链问题。

没有发现必须在本报告中列为 P0 的问题。仓库公开 `dummy.keystore` 是维护者确认的开源分发特性，因此不作为缺陷；但它意味着签名只能保证“同一把公开密钥”，不能认证发布者身份，见“已确认设计约束”。

## 已验证事项

- `./gradlew :shared:desktopTest --continue`：通过。
- `./gradlew check --continue`：通过；覆盖 shared desktop tests、Android 编译和 Android Lint。
- Android Lint：0 errors、27 warnings。主要是 App Link 声明、重复/形状不合规 launcher icon、无 monochrome icon、`mipmap-anydpi-v26` 在 minSdk 29 下冗余。
- `git diff --check`：通过。
- 英文与简体中文 Compose resource key 集合一致。
- 当前自动化测试只覆盖 8 个测试文件；没有 Android 单元测试/仪器测试，翻译、会话、设置原子保存、请求竞态和绝大多数 ScreenModel 没有测试。

## 严重问题

### P1-01 设置保存不是事务，并可能被语言切换中途取消

证据：

- `SettingsScreen.kt:245-257` 在一个 UI coroutine 中依次调用 10 个独立 setter。
- `AppSettings.kt:150-347` 的每个 setter 都单独执行一次 `DataStore.edit`。
- 保存顺序先写 theme、language，再写其余设置；`KcApp.kt:35-37` 通过 `key(localeTag)` 重建整棵内容树。

当用户修改语言并保存时，`setLanguage` 发出新值，`key(localeTag)` 会销毁旧 Settings composition；由 `rememberCoroutineScope` 启动的保存 coroutine 可能随之取消，留下“主题和语言已保存、其余设置未保存”的半事务状态。即使没有取消，任一后续写入失败也会部分提交。UI 只显示一个笼统错误，无法回滚。

修复：把所有可持久化设置定义为一个 `AppPreferences` snapshot，提供一次 `update {}` / 单次 `DataStore.edit` 的原子提交。设置页只向 ScreenModel 发送 `Save(SettingsDraft)`；保存成功后再触发语言/主题重建。API key 不进入该 snapshot，见 P1-07。

### P1-02 多个分页/搜索请求可由旧响应覆盖新状态

证据：

- `PostSearchScreenModel.kt:102-110` 的 `searchJob` 只包住 debounce；`search()` 在 `:176` 又启动了一个未保存的 job。取消 debounce job 无法取消已开始的请求。
- `PopularPostsScreenModel.kt:192-231` 每次日期/周期切换都启动新 job，没有取消旧 job或核对 request key；成功回调在 `:252-279` 直接覆盖当前 state。
- `TagsScreenModel.kt:47-77`、`FavoritesScreenModel.kt:28-57` 和详情加载也没有 single-flight/generation guard。
- append/prepend/jump 在多个 ScreenModel 中各自读取 `mutableState.value`，响应完成时再合并；期间 query、period 或页面窗口可能已经变化。

快速输入、切换热门周期或连续跳页时，较慢的旧请求可以把新请求的列表、分页信息和 loading flag 覆盖掉。现有测试只测纯 reducer，没有测试真实请求乱序。

修复：统一使用 `StateFlow<RequestSpec>.debounce().distinctUntilChanged().flatMapLatest`；分页动作进入单一 actor/reducer。每个 response 携带不可变 `RequestId(spec, generation)`，reducer 只接收当前 generation。append/prepend 也必须串行化，而不是每个按钮各 launch 一个 job。

### P1-03 业务实体身份被错误简化为裸 `id`

证据：

- `CreatorPagerUiState.kt:16-30` 几乎所有 map/set 都只用 `creator.id`。
- `PostPagerUiState.kt:16-26` 评论、翻译、收藏、图片请求和加载状态只用 `post.id`。
- `PostScreenModel.kt:62-64,233-311,362-562` 的进程内缓存同样只用 `post.id`；详情替换只额外比较了 service，却没有比较 creator id。
- `PostDetailPagingMerge.kt:26,37-39`、`PostSearchScreenModel.kt:292-293`、`PaginationReducer` 的多个调用点用 `post.id` 去重。
- `CreatorRouteScreen.kt:248,300` 的 remember/LaunchedEffect key 只用 creator id。

Pawchive 的稳定身份至少是 creator=`(service,id)`、post=`(service,creatorId,id)`。不同 service/creator 出现相同裸 id 时，会串评论、翻译、收藏状态、详情加载、Compose remember 状态或直接丢掉列表项。

修复：在 domain 层建立 `CreatorKey(service, id)` 与 `PostKey(service, creatorId, id)` value object；所有 cache key、map/set、lazy item key、route 参数和 reducer `keyOf` 只接受这些类型，不再接受裸 String。

### P1-04 cache 命中路径会崩溃，刷新后仍可能永远标记 stale

证据：

- `StoreQuery.kt:48-50` 在任何错误映射之前解析 Room cache；损坏/旧版本 JSON 会直接终止 flow，无法删除坏 cache 或回源。
- `ChunkedCache.kt:45-52` 同样直接 decode meta/chunk；`CreatorRepository.kt:121-129` 的读取发生在 `runCatching` 之外。
- `StoreQuery.kt:155-173` 的 `QueryAccumulator.reduce(Data)` 没有把 `isStale` 设为 false；由 stale cache 开始的成功刷新仍输出 `isStale=true`。
- `StoreQueryTest.kt:44-69` 只断言数据从 cached 变 fresh，没有断言最终 freshness，也没有 corrupted-cache case。

这会造成两种相反结果：坏 cache 阻止网络自愈；正常刷新后 UI 又认为数据仍旧过期并反复加载。

修复：cache decode 必须是可恢复边界：解析失败记录结构化事件、原子删除该 key、继续 fresh fetch。把 freshness 作为 `CachedAt + TTL` 的派生值，不在 accumulator 中复制保存；新增 stale→fresh、corrupt→network、schema version mismatch 测试。

### P1-05 历史记录被放在操作系统可清理的 cache 目录

证据：

- 同一个 `AppDatabase` 同时保存 `CacheEntity` 和两张 history 表（`AppDatabase.kt:13-16`）。
- Android DB 位于 `context.cacheDir/room-cache`（`AndroidPlatformModule.kt:24-32`）。
- Desktop DB 位于 `~/.cache/kc/room-cache`（`DesktopPlatformModule.kt:20-25`）。

系统清缓存、磁盘压力、用户清理工具或常规维护都会把“浏览历史”当网络缓存删掉。这不是实现细节，而是数据生命周期建模错误。

修复：至少将整个 Room DB 移到持久数据目录（Android 标准 database path、desktop `~/.local/share/kc`/平台 app-data）；更清晰的方案是 `ContentCacheDatabase` 与 `UserActivityDatabase` 分离，让清缓存不会触碰历史。补迁移，将旧 cache DB 中的 history 尽力搬到新位置。

### P1-06 启动初始化与首屏请求竞态

证据：`KcApp.kt:27` 异步 `appSettings.init()`，但 `RootNavigator` 在同一帧立即组成；各 tab 的 `LaunchedEffect`/singleton ScreenModel 会立即加载。`AppSettings` 同时维护默认值镜像和 DataStore flow（`AppSettings.kt:78-140`）。

如果用户配置了备用域名、翻译设置或其他非默认值，首屏请求可能在 init 完成前使用默认 Pawchive URL；随后 flow 更新 UI，却不会必然取消/重发已经开始的请求。这个问题被当前本地默认配置掩盖。

修复：应用启动先构造一个 `SettingsRepository.state: StateFlow<LoadState<AppPreferences>>`；只有 Ready 后才组成业务导航，或让所有 gateway 从同一 ready state `flatMapLatest` 获取配置。不要同时维护“可变字段镜像 + DataStore flow”两套真相。

### P1-07 secret 与用户内容的隐私边界不足

证据：

- OpenAI API key 以普通 Preferences DataStore 字符串保存（`AppSettings.kt:39,128-138,293-333`），而 manifest 设置 `android:allowBackup="true"` 且没有 backup rules。
- session 使用 KSafe，但 app backup 策略没有明确排除其底层文件。
- Google 翻译把完整原文放入 query 参数（`GoogleTranslationClient.kt:16-31`）；共享 Ktor client 开启 `LogLevel.INFO`（`KcHttpClient.kt:19-25`），runtime log 又可导出，因此请求 URL 可能把待翻译内容写入日志。
- `NetworkImage.kt:90-95`、`CachedImageHttpClient.kt:56-65` 记录完整媒体 URL；`ensureTranslationSuccess` 把响应 body 前 160 字符塞进异常（`TranslationProviderClient.kt:7-11`）。

修复：API key/session 统一进入 `SecretStore`，Android backup 显式排除；日志层默认只记录 host、route template、status、duration 和 opaque request id，禁止 query/body/authorization。翻译 provider error 只保留 status 与 provider error code。为日志增加 redaction 单测。

### P1-08 导航对象携带整个列表和可序列化业务图

证据：`CreatorRouteScreen`、`PostRouteScreen`、`DiscordChannelRouteScreen`、`ImageViewerScreen` 的构造参数直接携带 creator/post/channel/url 列表；data model 通过 `PlatformSerializable` 全部变成 Java `Serializable`。例如 history 页在 `HistoryRouteScreen.kt:109-143` 把完整历史列表推入 route。

这会放大导航栈内存，并在 Android 状态保存/恢复时面临大 bundle、慢序列化甚至 `TransactionTooLargeException`。它也迫使 data model 为 UI 导航实现 Java 序列化，反向污染 domain。

修复：route 只携带 `ResourceKey + PagingContextKey + startIndex`；列表窗口放到 navigator-scoped store/repository，恢复时按 key 重建。删除 `PlatformSerializable` 以及 model 上不必要的 Java serialization。

### P1-09 `runCatching` 普遍吞掉 coroutine cancellation

证据：几乎所有 ScreenModel 都用 `runCatching { suspendCall() }.onFailure { mutateState(...) }`；translation chunk 也采用同一模式（`TranslationChunkTranslator.kt:17-45`）。Kotlin 的 `runCatching` 会捕获 `CancellationException`。页面退出、query 被 `flatMapLatest` 取消或 app shutdown 时，取消会被当普通业务失败处理，旧 job 仍可同步写 error/rollback state；translation 甚至把取消转成每个 block 的 Failure。

这不仅制造假错误，也会破坏 P1-02 所需的请求取消语义。

修复：提供 `resultOfSuspend` helper，在 catch 中首先 `if (e is CancellationException) throw e`；或者使用显式 `try/catch` 只捕获预期异常。增加“取消不发 error、不修改新 generation state”的测试，并用静态规则禁止在 suspend 路径直接使用 `runCatching`。

## 架构与中优先级问题

### P2-01 伪多平台抽象应整体删除，而不是补齐

维护者已确认产品只需对齐 Pawchive。当前历史抽象包括：只有一个 entry 的 `Platform` enum；`AppSettings.activePlatform()` 恒定返回 Pawchive、两个 setter 是空函数；repository 大量接收但不使用 `platform`；cache key 和 fetcher name 又全部硬编码 `pawchive`；Android shortcut/deep link 仍尝试切 active platform。

修复范围应是架构级：删除站点 `Platform`、`LocalActivePlatform`、active-platform flow/setter、所有无用 platform 参数和分支、shortcut 的平台 extra。把 `baseUrl/cdnUrl/session` 直接建模为 `PawchiveConfig`。这会同时减少 ScreenModel 参数、Koin parameter index、route 序列化和测试组合。

注意：Android/desktop 运行平台仍然是有效 KMP 维度，不在删除范围内。

### P2-02 未实现能力用空列表伪装成功

`KcApiClient.kt:441-445,485-489,585-594` 的 creator DMs、recommended creators、Discord channels/posts 直接返回空列表；repository 又重复一层空实现。Creator 页面打开时仍在 `CreatorRouteScreen.kt:300-310` 主动请求 links、recommended、Discord 等资源，于是“未实现/不支持”被 UI 显示成“确实没有内容”。

`LoginScreen`/`LoginScreenModel` 没有任何导航入口，`KcApiClient.login` 也只是把 password（空时 username）保存为 session；真实入口已经改成 More 页 session cookie editor。这整条用户名密码登录链是死功能。

修复：对 Pawchive 不支持的能力直接删除 API、repository、DI、state、tab 和页面；确实需要保留的可选能力用 `Capability.Unsupported` 或 feature flag 明确表达，绝不能返回业务空集合。删除死登录链。

### P2-03 网络、HTML scraper、DTO 与 session mutation 塞在一个 686 行 client

`KcApiClient` 同时负责 URL 拼接、HTTP status、JSON API、HTML DOM scraping、分页推断、session、favorites mutation、文件下载和多个 stub。Repository 一部分缓存 raw body、一部分先 decode 再重新 encode 成 JSON；同一 creator page 为 posts 和 pageInfo 分别请求 JSON endpoint 与 HTML endpoint（`PostRepository.kt:275-285`），可能来自不同时间点。

修复：拆为 `PawchiveHttpGateway`（只处理请求/响应）、`PawchiveJsonApi`、`PawchiveHtmlScraper`、`SessionRepository`、`MediaDownloader`。每个 public operation 返回 typed DTO/result；creator page 的 items/pageInfo 应来自一次一致请求，或明确组合版本。URL path segment 用 Ktor URL builder 编码，不手拼字符串。

### P2-04 cache 方案叠床架屋且缺少清晰所有权

项目同时使用 Room raw-body cache、Store5、手写 `MutableSharedFlow` source-of-truth、特殊 creators chunk cache、Coil disk cache、Ktor `HttpCache`。Store converter 的 `fromOutputToLocal` 直接 `error()`（`StoreQuery.kt:130-136`），说明抽象与数据流并不匹配。force refresh 经常按大 prefix 删除所有搜索页，再立即重建。

修复：网络业务数据保留一套 cache coordinator 即可。当前体量最直接的是 Room + 明确的 stale-while-revalidate repository，删除 Store5 wrapper；如果保留 Store5，就按其数据流完整建模，不放“理论上不会调用”的 `error()`。所有 cache write/invalidate 进入事务和 single-flight。

### P2-05 chunk cache 写入非事务且可并发交叉

`ChunkedCache.kt:62-93` 依次 delete、写 N 个 chunk、写 meta，没有 Room transaction；多个 `observeCreators` 也没有 single-flight。并发刷新可把 A 的 meta 配到 B 的 chunks，进程中断则留下半写状态。meta 的 `itemCount/chunkCount` 也没有上限验证，损坏数据可造成大分配/长循环。

修复：DAO 提供单个 `@Transaction replaceChunkedList`，key 级 mutex/single-flight，先写带 generation 的新 chunks，最后原子切 meta，再清旧 generation；读取校验 count 上限与实际 item count。

### P2-06 翻译对错位结果“猜着对齐”，会静默生成错误译文

`TranslationResultAligner.kt:4-29` 在 separator 数量不符时依次尝试换行，最后通过补空字符串或按行数均分强行凑成期望块数。用户看到的是 `Success/Empty`，无法知道原文和译文已经错位。`TranslationChunkTranslator` 又把整个 chunk 的异常压成每块 Failure，外层错误处理重复。

修复：对齐必须 fail closed：provider 返回结构化 JSON（block id + text），或给每块不可变 marker 并严格校验集合相等；校验失败整 chunk 标为 alignment error，可按单块重试，不能均分猜测。为 HTML block extraction、CJK、marker 被模型改写、部分失败和 cancellation 建测试。

### P2-07 UI 状态机重复且错误状态经常被吞掉

`PopularPostsScreenModel`、`PostSearchScreenModel`、`DmSearchScreenModel`、`RecentDMsScreenModel`、`TagPostsScreenModel`、Creator 内 posts 各自实现相似分页窗口；只有部分使用 `PaginationReducer`。字段如 `startOffset/offset/visibleOffset/autoPrependArmed/isLoadingPrevious/isLoadingMore` 被复制，行为已出现分叉。

Creator announcements/tags/links/recommended/Discord 的 state 只有 loading set 和 data map；失败仅写日志，不进入可展示 error。History 页直接从 Composable 调 repository，异常没有 UI 边界。空、失败、不支持因此常被合并成同一种空页面。

修复：建立一个 `PagedWindow<Key, Item>` + reducer/actor，并让所有 feature 复用；资源状态统一为 `NotLoaded | Loading(previous?) | Data | Empty | Unsupported | Error`。ScreenModel 输出 state + one-off effect，Composable 不直接调用 repository。

### P2-08 巨型 route/composable 与 service locator 破坏 feature 边界

`PostRouteScreen.kt` 1257 行、`CreatorRouteScreen.kt` 761 行、`SettingsScreen.kt` 592 行、`WorksScreen.kt` 537 行。页面内部同时做导航、下载、历史写入、翻译展示、分页、toast 和布局。大量 Composable 通过 `koinInject`/`LocalAppSettings` 隐式取依赖，预览和测试难以构造。

修复：按 feature vertical slice 组织，每个 route 只有 wiring；拆 `PostDetailContent`, `PostMediaSection`, `PostActions`, `CommentsSection` 等纯 UI。依赖在 route 边界注入，子组件只接 `UiState` 和 `UiAction`。不要为每个小 UI 建 Gradle module；先包级收敛，边界稳定后再模块化。

### P2-09 about-libraries 的三层 fallback 会静默隐藏法定信息

`AboutLibrariesJsonLoader.kt` 先读 Compose resource，再尝试 Android 全局 context/desktop classloader，最终返回合法但空的 JSON。Android/desktop actual 又各自 `runCatching().getOrNull()`。资源打包一旦出错，用户看到的是“没有依赖”，构建和运行都不失败。

修复：Compose resource 是唯一入口；加载失败显示明确错误并记录 telemetry，release build 加验证任务，断言 JSON 可解析且 libraries 非空。删除 `AboutLibrariesAndroidContextHolder`、两个 platform loader 和空 JSON fallback。

### P2-10 设置允许的 HTTP URL 在 Android 上实际不可用

`SettingsDraft.kt:44-48,64-67` 接受 `http://` Pawchive/OpenAI URL；manifest 没有启用 cleartext 或 network-security-config，而 minSdk=29/targetSdk=36 默认拒绝明文流量。manifest 还声明了 HTTP deep links。这个“支持 HTTP”的兜底在 Android 必然走不到成功路径。

修复：如果没有明确局域网开发需求，只允许 HTTPS 并删除 HTTP deep link；如果确实需要，做 debug-only network security config，并在 UI 明确标注，而不是生产配置全局放开。

### P2-11 locale actual 在 composition 期间修改全局 Locale

Android/desktop `LocalAppLocale.provides` 在 Composable 求值期间直接 `Locale.setDefault`。这是不可回滚的全局副作用，可能影响并发格式化、测试和第三方库；Android 还用静态 `default` 捕获首次 locale。

修复：Android 使用 per-app language/受控 `Configuration` context，desktop 用显式 composition local 给资源与 formatter；全局 Locale 变更放在受生命周期管理的 effect，并在退出/切 system 时可恢复。删除无人使用的 `LocalAppLocale.current`。

## 低优先级清洁项

### P3-01 明确删除的死代码

- `BackTopAppBar.kt` 只有一行“兼容”注释，没有声明。
- `PagerUiState.kt` 未被业务使用。
- `Platform.defaultCdnUrl`、`AppSettings.setActivePlatformPersisted`、`ChunkedCache.readChunkedList`、`CreatorLink.toLinkedCreatorOrNull`、`KcDdosGuardException`、`LogSummaries.summarizeThrowable/summarizeUrl` 无调用。
- `LoginScreen`/`LoginScreenModel`/对应 DI 是不可达链。
- `getCreatorDMs` API/repository 无调用且恒空。
- `selectBoundaryDate` 的 boundary 被 suppress 后完全忽略。

删除优于保留“以后也许用”。未来需求应从真实 use case 重新引入。

### P3-02 错误文案混在 data/domain 层

`QueryState.kt`、exception 默认文案和多个 platform writer 直接硬编码中文/英文；UI 也仍有 Pawchive Session、返回/主页/翻译/加载大图等硬编码。资源 key 虽然中英文集合一致，但实际体验并未完全国际化。

修复：data 层只返回 error code + metadata；UI 按 locale 映射 resource。开发日志可以固定语言，用户可见文案不可以。

### P3-03 网络默认 Accept 明显不合语义

`KcHttpClient.kt:26-29` 对所有请求设置 `Accept: text/css`，而 client 实际请求 JSON、HTML、二进制和翻译 API。单个请求可能覆盖它，但默认值会制造服务端协商和排障噪音。

修复：不设全局 Accept；每个 adapter 按 endpoint 设置 JSON/HTML/*。媒体下载用独立 client 或明确配置。

### P3-04 文件写入边界依赖“调用者已经清洗”

desktop writer 直接 fold `relativeDirectories` 并 resolve `fileName`，公共 request 类型本身不保证相对、安全 segment。当前下载命名路径经过 sanitize，但其他调用者可传 `..`/absolute segment。Android writer 也没有统一校验 segment。

修复：在 writer 边界校验 canonical target 必须位于 selected root 内；request 接受 `SafeRelativePath`，不要接受任意 String 列表。保留 DownloadNaming 单测并增加 traversal case。

### P3-05 git hook 会产生意外工作区修改

`lefthook.yml` 的 `version_code` 在每次 pre-commit 都改 working tree，但没有 `stage_fixed`，很可能不会进入本次 index，反而留下脏 `gradle.properties`。treefmt 管道没有 NUL 分隔，也没有 empty-input guard；含空格文件名会出错，无 staged file 时可能运行全库。

修复：version code 由 release task/tag 显式更新，普通 commit 不自增；或确保修改被明确 stage。使用 `git diff -z ... | xargs -0 -r treefmt`。CI 增加 format/check，而不只构建 release。

### P3-06 Android manifest/resource 警告应清零

根据本次 Lint：明确为 App Link 则正确配置 `autoVerify` 与域名 `assetlinks.json`，否则显式 `autoVerify=false`；拆清 data matcher；把 minSdk 以下的 v26 目录合并；修正 round icon、monochrome layer 和重复 icon。`allowBackup` 同时按 P1-07 加规则。

## 建议的目标架构

### 依赖方向

```text
androidApp / desktopApp
        ↓
app-shell (导航、DI、启动门)
        ↓
feature: creators | posts | dms | history | settings | about
        ↓
domain (ResourceKey、实体、repository contract、error code)
        ↓
data:pawchive (JSON API、HTML scraper、repository、cache coordinator)
        ↓
core: http | database | preferences | secrets | logging
```

这是逻辑边界，不要求一次创建十几个 Gradle module。第一阶段先按 package 和依赖规则落地；只有需要独立编译/测试或边界经常被破坏时再拆模块。建议最终 Gradle 形态：

- `:shared:core`：KMP 基础设施和 design primitives，不知道任何 feature。
- `:shared:pawchive`：Pawchive DTO、gateway、scraper、repository/cache。
- `:shared:features`：可先保持一个 module，内部 vertical slices；稳定后按重 feature 拆分。
- `:shared:app`：导航、DI、启动与跨 feature coordination。
- `:androidApp`、`:desktopApp`：只做平台装配。

### 关键建模决定

1. 单站点：删除站点 `Platform`，保留 `PawchiveConfig(baseUrl)`。
2. 强身份：`CreatorKey`、`PostKey`、`ChannelKey`，禁止裸 id 进入 state key。
3. 单真相设置：`StateFlow<AppPreferences>`，启动 Ready gate，一次原子保存；`SecretStore` 独立。
4. 单 cache：typed record + schemaVersion + fetchedAt；解析失败自愈；按 key single-flight。
5. 单分页状态机：request spec + generation + actor/reducer；所有 feature 复用。
6. 显式能力：不支持就从 Pawchive UI 删除；不能用 empty list 表示 unsupported。
7. 小 route：只传 key/context id，不传完整列表。
8. UI 纯化：route 注入、ScreenModel 协调、Composable 只渲染 state/发送 action。

## 分阶段修复路线

### Phase 0：建立安全网（1-2 天）

- 为当前行为补 characterization tests：cache stale/corrupt、设置原子保存、搜索乱序、ID collision、route parser。
- CI 在 release build 前执行 `check`、format check、资源/Room schema verification。
- 为关键 state transition 加 deterministic fake repository，不依赖真实时间和网络。

退出条件：上述 P1 场景都有失败测试，现有 fixture 测试继续通过。

### Phase 1：单 Pawchive 化（2-4 天）

- 删除 `Platform`、active platform、无用参数/shortcut extra/CompositionLocal。
- 删除 Login、creator DMs、recommended、Discord 空实现及其不可达 UI；若 Pawchive 的 Discord 能力确实要做，先写契约测试再独立实现。
- 引入 `CreatorKey`/`PostKey`，全库替换裸 id state key。

退出条件：不存在 `Platform.PAWCHIVE`、空函数和业务 API `= emptyList()`；同 id 跨 service 测试通过。

### Phase 2：启动、设置与 secret（2-3 天）

- `SettingsRepository` 暴露单一 ready state；应用等待 ready 再创建 feature graph。
- 设置一次原子提交，语言切换在提交成功后生效。
- API key/session 迁移到 SecretStore；Android backup rules 与日志 redaction 落地。

退出条件：故障注入不会产生半保存；冷启动只向持久化配置的 base URL 发请求；日志中没有原文、query、token。

### Phase 3：数据层收敛（4-7 天）

- 拆 `KcApiClient` 为 gateway/JSON/scraper/session/media。
- 选择 Room 自建 SWR 或正确使用 Store5，只保留一套；建议前者。
- cache decode 自愈、transactional chunk/single-flight；history 迁到持久目录。
- creator posts + pageInfo 合成一致的 repository operation。

退出条件：数据层没有 `error("不会发生")`；cache corruption、并发 refresh 和迁移测试通过。

### Phase 4：统一 UI 状态机（5-8 天）

- 用统一 `PagedWindow` 替代 6 套分页实现；请求使用 flatMapLatest/generation。
- 为 resource 建模 Empty/Unsupported/Error；移除 Composable 直连 repository。
- 拆 Post/Creator/Settings 巨型页面；route 只传 key。

退出条件：旧响应无法覆盖新 query；详情页文件控制在可审查职责范围（建议 <300 行，而非机械指标）；ScreenModel 有 reducer 测试。

### Phase 5：翻译、about 与清洁收尾（2-4 天）

- 翻译改成结构化对齐/fail-closed，补齐测试。
- about-libraries 单入口 + release verification，删除静默空 fallback。
- 删除死文件/死 symbol，清 Android Lint、hook 和硬编码用户文案。

退出条件：`rg` 无已知 dead stubs，Lint warning 有明确为零或书面豁免，`REVIEW.md` 中所有条目被 issue/commit 关闭。

## 已确认设计约束

- `dummy.keystore` 与公开密码是有意的开源签名方案，不作为本次缺陷。它支持任何人复现/签出同 key 的 APK，但不提供“只有官方维护者能发布更新”的身份认证。README/发布页应明确这一点，避免贡献者误把它当 secret 或官方信任根。
- 站点维度只保留 Pawchive；过去的 Kemono/Coomer 等平台兼容不再是目标。架构应据此做减法。

## 逐文件审查台账

说明：这里的“可保留”表示没有发现独立于上文的阻断问题，不表示文件永远无需随架构迁移；“合并处理”表示问题已经由对应编号完整描述。生成物、图片、fixture 按来源、引用关系、重复性和构建可达性审查，不对二进制内容做源码式评价。

### 根目录、构建与 CI

| 文件 | 结论 |
|---|---|
| `.envrc` | 可保留；单一 `use flake`，职责明确。 |
| `.gitignore` | 需清理：多份 gibo 模板直接粘接，`Kotlin.gitignore` 结尾与下一段注释粘在同一行；`**/generated` 与 SymbolCraft 向源码目录生成的策略共同造成隐形源文件。 |
| `.vscode/settings.json` | 可保留；仅编辑器偏好。 |
| `.github/actions/setup-android-build/action.yml` | 可用；建议 action pin 到 commit SHA，并与 version catalog/compileSdk 共享 SDK 版本来源。 |
| `.github/workflows/android-release.yml` | 合并处理 P3-05：只做 release build、不执行 `check`/format；每个 branch 都创建可变 pre-release，职责过重。公开 keystore 按已确认约束处理。 |
| `build.gradle.kts` | 可保留；根 plugin declaration 简洁。 |
| `settings.gradle.kts` | 可保留；若无只能从 JitPack 获取的依赖，应删除 JitPack 以缩小供应链面。 |
| `androidApp/build.gradle.kts` | 公开签名 fallback 是确认特性；其余可保留。建议把版本/SDK 常量集中，避免与 CI、shared 重复。 |
| `desktopApp/build.gradle.kts` | 可保留；SemVer 校验清晰。缺少 macOS/Linux 分发是否为产品选择需另行确认。 |
| `shared/build.gradle.kts` | 需改：单 module 承担 data、UI、生成器和全部 feature；SymbolCraft 输出到 `src/commonMain/kotlin` 却由 gitignore 隐藏；手写 task 通过固定任务名挂依赖脆弱。对应 P2-08/目标架构。 |
| `gradle/libs.versions.toml` | 可用；存在 alpha/beta 核心依赖（Store5、Voyager、Material3），应配依赖更新与回归策略；若按 P2-04 删除 Store5 可同步瘦身。 |
| `gradle.properties` | 可保留；当前用户已有修改未触碰。`configuration-cache=false` 应记录具体阻塞插件，否则容易永久关闭。 |
| `gradle/wrapper/gradle-wrapper.properties` | 可保留。 |
| `gradle/wrapper/gradle-wrapper.jar` | 标准 wrapper 二进制；建议 CI 校验 wrapper checksum。 |
| `gradlew` | 标准生成脚本，可保留。 |
| `gradlew.bat` | 标准生成脚本，可保留。 |
| `treefmt.toml` | 可保留；建议 CI 只做 check 模式。 |
| `lefthook.yml` | 合并处理 P3-05：version code 修改不可靠、文件名管道不安全、空输入行为不明确。 |
| `flake.nix` | 可用；Android NDK 当前未见使用，`lint-staged` 也未见配置，可删无用依赖；`nixos-unstable` 已由 lock 固定。 |
| `flake.lock` | 生成锁文件，可保留。 |
| `LICENSE` | 可保留；同时被 about metadata 生成任务读取。 |
| `dummy.keystore` | 已确认的开源签名特性；不是 secret。建议在 README 声明信任模型。 |
| `shared/schemas/ddd.kc.data.local.AppDatabase/1.json` | Room v1 schema 可保留；后续分离 cache/history 与迁移时必须提交新 schema 和 migration test。 |

### data/model、settings 与基础类型

| 文件 | 结论 |
|---|---|
| `data/i18n/AppLanguage.kt` | 可保留；未来可把 locale tag 作为构造数据，减少 when。 |
| `data/model/Announcement.kt` | DTO 可保留；默认空 service/user/hash 会把缺字段伪装成合法对象，建议 API DTO 严格、domain model 非空。 |
| `data/model/Comment.kt` | DTO 可保留；同样建议与 domain model 分离。 |
| `data/model/Creator.kt` | 合并处理 P1-03；应提供 `CreatorKey`，URL 派生移到 Pawchive media resolver。 |
| `data/model/CreatorLink.kt` | `CreatorLink` 当前 API 不使用，转换函数完全无调用；删除，见 P3-01。 |
| `data/model/DM.kt` | DTO 可保留；大量 nullable/default 使 parser 失败不显性，重构时区分 wire/domain。 |
| `data/model/DiscordModels.kt` | 对应能力恒空；若删除 Pawchive Discord feature 则整体删除，见 P2-02。媒体扩展名与 `PostFile` 重复。 |
| `data/model/FlexibleLongSerializer.kt` | 高风险容错：任何非法时间都静默变 0；至少记录/测试非法值，理想方案是 wire type 显式解析结果。 |
| `data/model/Platform.kt` | 删除；历史站点抽象，见 P2-01。`defaultCdnUrl` 无调用。 |
| `data/model/PlatformSerializable.kt` | 删除；反向污染 domain，见 P1-08。 |
| `data/model/Post.kt` | 合并处理 P1-03；文件同时包含多个 DTO 和复杂 tags serializer，建议拆 wire DTO/serializer/domain。非法 tag shape 当前返回 null，可能隐藏上游变化。 |
| `data/model/PostFile.kt` | 可用；URL 构建属于 Pawchive adapter 而非通用 model。extension 集合与 Discord 重复。 |
| `data/model/QueryState.kt` | 合并处理 P2-07/P3-02；UI load state 和中文 error message 不应放在 data model package。 |
| `data/model/Tag.kt` | DTO 简单，可保留。 |
| `data/settings/AppSettings.kt` | 核心重构文件：P1-01、P1-06、P1-07、P2-01。双真相、巨型职责、空 active-platform setter、非原子保存、secret 明文。 |
| `data/settings/DownloadSettings.kt` | 可保留；适合迁到 settings domain。 |
| `data/settings/ThemeMode.kt` | 可保留。 |

### local、cache 与 repository

| 文件 | 结论 |
|---|---|
| `data/local/AppDatabase.kt` | 合并处理 P1-05；cache/history 生命周期混在同一 DB。 |
| `data/local/dao/CacheDao.kt` | API 可用；prefix LIKE 依赖调用方正确转义 `%/_`，当前 key 可能含用户 query，建议不用 LIKE 拼 prefix或统一 escape。 |
| `data/local/dao/HistoryDao.kt` | 可用；trim 与 upsert 应放在同一 transaction，避免并发超限。 |
| `data/local/entity/CacheEntity.kt` | 可保留；应增加 schema/version/content type，而非只存裸 JSON。 |
| `data/local/entity/CreatorHistoryEntity.kt` | 合并处理 P1-05；platform 字段在单 Pawchive 化后删除，JSON snapshot 需版本策略。 |
| `data/local/entity/PostHistoryEntity.kt` | 同上。 |
| `data/store/StoreQuery.kt` | 核心重构文件：P1-04、P2-04；stale 状态错误、坏 cache 不自愈、converter 用 `error()`。 |
| `data/repository/ActivityHistoryRepository.kt` | 合并处理 P1-05；反序列化失败逐条吞掉可接受但应清理坏行；`Dispatchers.Default` 默认值不适合 DB IO。 |
| `data/repository/ChunkedCache.kt` | 核心重构文件：P1-04、P2-05；无事务、无并发代际、无完整性校验；`readChunkedList` 无调用。 |
| `data/repository/CreatorRepository.kt` | 核心重构文件：忽略 platform、重复 encode/decode、空能力、手写特殊 cache 和本地搜索；见 P2-01/02/03/04。 |
| `data/repository/DiscordRepository.kt` | 整条调用链只会得到空列表；删除或真实实现，见 P2-02。 |
| `data/repository/PostRepository.kt` | 核心重构文件：忽略 platform、raw cache、两请求拼 creator page、重复 state mapping；见 P2-03/04/07。 |
| `data/repository/TagRepository.kt` | 结构相对简单；仍受 platform 幽灵参数、Store wrapper 和中文日志影响。 |
| `data/repository/TimeUtil.kt` | 可保留；更利于测试的做法是注入 `Clock`。 |

### network、media 与 translation

| 文件 | 结论 |
|---|---|
| `data/network/AuthRequiredException.kt` | 可保留为 typed error，但用户文案移出 exception。 |
| `data/network/KcApiClient.kt` | 686 行核心拆分对象；P2-02/03、P3-03。空 API、HTML/JSON/session/media 混杂、手拼 path、404→空语义混淆。 |
| `data/network/KcApiException.kt` | `KcApiException` 可保留；`KcDdosGuardException` 无调用应删。 |
| `data/network/KcHttpClient.kt` | P1-07/P3-03；日志隐私与错误 Accept。建议统一 timeout、redaction、request id。 |
| `data/network/KcSessionStore.kt` | 单 Pawchive 后删除 platform 参数；KSafe 是正确方向，但需 backup 规则和 session 格式测试。 |
| `data/network/QueryErrors.kt` | error mapping 思路可保留；不要把任意 throwable message 直接变用户文案。 |
| `data/media/CachedImageHttpClient.kt` | 去重思路可用；全局 Koin lookup、完整 URL 日志和独立 Ktor cache 增加隐式耦合。需确认同一 saved call 被多 consumer 读取的契约测试。 |
| `data/media/ImageProgressTracker.kt` | 可用；256 项按 map 迭代顺序淘汰不是真 LRU，完成后也不主动清理。 |
| `data/translation/GoogleTranslationClient.kt` | P1-07；原文进入 GET query/log。非正式 `gtx` endpoint 是脆弱外部依赖，应有 provider contract test/降级提示。 |
| `data/translation/KtorTranslationPort.kt` | 分派清楚，可保留；命名改为 gateway/registry 更贴合职责。 |
| `data/translation/MicrosoftTranslationClient.kt` | 非正式 Edge token endpoint，稳定性与条款风险应显式；每次翻译都取 token，缺少缓存/过期模型。 |
| `data/translation/OpenAiCompatibleTranslationClient.kt` | 可用但应使用 typed request/response DTO；response body error 泄露见 P1-07，`stripThinkTag` 是 provider-specific 补丁。 |
| `data/translation/OpenAiTranslationConfig.kt` | API key 不应作为普通可复制 data class 字段长期流经 UI/state；默认模型属于可更新配置。 |
| `data/translation/TranslationBlockExtractor.kt` | 算法复杂且无测试；wrapper 重建可能生成结构合法但语义变化的 HTML。先加 fixture/property test。 |
| `data/translation/TranslationChunkExecutor.kt` | 可用；Channel 容量=chunks + Semaphore 组合偏复杂，`mapAsync(concurrency)`/worker pool 更直接；需 cancellation test。 |
| `data/translation/TranslationChunkPlanner.kt` | 可用；所谓 word limit 是估算字符/token混合，并不等于 provider token limit，命名应诚实。 |
| `data/translation/TranslationChunkTranslator.kt` | 合并处理 P2-06；catch-all 把 cancellation 也包装为 Failure 的风险需修正，`CancellationException` 必须重抛。 |
| `data/translation/TranslationEngine.kt` | 组合职责清楚；直接读取 AppSettings 镜像受 P1-06 影响，应接收 immutable settings。 |
| `data/translation/TranslationHttpTransport.kt` | 可保留；typed DTO 后 body 不应是裸 String。 |
| `data/translation/TranslationModels.kt` | 可保留；Failure 持有 Throwable 不宜进入长期 UI state/序列化。 |
| `data/translation/TranslationProvider.kt` | 可保留。 |
| `data/translation/TranslationProviderClient.kt` | P1-07；不要把 response body 放异常。 |
| `data/translation/TranslationRequest.kt` | 可保留；`TranslationPort` 建议独立文件，request normalization 应验证空 target/source。 |
| `data/translation/TranslationResultAligner.kt` | 核心正确性问题 P2-06；禁止猜测式均分。 |
| `data/translation/TranslationSettings.kt` | 可保留；与 AppSettings 默认常量重复，应有单一默认来源。 |
| `data/translation/TranslationTargetLanguage.kt` | 可保留。 |

### DI 与应用启动

| 文件 | 结论 |
|---|---|
| `di/AppModule.kt` | `GlobalContext` 和手工 stop 是全局 service locator；app shell 可保留一个 composition root，但 library/UI 不应反查全局 Koin。close 的 `runCatching` 静默吞错可记录。 |
| `di/DatabaseModule.kt` | 简单可保留；随 DB 分离调整。 |
| `di/NetworkModule.kt` | `AppSettings` 放在 NetworkModule 语义错误；shared client 让 Pawchive 与翻译共享日志策略，需拆。 |
| `di/RepositoryModule.kt` | 可用；JSON 宽松配置全局共享会让不同 endpoint 都静默 coerce，应按 adapter 配置。 |
| `di/ScreenModelModule.kt` | 参数通过 `params[0..5]` 极脆弱且无类型保护；route 变更容易运行时错位。单例 ScreenModel 也会把 tab 状态变成全局会话状态。 |
| `ui/app/KcApp.kt` | 核心启动问题 P1-06；`LocalActivePlatform` 删除，settings Ready 后再组成 app。 |

### 通用 UI、导航与状态

| 文件 | 结论 |
|---|---|
| `ui/components/AppFeedbackHost.kt` | CompositionLocal 默认 no-op 会让缺 provider 静默丢反馈；建议默认 fail-fast 或显式 `FeedbackSink`。并发 snackbar 当前互相 dismiss，需确认产品语义。 |
| `ui/components/BackTopAppBar.kt` | 空兼容文件，删除，见 P3-01。 |
| `ui/components/CreatorCard.kt` | 简单可保留；隐式读取 LocalAppSettings 使组件不纯，应传入 avatar URL。 |
| `ui/components/CreatorSearchCard.kt` | 可保留；同上，URL resolution 和 icon catalog 不应在叶子组件内 service locate。 |
| `ui/components/DmCard.kt` | UI/domain/翻译状态耦合偏深；硬编码 `Unknown`、`(no content)`、`翻译失败` 未国际化。 |
| `ui/components/ImageFallbacks.kt` | 可保留；纯展示组件。 |
| `ui/components/ImageLoadingProgress.kt` | 默认 `koinInject()` 隐式依赖；应由 route 提供 tracker/状态。 |
| `ui/components/KcPullRefreshBox.kt` | 可保留；薄封装。 |
| `ui/components/NavigationBackHandler.kt` | expect 声明可保留；命名中的 Platform 指运行平台，语义有效。 |
| `ui/components/NetworkImage.kt` | P1-07：失败日志含完整 URL；fallback 重试策略应有次数/host 约束测试。 |
| `ui/components/PageJumpFabMenu.kt` | 可保留；225 行对一个菜单偏大，可拆纯 page validation。 |
| `ui/components/PagedPostGrid.kt` | 可保留；它与 PaginatedList/各 ScreenModel 共同分担分页行为，建议收敛为统一 PagedWindow UI adapter。 |
| `ui/components/PaginatedList.kt` | 算法已有测试，是较好的边界；仍应减少基于滚动距离的隐式自动触发复杂度，并补 prepend 后位置保持集成测试。 |
| `ui/components/PlatformVideoPlayer.kt` | 267 行、第三方 player 细节和完整控制栏混合；回调处存在空 lambda。建议隔离 player adapter，避免 feature 直接依赖播放器状态。 |
| `ui/components/PostCard.kt` | 可保留；隐式 LocalAppSettings URL resolution 需上移。 |
| `ui/components/RouteTopBar.kt` | `BackTopAppBar` 已迁移完成；本文件可保留，但 content description 仍硬编码中文。 |
| `ui/components/ShareIconButton.kt` | 可保留。 |
| `ui/components/Skeleton.kt` | 可保留；固定 260dp 和重复 skeleton builder 可小幅收敛，不是架构阻断。 |
| `ui/components/platform/DownloadNaming.kt` | 有测试、清洗思路正确；需补 reserved Windows names、Unicode normalization、重复文件名和 traversal 测试。 |
| `ui/components/platform/PlatformBinaryFileWriter.kt` | P3-04；Stringly typed path 边界。 |
| `ui/components/platform/PlatformDirectoryPicker.kt` | expect 声明可保留。 |
| `ui/components/platform/PlatformFileSavePicker.kt` | expect 声明可保留。 |
| `ui/components/platform/PlatformTextActions.kt` | expect 声明可保留。 |
| `ui/components/platform/PlatformTextFileWriter.kt` | P3-04；与 binary writer 可共享 safe destination abstraction。 |
| `ui/icons/ServiceIcons.kt` | 资源解析失败静默回退到只有 patreon/fanbox，可能把 catalog 损坏伪装成正常；global object + Composable load 是隐式初始化。应在 app Ready 阶段加载并验证。 |
| `ui/navigation/KcLinkUriHandler.kt` | URL 解析手写 substring，不做 percent-decoding/规范化；应使用 URI parser。单 Pawchive 化后 route 不再带 Platform。 |
| `ui/navigation/MainScreen.kt` | 可用；tab index 是裸 Int，恢复出界时 `when` 无内容，建议 enum/sealed key。直接实例化 `Screen().Content()` 绕过导航生命周期语义，宜改普通 Composable。 |
| `ui/navigation/RootNavigator.kt` | 可保留；external link replay=1 可能在 Activity 重组/collector 重建后重复 push，事件应消费一次。 |
| `ui/navigation/RouteInstanceKey.kt` | 全局可变 Int 非线程安全且不可恢复；route 改为稳定资源 key 或 UUID，由 navigation 层生成。 |
| `ui/state/ContentTranslationState.kt` | 可保留；建议归属 translation feature，不是全局 UI state。 |
| `ui/state/PagerUiState.kt` | 未使用，删除。 |
| `ui/state/PaginationReducer.kt` | 当前最接近可复用核心，测试也较好；但没有 request generation，`hasMore` 同时被 prepend/append 复用语义模糊。作为 P2-07 重写起点。 |
| `ui/state/ScrollPosition.kt` | 可保留；应进入 saveable state 或 route-scoped state，而非 singleton 普通 var。 |
| `ui/i18n/AppLocale.kt` | P2-11；`key(localeTag)` 还触发 P1-01。 |
| `ui/theme/KcTheme.kt` | 可保留；受 locale/settings 启动门重构影响较小。 |
| `ui/platform/PlatformShortcutManager.kt` | 单 Pawchive 化后不应接收站点 Platform；若只剩一个固定 shortcut，可直接提供 `pinPawchiveShortcut` 或删除动态 manager。 |

### about、history、settings 与 more feature

| 文件 | 结论 |
|---|---|
| `ui/pages/about/AboutLibrariesJsonLoader.kt` | P2-09；删除静默空 JSON 与平台 fallback。 |
| `ui/pages/about/AboutLibrariesRouteScreen.kt` | 可保留；应显示加载错误而不是消费伪空 JSON。 |
| `ui/pages/about/AboutRouteScreen.kt` | 421 行同时处理日志导出、复制、导航和 license UI；拆 section 与 export use case。 |
| `ui/pages/history/HistoryRouteScreen.kt` | P1-05/P2-07/P1-08；Composable 直连 repo、无 loading/error、传整个历史列表进 route。 |
| `ui/pages/settings/SettingsDraft.kt` | 验证职责清晰；错误字符串需资源化，URL 解析不能只测 prefix，HTTP 假支持见 P2-10。 |
| `ui/pages/settings/SettingsLabels.kt` | 可保留；纯资源映射。 |
| `ui/pages/settings/SettingsRouteScreen.kt` | 薄 wiring 可保留；理想状态下注入 SettingsScreenModel，不直接传 AppSettings。 |
| `ui/pages/settings/SettingsScreen.kt` | 核心问题 P1-01，且 592 行；表单、保存 orchestration、dialog、平台 picker 混合。 |
| `ui/pages/settings/components/SettingsDropdownField.kt` | 可保留。 |
| `ui/pages/settings/components/SettingsSwitchRow.kt` | 可保留。 |
| `ui/pages/settings/components/SettingsWidgets.kt` | 318 行“组件杂物箱”；按 section/输入类型拆文件，避免继续增长。 |
| `ui/pages/more/FavoritesScreen.kt` | 可保留；route 仍把完整 favorites 列表传详情，见 P1-08。 |
| `ui/pages/more/FavoritesScreenModel.kt` | 两个并行请求思路可用；重复 load 无 job guard、旧响应可覆盖，见 P1-02；部分失败合并成单 message。 |
| `ui/pages/more/LoginScreen.kt` | 无入口且业务语义已被 session editor 替代，删除，见 P2-02/P3-01。 |
| `ui/pages/more/LoginScreenModel.kt` | 删除；所谓 login 只保存 password 为 session。 |
| `ui/pages/more/MoreScreen.kt` | 直接注入 SessionStore 与 ScreenModel，硬编码英文 session dialog；应由 account/session feature state 驱动。 |
| `ui/pages/more/MoreScreenModel.kt` | 可收敛为 SessionScreenModel；logout 清两个 cache 的协调应在 session repository transaction/use case。 |

### creators、post 与 Discord feature

| 文件 | 结论 |
|---|---|
| `ui/pages/creator/CreatorListRouteScreen.kt` | 可保留为纯列表；route 传整个 creators list 见 P1-08。 |
| `ui/pages/creator/CreatorPagerUiState.kt` | P1-03；全部 map/set 改用 `CreatorKey`。Unsupported/Error 状态缺失见 P2-07。 |
| `ui/pages/creator/CreatorRouteScreen.kt` | 761 行巨型页面；P1-03/P2-02/P2-08。打开即并发加载所有 tab 和空能力，LaunchedEffect key 不完整。 |
| `ui/pages/creator/CreatorScreenModel.kt` | 759 行“上帝 ScreenModel”；管理 6 类资源、分页、翻译、收藏和 pager。大量 cancellation 吞噬、错误只写日志、id key 冲突。应拆 feature child state/reducer。 |
| `ui/pages/creators/CreatorSearchScreenModel.kt` | 本地 filter/paging 可保留思路；UI resource label 函数不应放 ScreenModel 文件。受 P1-02/09，且 repository 已有重复 `searchCreators` 实现却未复用。 |
| `ui/pages/creators/CreatorsScreen.kt` | 316 行可拆 filter bar/grid；singleton model + LocalActivePlatform 在单 Paw 化后简化。 |
| `ui/pages/post/PostDetailPagingMerge.kt` | 有测试；去重裸 id 错误见 P1-03。修成 `PostKey` 后可并入统一 reducer。 |
| `ui/pages/post/PostPagerUiState.kt` | P1-03；所有 per-post map/set 改 `PostKey`。 |
| `ui/pages/post/PostPagingContext.kt` | 可保留 concept；删 PlatformSerializable，使用 typed query spec，不用字符串 period/source。 |
| `ui/pages/post/PostRouteScreen.kt` | 1257 行最大热点；下载、媒体、评论、导航、历史、翻译、收藏全部混在 route，P1-08/P2-08。优先拆。 |
| `ui/pages/post/PostScreenModel.kt` | 565 行热点；P1-03/P1-09。loaded/loading/comment/translation/favorite 全用裸 id；favorite 双击竞态与全局 `favoriteStatusDisabled` 会在重新登录后仍禁用。 |
| `ui/pages/discord/DiscordChannelRouteScreen.kt` | 上游恒空，正常 UI 不可达；若删除 Discord 能力则删除本文件。 |
| `ui/pages/discord/DiscordChannelScreenModel.kt` | 上游恒空；删除或在真实 API 出现时重写。当前 reducer 仍用裸 message id。 |

### works、DM、tags 与 recent feature

| 文件 | 结论 |
|---|---|
| `ui/pages/works/WorksScreen.kt` | 537 行把三个 tab 实现在一文件并直接注入三个 singleton model；拆为 Popular/Search/Tags route content。存在只读 field 的空 `onValueChange`。 |
| `ui/pages/works/WorksScreenModel.kt` | 仅保存 tab/scroll；scroll 是普通 var，不可 save/restore。可被 Main/route saveable state 替代，可能无需 ScreenModel。 |
| `ui/pages/works/PostSearchScreenModel.kt` | P1-02 直接证据；分页逻辑与 DM/Popular 重复。旧 search job 无法取消实际请求。 |
| `ui/pages/dm/DmScreen.kt` | 在 Composable 内实现 translation orchestration，与 DmSearch/Recent 两个 model 混合；应移入 DM feature ScreenModel。 |
| `ui/pages/dm/DmSearchScreenModel.kt` | 比 PostSearch 的 job 管理稍好（search 是 suspend），但 append/jump 仍独立 launch，query 读取时机可串页；重复状态机见 P2-07。 |
| `ui/pages/recent/RecentDMsScreenModel.kt` | 使用 reducer是正确方向；DM key fallback 到 content 会把不同消息合并，应定义稳定 `DmKey`。 |
| `ui/pages/recent/PopularPostsScreenModel.kt` | P1-02 热点；日期/周期请求无取消，boundary 参数被忽略，英文 enum label 未资源化。 |
| `ui/pages/tagposts/TagPostsScreen.kt` | 薄 route 可保留；route key/Platform 参数随全局重构删除。 |
| `ui/pages/tagposts/TagPostsScreenModel.kt` | 使用 reducer可作为迁移样板；仍缺 request generation/cancellation，Post key 只用 id。 |
| `ui/pages/tags/TagsScreen.kt` | 401 行主要因为自定义流式布局测量与缓存；优先验证性能收益，否则改用成熟 FlowRow/Lazy layout，减少手写布局状态。 |
| `ui/pages/tags/TagsScreenModel.kt` | 把像素宽度、textStyleHash 等 UI layout cache 放 ScreenModel，跨 configuration/font scale 容易陈旧；布局缓存应属于 Compose remember。重复 load job 无 guard。 |
| `ui/pages/imageviewer/ImageViewerScreen.kt` | 234 行可保留；route 携带所有 URL 见 P1-08，完整 URL 也不应进入可保存导航 state。 |

### Android/desktop actual 与应用入口

| 文件 | 结论 |
|---|---|
| `androidApp/src/main/kotlin/ddd/kc/android/KcApplication.kt` | 启动顺序可用；AboutLibraries 全局 context holder 在 P2-09 后删除。 |
| `androidApp/src/main/kotlin/ddd/kc/android/MainActivity.kt` | replay=1 external link 可重复导航；shortcut 调用 no-op active platform。1GiB Coil cache 对移动端偏大，应按磁盘比例/产品需求设置。 |
| `desktopApp/src/desktopMain/kotlin/ddd/kc/desktop/Main.kt` | 可保留；同样使用 1GiB 固定 cache。DI shutdown 只挂窗口 close，异常退出依赖进程清理。 |
| `desktopApp/src/desktopMain/kotlin/ddd/kc/desktop/DesktopPlatformModule.kt` | P1-05：DB 在 cache 目录；配置目录手拼 Unix path，不适配 Windows/macOS app-data。应引入平台目录 resolver。 |
| `shared/src/androidMain/kotlin/ddd/kc/AndroidPlatformModule.kt` | P1-05/P1-07：history DB 在 cache，secret/backup 边界不明。 |
| `shared/src/androidMain/kotlin/ddd/kc/data/model/PlatformSerializable.android.kt` | 随 common expect 删除，见 P1-08。 |
| `shared/src/desktopMain/kotlin/ddd/kc/data/model/PlatformSerializable.desktop.kt` | 同上。 |
| `shared/src/androidMain/kotlin/ddd/kc/data/network/KcHttpClient.android.kt` | 薄 actual 可保留；未来 Pawchive/translation client 拆配置。 |
| `shared/src/desktopMain/kotlin/ddd/kc/data/network/KcHttpClient.desktop.kt` | 同上。 |
| `shared/src/androidMain/kotlin/ddd/kc/ui/components/NavigationBackHandler.android.kt` | 可保留。 |
| `shared/src/desktopMain/kotlin/ddd/kc/ui/components/NavigationBackHandler.desktop.kt` | 可保留；文件级 suppress 应跟踪上游 API，避免永久压制。 |
| `shared/src/androidMain/kotlin/ddd/kc/ui/i18n/AppLocale.android.kt` | P2-11；composition 期间写全局 Locale。 |
| `shared/src/desktopMain/kotlin/ddd/kc/ui/i18n/AppLocale.desktop.kt` | P2-11；同样写全局 Locale。 |
| `shared/src/androidMain/kotlin/ddd/kc/ui/theme/KcTheme.android.kt` | 可保留。 |
| `shared/src/desktopMain/kotlin/ddd/kc/ui/theme/KcTheme.desktop.kt` | 可保留；与 Android 完全相同，若 API KMP 可直接 common 化。 |
| `shared/src/androidMain/kotlin/ddd/kc/ui/platform/PlatformShortcutManager.android.kt` | P2-01；动态站点 shortcut 已无意义。字符串 label 未资源化，request 结果未反馈。 |
| `shared/src/desktopMain/kotlin/ddd/kc/ui/platform/PlatformShortcutManager.desktop.kt` | `canPin=false` 与 no-op 是合理 platform unsupported 实现；单 Pawchive 后可用 capability 删除 UI 分支或整个功能。 |
| `shared/src/androidMain/kotlin/ddd/kc/ui/components/platform/PlatformBinaryFileWriter.android.kt` | P3-04；先删同名旧文件再创建，后续写失败会丢失旧文件；错误原因被压成固定字符串。应临时文件/原子替换（受 SAF 能力限制时明确提示）。 |
| `shared/src/desktopMain/kotlin/ddd/kc/ui/components/platform/PlatformBinaryFileWriter.desktop.kt` | P3-04；需 canonical path 校验和原子写。 |
| `shared/src/androidMain/kotlin/ddd/kc/ui/components/platform/PlatformDirectoryPicker.android.kt` | persistable permission 失败只 `printStackTrace`，仍把 URI 当成功返回，之后保存必然失败；应返回 typed failure。 |
| `shared/src/desktopMain/kotlin/ddd/kc/ui/components/platform/PlatformDirectoryPicker.desktop.kt` | 异常被 `getOrNull` 伪装成用户取消；应区分 Cancelled/Failure。与 save picker 重复 AWT helper。 |
| `shared/src/androidMain/kotlin/ddd/kc/ui/components/platform/PlatformFileSavePicker.android.kt` | 可保留；返回裸 String 应改 typed Uri destination。 |
| `shared/src/desktopMain/kotlin/ddd/kc/ui/components/platform/PlatformFileSavePicker.desktop.kt` | 异常同样被伪装成取消；mimeType 参数未用于 filter。 |
| `shared/src/androidMain/kotlin/ddd/kc/ui/components/platform/PlatformTextActions.android.kt` | 可保留；失败仅 Boolean，无法展示原因。 |
| `shared/src/desktopMain/kotlin/ddd/kc/ui/components/platform/PlatformTextActions.desktop.kt` | 同上。 |
| `shared/src/androidMain/kotlin/ddd/kc/ui/components/platform/PlatformTextFileWriter.android.kt` | 与 binary writer 大量重复且非原子；抽共享 writer core。 |
| `shared/src/desktopMain/kotlin/ddd/kc/ui/components/platform/PlatformTextFileWriter.desktop.kt` | 同上；P3-04。 |
| `shared/src/androidMain/kotlin/ddd/kc/ui/pages/about/AboutLibrariesAndroidContextHolder.kt` | 删除，P2-09；全局 Context 是多余的资源访问旁路。 |
| `shared/src/androidMain/kotlin/ddd/kc/ui/pages/about/AboutLibrariesJsonLoader.android.kt` | 删除，P2-09；异常静默。 |
| `shared/src/desktopMain/kotlin/ddd/kc/ui/pages/about/AboutLibrariesJsonLoader.desktop.kt` | 删除，P2-09；候选路径和异常静默掩盖打包错误。 |

### 测试源码

| 文件 | 结论 |
|---|---|
| `shared/src/commonTest/kotlin/ddd/kc/fake/TestFixtures.kt` | 可保留；命名 `fake` 不准确，建议 `testutil`；读取失败 fail-fast 是正确的。 |
| `shared/src/desktopTest/kotlin/ddd/kc/data/network/KcApiClientTest.kt` | fixture 覆盖有价值，但 329 行单测试承担 endpoint、parser、model URL 多职责；拆 adapter contract tests。live test 默认跳过，不计稳定覆盖。 |
| `shared/src/desktopTest/kotlin/ddd/kc/data/settings/AppSettingsTest.kt` | 只测 setter/flow happy path；缺 init、原子 save、迁移、并发和 secret。 |
| `shared/src/desktopTest/kotlin/ddd/kc/data/store/StoreQueryTest.kt` | P1-04：缺最终 `isStale=false` 与 corrupted cache；因此现有 bug 未被发现。Fake DAO 的 LIKE 行为是近似实现，不足以验证 SQL。 |
| `shared/src/desktopTest/kotlin/ddd/kc/ui/components/PaginatedListTest.kt` | 纯函数测试较好；补真实 Lazy grid prepend/scroll 集成。 |
| `shared/src/desktopTest/kotlin/ddd/kc/ui/components/platform/DownloadNamingTest.kt` | 可保留；补 P3-04 边界。 |
| `shared/src/desktopTest/kotlin/ddd/kc/ui/navigation/MainScreenTest.kt` | 覆盖过浅；deep link 只 assertNotNull，未断言 route key/service/creator/post、恶意/编码 URL。 |
| `shared/src/desktopTest/kotlin/ddd/kc/ui/pages/post/PostDetailPagingMergeTest.kt` | 测试本身清楚，但把裸 post id 当正确身份，固化了 P1-03 bug；改为跨 service/creator collision case。 |
| `shared/src/desktopTest/kotlin/ddd/kc/ui/state/PaginationReducerTest.kt` | 当前最扎实的 state test；可作为统一分页 reducer 的迁移基础。 |

### Manifest、Android 资源与 ProGuard

| 文件 | 结论 |
|---|---|
| `androidApp/src/main/AndroidManifest.xml` | P1-07/P2-10/P3-06；backup、HTTP、App Link 和 Lint warning。 |
| `androidApp/proguard-rules.pro` | 空文件不是问题；release shrink 依赖库 consumer rules。应至少有 release smoke test，防止序列化/Room/DI 被裁剪后才暴露。 |
| `androidApp/src/main/res/values/colors.xml` | 可保留。 |
| `androidApp/src/main/res/values/strings.xml` | 可保留；shortcut label 只有英文，若支持中文应补 locale resource。 |
| `androidApp/src/main/res/values/themes.xml` | 可保留；启动主题没有 splash 定制属于产品项。 |
| `androidApp/src/main/res/xml/shortcuts.xml` | P2-01：platform extra 已无效；category=conversation 与实际内容不符，建议固定普通 shortcut 或删除。 |
| `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | P3-06：minSdk 29 下 v26 qualifier 冗余，缺 monochrome。 |
| `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | 同上。 |
| `androidApp/src/main/res/mipmap-hdpi/ic_launcher.png` | Lint 报告图标填满方形；修正资产。 |
| `androidApp/src/main/res/mipmap-mdpi/ic_launcher.png` | 同上。 |
| `androidApp/src/main/res/mipmap-xhdpi/ic_launcher.png` | 同上。 |
| `androidApp/src/main/res/mipmap-xxhdpi/ic_launcher.png` | 同上。 |
| `androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png` | 同上。 |
| `androidApp/src/main/res/mipmap-hdpi/ic_launcher_round.png` | 与普通 icon 内容重复且不圆，P3-06。 |
| `androidApp/src/main/res/mipmap-mdpi/ic_launcher_round.png` | 同上。 |
| `androidApp/src/main/res/mipmap-xhdpi/ic_launcher_round.png` | 同上。 |
| `androidApp/src/main/res/mipmap-xxhdpi/ic_launcher_round.png` | 同上。 |
| `androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png` | 同上。 |
| `androidApp/src/main/res/mipmap-hdpi/ic_launcher_foreground.png` | adaptive foreground 可保留；与新 icon/monochrome 一并重导出。 |
| `androidApp/src/main/res/mipmap-mdpi/ic_launcher_foreground.png` | 同上。 |
| `androidApp/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png` | 同上。 |
| `androidApp/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png` | 同上。 |
| `androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png` | 同上。 |

### Compose resources、图标源与 fixture

| 文件 | 结论 |
|---|---|
| `shared/src/commonMain/composeResources/values/strings.xml` | key 集完整；仍有未使用的旧多平台/Kemono/Coomer/shortcut/login 文案，单 Paw 化时清理。 |
| `shared/src/commonMain/composeResources/values-zh-rCN/strings.xml` | 与英文 key 完全一致；同样清旧文案。 |
| `shared/src/commonMain/composeResources/files/kc-service-icons.json` | 应成为 Pawchive service catalog 的唯一来源并加 schema test；当前代码另有 patreon/fanbox fallback，形成双真相。 |
| `shared/src/commonMain/composeResources/files/aboutlibraries.json` | 生成资源可保留；CI 应重新生成后 diff/check，且 release 验证非空，见 P2-09。 |
| `shared/src/commonMain/composeResources/drawable/app_logo_foreground.png` | 被 Compose about/UI 使用的位图资产，可保留。 |
| `assets/dlsite.svg` | SymbolCraft 本地图标源，可保留；生成任务应验证输出。 |
| `assets/fantia.svg` | 同上。 |
| `shared/src/commonTest/resources/fixture/pawchive.st:dms:search-test.html` | 可保留；当前只覆盖空 DM 结果，必须增加含 DM 卡片 fixture。冒号文件名在 Windows checkout/工具链上不兼容，建议改安全命名。 |
| `shared/src/commonTest/resources/fixture/pawchive.st:patreon:user:3295915.html` | 有价值的 scraper fixture；同样改安全文件名并记录抓取日期/来源结构版本。 |
| `shared/src/commonTest/resources/fixture/pawchive.st:patreon:user:3295915:tags.html` | 同上。 |
| `shared/src/commonTest/resources/fixture/pawchive.st:posts:popular.html` | 同上。 |
| `shared/src/commonTest/resources/fixture/pawchive.st:posts:service-patreon.html` | 同上。 |
| `shared/src/commonTest/resources/fixture/pawchive.st:posts:tag-nsfw.html` | 同上。 |
| `shared/src/commonTest/resources/fixture/pawchive.st:posts:tags.html` | 同上。 |

### 通用工具

| 文件 | 结论 |
|---|---|
| `utils/TemplateStringUtils.kt` | 简单可保留；未知 token 原样保留是合理策略，应补转义、重复 token 和未知 token 单测。 |
| `utils/TextWhitespace.kt` | 简单可保留；名称应说明只折叠三行以上空行。 |
| `utils/logging/KcLog.kt` | runtime ring buffer 思路可用；P1-07 要求在进入 writer 前统一脱敏。全局 `initialized`/writer 设置应线程安全且测试重复 init。 |
| `utils/logging/LogSummaries.kt` | post summary 可保留；`summarizeThrowable`、`summarizeUrl` 无调用，后者即使使用也不做脱敏，应删除。 |

以上台账覆盖当前 244 个 tracked files；同类密度资源逐个列出，未把 `build/`、`.gradle/`、`.direnv/` 和被忽略的 SymbolCraft 生成 Kotlin 当作手写源文件。
