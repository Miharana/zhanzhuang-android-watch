# 站桩 · Zhan Zhuang — 产品与技术设计

日期：2026-07-31

状态：已批准
首发版本：0.1.0（手机 version code 1000001；手表 version code 2000001）

## 1. 目标

为 Android 手机与 Pixel Watch 4 制作一款完全免费、无广告、无账号、无云端服务的站桩计时应用。应用支持 15 分钟至 3 小时训练、固定间隔震动提醒、训练中心率采集、本地历史统计，并把训练记录写入 Health Connect。

首发成功标准：

- Pixel Watch 4 在熄屏、离开手机和短暂断连时仍能可靠完成最长 3 小时训练。
- 开始、间隔和结束提醒使用不同震动节奏，不播放声音。
- 手表采集的心率与实际训练时长能够无重复地同步到手机。
- 用户拒绝心率或 Health Connect 权限时，基础计时仍可使用，数据不丢失。
- 手机与手表界面提供简体中文和英文。
- 通过单元测试、设备测试、release 构建和 Google Play 内部测试轨道验证。

## 2. 首发范围

### 包含

- 训练时长：15–180 分钟，5 分钟步进；快捷值 15、30、45、60、90、120、180 分钟。
- 提醒间隔：5、10、15、20、30 分钟；默认 10 分钟。
- 默认训练时长：30 分钟。
- 手机和手表均可发起、暂停、继续、提前结束训练。
- 手表在线时使用 Wear OS Health Services 采集心率。
- 手机本地训练列表、单次详情、今日/本周/本月累计时长。
- 单次详情展示平均、最低、最高心率和简洁心率曲线；没有心率时明确标记“未记录”。
- Health Connect 写入权限流程；首发不读取其他应用的健康数据。
- 手表到手机的离线队列与重试同步。
- Wear OS Ongoing Activity 和前台健康服务。
- Google Play 中英文商店文案、手机/手表截图、功能图片与隐私政策。

### 不包含

- 账号、云同步、社交、排行榜、广告、付费功能或分析追踪 SDK。
- 卡路里估算、GPS、动作识别、姿势纠正、语音或提示音。
- 分阶段口诀、自定义任意提醒分钟数、训练备注、连续打卡。
- iOS、Apple Watch、Web 或其他健康云 API。

## 3. 平台与发布身份

- 应用名称：站桩 · Zhan Zhuang
- application ID：`app.zhanzhuang.timer`
- 手机最低版本：Android 9 / API 28；目标与编译 API 36。
- 手表最低版本：Wear OS 4 / API 33；目标与编译 API 36。
- 手机与手表使用相同 application ID 和签名证书，以同一 Play 商店条目管理不同设备形态。
- 手表声明为 standalone，因为计时、提醒和心率采集无需手机即可完成。
- 首次上传前必须在 Play Console 确认 `app.zhanzhuang.timer` 尚未被占用；一旦创建 Play 应用，包名不再更改。

目标 API 36 是主动选择，以满足 2026-08-31 起手机新应用的 Play 要求，并提前处理 Wear OS 6 的健康权限模型。Wear OS 4–5.1 使用带 `maxSdkVersion=35` 的旧传感器权限，Wear OS 6+ 使用 `READ_HEART_RATE` 与后台健康数据权限。

## 4. 架构

采用原生 Kotlin、Gradle Kotlin DSL 和版本目录，模块如下：

- `:mobile`：手机 Compose UI、Room 数据库、Health Connect、手机计时服务和 Data Layer 同步。
- `:wear`：Compose for Wear OS UI、Health Services、训练前台服务、Ongoing Activity、本地恢复与 Data Layer 同步。
- `:core:model`：训练配置、训练状态、训练记录、心率样本和同步消息模型。
- `:core:domain`：计时、暂停、提醒调度、统计和冲突合并规则，不依赖 Android UI。
- `:core:testing`：时钟、振动器、健康客户端和同步传输的测试替身。

依赖方向为 UI → domain/model；平台 API 通过接口注入。手机与手表不能直接依赖对方模块。

## 5. 训练所有权与状态机

状态：`Idle → Starting → Running ↔ Paused → Completing → Completed`，任意活动状态可进入 `Cancelled` 或 `Interrupted`。

每次训练生成 UUID，并记录创建设备、开始墙上时钟、开始单调时钟、累计暂停时长、目标时长和提醒间隔。

- 手表发起：手表是训练所有者，独立计时、震动和采集心率。
- 手机发起且手表在线：手机发送带 UUID 的启动命令；手表确认后成为训练所有者。5 秒未确认时手机查询一次手表状态，再等待 5 秒；仍无响应则发送取消标记并切换为手机计时。手表稍后重连时以该取消标记和手机所有权版本终止任何迟到的同 UUID 会话。
- 手机发起且手表离线：手机成为训练所有者，仅记录时长，不伪造心率。
- 同一时刻只允许一个活动训练。重复命令按 UUID 幂等处理。
- 倒计时使用单调时钟计算，不依赖界面刷新或系统墙上时间。
- 暂停期间不计入实际时长，不触发间隔提醒，并暂停 Health Services 训练。
- 用户提前结束时保存实际完成时长；少于 1 分钟的误触训练不写入 Health Connect，但保留本地可删除记录。

## 6. Wear OS 训练执行

手表训练由 health 类型的前台服务持有，并通过 Ongoing Activity 提供返回入口。Health Services 使用 `ExerciseClient`、`ExerciseType.MEDITATION` 和 `HEART_RATE_BPM`，不启用 GPS、距离或卡路里。

UI 退出、屏幕熄灭或进入环境模式不会停止服务。服务定期把可恢复快照写入手表本地存储。进程被系统重建后根据快照和单调时钟恢复；设备重启导致 Health Services 会话终止时，将记录标记为 `Interrupted` 并保存截至最后可靠时间的数据，不虚构重启期间的心率。

震动模式：

- 开始：一次短震。
- 间隔：两次短震。
- 完成：一长两短。
- 暂停/继续：一次轻短震，仅用于确认操作。

提醒以训练的有效运行时间为基准。若系统延迟唤醒，不连续补发错过的多次震动，只触发当前应到提醒并推进到下一个间隔。

## 7. 界面设计

### 视觉系统

颜色与材质参考 `/Users/pema/Documents/uart/urticad-website/src/public/css/miharana-gold-web.css` 和 Urticad Gold System，但针对小屏与健康场景做更克制的 Android 适配：暖纸、土色、反射金与极少量闪光，不使用青紫渐变、渐变文字或大面积金色铺底。

核心颜色令牌：

- 暖纸背景 `#FBF8F1`，抬升表面 `#FFFDF8`，浅土凹面 `#EBE3D2`，奶油层 `#F4EEE1`。
- 泥土正文 `#5B4F3B`，柔和土色 `#998A72`。
- 反射金 `#C6A867`，深金 `#A9863F`，暗金 `#8A6B30`，浅金 `#F0E4C4`。
- 手表深土背景 `#15110B`，抬升深土表面 `#241C12`，暖纸文字 `#FBF8F1`，闪光高光 `#FFF4D6`。
- 银灰仅用于来源、权限和同步状态面板：`#EDEAE1` / `#BDB9AD`。

手机界面约 72% 暖纸/浅土背景、18% 泥土文字和结构、8% 金色关键动作与细线、2% 银灰或闪光状态。手表界面约 82% 深土背景、10% 暖纸文字、6% 金色进度与主操作、2% 闪光高光。比例是视觉占比目标，不要求逐像素计算。

金色仅用于品牌标记、主按钮、选中态、进度环和 1 px 分隔线。闪光只在训练开始和完成时播放一次 280–420 ms 的低幅径向微光或单点掠光；尊重系统“移除动画”设置，环境模式和低电量模式完全禁用闪光。所有正文和关键状态必须在没有金属渐变或动画时仍清晰可辨。

形状沿用 Urticad 的 6/10/16/24 dp 圆角与胶囊选择控件；阴影非常柔和，主要依赖细边线和色阶区分层级。Android 正文使用系统无衬线字体保证中英文可读性，品牌名称可在大尺寸标题使用系统衬线回退，但数字计时始终使用等宽数字特性。

### 手表

采用深土背景、高对比度暖纸文字和克制的反射金强调色，优先适配圆形 41 mm 与 45 mm 表盘。

1. 首页：大号“30 分钟”，旋转表冠或加减按钮以 5 分钟调整；提醒间隔为一行可横向滚动选项；底部主按钮“开始”。
2. 训练页：中心显示剩余时间和环形进度；下方显示当前心率及“下次提醒”；暂停和结束放在二级操作区，结束需要二次确认。
3. 完成页：实际时长、平均心率、最低/最高心率、同步状态；主操作为“完成”。
4. 权限页：在用户首次开始需心率的训练时才请求权限，拒绝后允许“继续但不记录心率”。

环境模式隐藏心率曲线和动画，只保留低频更新的剩余分钟与状态，降低烧屏和耗电风险。

### 手机

使用 Material 3，底部导航仅保留“训练”“历史”“设置”。

1. 训练：首页卡片展示时长、提醒间隔、手表连接状态与开始按钮。手表不可用时，开始按钮旁明确标注“本次不记录心率”。
2. 历史：顶部展示今日、本周、本月累计，下面按日期列出训练。仅统计实际运行时间。
3. 详情：显示开始时间、实际时长、设备来源、平均/最低/最高心率、心率曲线和 Health Connect 同步状态。
4. 设置：语言跟随系统、Health Connect 权限与重新同步、默认时长、默认提醒间隔、隐私说明与开源许可。

界面不使用伪医疗措辞，不宣称诊断、治疗、减肥或热量消耗效果。

## 8. 数据模型与同步

核心记录：

- `SessionRecord`：UUID、状态、所有者、开始/结束时间、目标/实际时长、暂停时长、提醒间隔、设备、心率摘要、Health Connect 状态、创建/更新时间。
- `HeartRateSample`：session UUID、时间戳、BPM、数据准确性。
- `UserPreferences`：默认时长、默认间隔、语言与已完成引导标志。
- `SyncOutbox`：事件 UUID、类型、载荷版本、尝试次数与下次重试时间。

进行中的低延迟控制使用 MessageClient；配置和完成记录使用持久化 DataClient/Channel 传输。完成记录先落地手表数据库，再进入 outbox；手机以 session UUID 和记录版本执行 upsert。只有手机确认持久化后，手表才清理已发送载荷。

冲突规则：训练所有者的状态与心率数据优先；较高记录版本优先；`Completed/Cancelled/Interrupted` 不被旧的活动状态覆盖。删除只在手机本地执行，不反向删除 Health Connect 之外的第三方数据。

## 9. Health Connect

手机是唯一 Health Connect 访问方。用户可以不授权，应用其余功能不受影响。

- 首选写入 `MindfulnessSessionRecord`，类型为 `MEDITATION`，标题为“站桩 / Zhan Zhuang”。
- 若设备的 `FEATURE_MINDFULNESS_SESSION` 不可用，则写入 `ExerciseSessionRecord` 的 other workout 兼容记录。
- 有有效心率样本且获得权限时，按训练时间范围写入 `HeartRateRecord`。
- 使用稳定的 client record ID 和 version，重复同步更新同一记录而非插入副本。
- 本地记录分别追踪 session 与 heart-rate 写入结果；部分失败可独立重试。
- 用户撤销权限后停止访问并保留本地数据；设置页提供重新授权和手动重试。

## 10. 异常处理

- 心率权限拒绝或腕上读取不可用：计时继续，心率显示“—”，完成记录标记未记录原因。
- Health Services 已有其他应用训练：不抢占现有训练，降级为仅计时并说明原因。
- 手机/手表断连：所有者继续训练；完成数据保留在 outbox，重连后同步。
- Health Connect 缺失、版本过旧或权限拒绝：本地保存，给出可操作提示，不循环弹窗。
- 手机发起命令超时：切换手机计时前显示明确状态，避免两端同时开始；迟到的手表命令通过 UUID 和所有权租约拒绝。
- 低电量：继续计时，遵循系统限制；不提高传感器采样率，不启用 GPS。
- 应用崩溃/进程重建：从持久化快照恢复；不能可信恢复时保存为中断记录。
- Health Connect 重复或区间冲突：按 client record ID 更新，记录错误供设置页重试，不删除其他应用数据。

## 11. 隐私与政策

- 健康数据仅保存在用户设备、配对设备和 Health Connect，不发送到开发者服务器。
- 仅请求运行功能所需的心率、后台健康数据、前台服务、通知和 Health Connect 写入权限。
- 不申请位置、联系人、广告 ID、互联网分析或账户权限；Data Layer 仅在配对设备之间工作。
- 首次请求健康权限前提供简短用途说明；设置页提供完整隐私政策入口。
- Play Console 健康应用声明、Data safety 表单和隐私政策必须与实际行为一致。

## 12. 测试策略

### 自动测试

- domain 单元测试：15/180 分钟边界、5 分钟步进、暂停、延迟唤醒、间隔不补发、时钟变化、提前结束。
- 同步测试：重复消息、乱序版本、断连重试、确认后清理、迟到启动命令和所有权冲突。
- 数据测试：Room 迁移、心率摘要、日/周/月统计、时区与夏令时。
- Health Connect 测试：功能可用/不可用、权限拒绝、部分写入失败、幂等更新。
- Compose UI 测试：中英文长度、字体缩放、圆屏边缘、TalkBack 内容描述和最小触控目标。

### 设备验证

- Pixel Watch 4 物理设备：15 分钟、默认 30 分钟及完整 3 小时熄屏训练。
- Wear OS 4、5、6 圆形模拟器：权限差异、后台服务和恢复。
- Android 9、14、16 手机：Health Connect 可用性与权限流程。
- 场景：手表离线启动、训练中断连/重连、手机启动但手表不可达、拒绝心率、拒绝 Health Connect、进程被杀、设备重启、低电量模式。

发布门槛：所有自动测试通过；release AAB 可构建且签名验证通过；Pixel Watch 4 三小时训练的最终计时误差不超过 2 秒，所有应触发提醒各触发一次，无重复 Health Connect 记录。

## 13. 发布方案

- 先进入 Google Play `internal` 轨道，不做分阶段 rollout；验证预发布报告和实体设备安装后再决定是否进入 closed/production。
- 启用 Play App Signing，本地仅保存上传密钥；密钥和密码不进入 Git。
- fastlane `supply` 分离 metadata 与二进制上传；中英文目录分别为 `zh-CN` 和 `en-US`。
- 商店素材至少包含手机截图、圆形 Wear OS 截图和 1024×500 功能图片；截图叙事依次突出“最长 3 小时”“安静震动提醒”“实时心率”“本地隐私”“Health Connect”。
- 首发 version name `0.1.0`、手机 version code `1000001`、Wear version code `2000001`；后续版本分别在各自百万段内递增，确保跨形态唯一。
- 构建并签名两个 AAB（手机与 Wear）；在同一 Play 应用条目中分别上传到手机内部测试轨道与 Wear OS 专用内部测试轨道。
- 上传前确认 Play Console 应用记录、包名、健康权限声明、开发者身份验证、服务账号权限与目标轨道。

## 14. 明确取舍

- 可靠性高于视觉动画：计时在服务/domain 层，UI 不持有真相。
- 隐私和零成本高于跨设备云恢复：不建设服务器。
- 精确原始数据高于热量推算：记录真实心率，不输出未经验证的能量消耗。
- 可降级高于强制授权：拒绝任何健康权限时仍能完成站桩计时。
- 首发克制高于功能堆叠：只做固定间隔与基础统计，复杂课程和习惯养成留待真实反馈验证。

## 15. 官方依据

- Health Services：<https://developer.android.com/health-and-fitness/health-services>
- Wear 健康权限：<https://developer.android.com/health-and-fitness/health-services/permissions>
- Health Connect 正念记录：<https://developer.android.com/health-and-fitness/health-connect/features/mindfulness>
- Wear 长时任务与 Ongoing Activity：<https://developer.android.com/training/wearables/get-started/creating>
- Wear OS 打包与发布：<https://developer.android.com/training/wearables/packaging>
- 2026 Google Play target API 要求：<https://developer.android.com/google/play/requirements/target-sdk>
