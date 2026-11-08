# 开发与验证

## Android 构建变体

| 变体 | 包名 | 图标标识 |
| --- | --- | --- |
| debug | `com.storyteller_f.ping.debug` | 蓝色 DEBUG ribbon |
| alpha | `com.storyteller_f.ping.alpha` | 橙色 ALPHA ribbon |
| release | `com.storyteller_f.ping` | 原始图标 |

使用 `./gradlew.bat :ping:assembleDebug`、`:ping:assembleAlpha` 或
`:ping:assembleRelease` 构建；macOS/Linux 使用 `./gradlew`。
三个包名可并存，数据和已设置的壁纸组件分别独立。
三种构建均只打包 Purism JNI 支持的 arm64-v8a、x86_64、x86，避免其他依赖
夹带未提供 Core 的 ABI，导致安装后无法加载原生库。
EasyLauncher 6.4.1 为 debug、alpha 的普通及圆形启动图标生成 ribbon。
启动图标使用浮萍/壁纸叶片设计，源文件和再生成说明见 [artwork](artwork/README.md)。
自适应图标分离纯色背景与带波纹镂空的矢量前景，普通、圆形 PNG 覆盖五种密度。

alpha 继承 release 的混淆、资源压缩和签名配置，版本名追加 `-alpha`，依赖模块
使用 release 变体；不启用 debug 的 LeakCanary。发布签名需要
`storyteller_f_sign_path`、`storyteller_f_sign_alias`、
`storyteller_f_sign_store_password`、`storyteller_f_sign_key_password` 环境变量。
本地未提供签名时，alpha/release 产物为 unsigned APK，须签名后才能安装。
CI 的 main 分支构建 alpha，有配置密钥时签名；标签构建签名 release，缺少密钥
则失败。CI 构建产物保存在 `ping/build/outputs/apk/` 对应变体目录。

## Purism Core Android 最小验证

`tools/purism-probe` 是独立的 Android NDK 对比程序，不修改应用的 Core 后端。
它从命令行 `--aar` 指定的外部官方 AAR 提取对比基线，与固定版本的
Purism Core 分别加载同一份 MOC3，输入相同动作参数，再比较输出。
Purism Core 固定为 `1069334965522df5d0b791e97f01e26b19c45456`，使用 v5 ABI。

应用现已通过 `purism-core` 模块构建新的 `libLive2DCubismCoreJNI.so`，
其原生计算实现为 Purism Core v6，桥接代码位于 `purism-core/src/main/cpp/bridge.cpp`。
旧的 `Core/android/Live2DCubismCore.aar` 和构建中的解包任务已删除。
应用仅保留 `purism-core/libs/cubism-java-api.jar`，它来自官方 Java R5 SDK
的 `classes.jar`，不含原生库；API 许可文本随 APK 的 assets 分发。
该 Java 接口仍是运行所需依赖，不代表整个 SDK 均已开源。
`cubism-framework` 模块编译仓库内置的 Framework 源码，固定在与 Java API 匹配的
`5-r.5`（`c2d4200`），匹配新版 Java API，并支持 Ren 所需的离屏绘制和混合模式。
源码、shader assets 和许可位于 `cubism-framework/upstream`，版本来源及更新规则见
[UPSTREAM.md](cubism-framework/upstream/UPSTREAM.md)。仓库不再使用 Git submodule，
普通 clone 即包含这些文件，CI 无需递归拉取子模块；Purism 源码仍由 CMake 按固定提交获取。
JNI 会同步离屏透明度、颜色、遮罩、部件映射及完整绘制顺序；不能只压缩顺序
编号来让旧渲染器避开越界，否则会丢失离屏绘制语义。
新版 Framework 的 shader assets 由模块打包，Application 提供资源加载回调。
Live2D 代码已移至主源码目录，壁纸服务、`.book` 导入及系统预览已恢复。

### 构建

需要 JDK 21、Python 3、Android SDK 的 adb、NDK 和 CMake/Ninja。
以下为 PowerShell 命令，从仓库根目录执行；将变量换成实际安装位置。
探针目前支持 ARM64 Android 设备，最低 API 26。

```powershell
$sdkRoot = $env:ANDROID_HOME
$ndkRoot = Join-Path $sdkRoot 'ndk/29.0.14206865'
$cmakeRoot = Join-Path $sdkRoot 'cmake/3.22.1/bin'
& "$cmakeRoot/cmake.exe" -S tools/purism-probe -B build/purism-probe -G Ninja `
  "-DCMAKE_MAKE_PROGRAM=$cmakeRoot/ninja.exe" `
  "-DCMAKE_TOOLCHAIN_FILE=$ndkRoot/build/cmake/android.toolchain.cmake" `
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release
& "$cmakeRoot/cmake.exe" --build build/purism-probe
python -m unittest discover -s tools/purism-probe -p test_motion.py
```

CMake 首次配置会获取固定提交的 Purism Core 源码。
应用自身仍使用 Gradle 构建。jksify 插件及 GitHub Packages 仓库已移除；
`common-ui` 和 `ext-func-compiler` 均使用 Maven Central 上的
`io.github.storytellerf` / `0.0.1-alpha2`，无需 GitHub 凭据。
该公开版本不再提供旧的 SimpleFragment，项目使用自己的 BindingFragment。
公开依赖要求 compileSdk 37；targetSdk 仍为 36。
发布签名使用 CI 已有的密钥文件路径与环境变量。

### 运行

可使用自己的模型目录，也可在仓库外获取官方示例：

```powershell
git clone https://github.com/Live2D/CubismNativeSamples.git "$env:TEMP/cubism-probe-samples"
git -C "$env:TEMP/cubism-probe-samples" checkout a3a64f6cb327aafce380949e0f54fe51c4d773d9
```

模型和 SDK 各自的许可继续适用；不将示例资源复制进本仓库。
取得测试设备的独占使用权后运行（将 `DEVICE_SERIAL` 替换为实际序列号）：

```powershell
python tools/purism-probe/run.py --serial DEVICE_SERIAL `
  --models "$env:TEMP/cubism-probe-samples/Samples/Resources" `
  --build build/purism-probe --ndk "$ndkRoot" `
  --aar PATH_TO_EXTERNAL_BASELINE_AAR `
  --samples 121 --report build/purism-probe/results.json
```

工具仅在设备的独立临时目录推送原生程序和模型；结束时清理该目录。
不安装应用，不改变壁纸。JSON 报告保存在忽略的构建目录，含 Core/模型
SHA256、逐模型帧数、动作数、数值误差和失败信息。任一模型失败时退出码非零。

### 2026-11-07 验证结果

使用上述固定提交的官方示例，在 ARM64 Android 真机上比较仓库内官方 Core
5.1.0 与 Purism Core 1.1.0/v5。每个动作在完整时长内等距采样 121 帧，
另对比每个模型的默认姿态。

| 模型 | 动作数 | 帧数 | 最大顶点坐标差 | 结果 |
| --- | ---: | ---: | ---: | --- |
| Haru | 6 | 727 | 0.00000345 | 通过 |
| Hiyori | 10 | 1211 | 0.00000406 | 通过 |
| Mao | 8 | 969 | 0.00001827 | 通过 |
| Mark | 6 | 727 | 0.00000028 | 通过 |
| Natori | 8 | 969 | 0.00000962 | 通过 |
| Rice | 4 | 485 | 0.00001243 | 通过 |
| Wanko | 5 | 606 | 0.00000009 | 通过 |
| Ren | 3 | — | — | 现有官方 Core 的 MOC3 校验失败，未进入两侧比较 |

通过的样本合计 47 个动作、5694 帧、20153459 个顶点采样。
浮点比较使用 `abs(a-b) <= 0.0001 + 0.0001 * max(abs(a), abs(b))`；
拓扑、遮罩、绘制顺序、纹理索引和标志要求完全相等。
此外比较模型参数定义、UV、透明度、乘色和屏幕色。
Ren 导致完整示例集返回非零，不能将整套结果表述为全部通过。

### 覆盖边界

- 动作采样器支持线性、Bezier、阶跃和反向阶跃段，仅输入 `Parameter`
  和 `PartOpacity` 曲线。它不是完整的 Cubism 动作播放器。
- 不属于模型的动作参数两侧均忽略并计数：本次 Haru 有 726 次、Mao 有
  121 次，其余通过样本为零。忽略项不能视为已验证的模型动作。
- `Model` 曲线不进入 Core 参数对比，报告会列出；本次涉及 Haru 的
  `Opacity` 和 Mark 的 `EyeBlink`、`Opacity`。
- 尚未覆盖动作淡入淡出、混合、循环边界、物理、表情叠加、Java/JNI 生命周期、
  实际纹理渲染、GPU 遮罩或壁纸服务运行。不能把网格通过解释为视觉完全一致。
- 结论限于这些模型和固定版本；支持继续开发 JNI 适配，不代表可直接替换 AAR。

## Live2D 点击行为

Live2D 壁纸服务不接收触摸，不转发点击或拖拽事件；模型层亦移除了点击触发路径。
自动动作和物理逻辑保留。

## Java/JNI 与画面验证

2026-11-07，ARM64 Android 15 真机：8 个官方模型（包含 Ren）的 Java/JNI
加载、同步、释放测试通过，合计 24 个生命周期、2880 次更新，启用了 CheckJNI。
后续已升级到 Framework R5，并补齐新 Java API 所需的离屏 JNI 数据。

设备测试 `Live2DDeviceTest` 现在对 Haru、Hiyori、Mao、Mark、Natori、Rice、
Wanko、Ren 分别验证 GPU 纹理渲染、完整动作集的参数变化、物理输入响应及
壁纸模型选择持久化。2026-11-07 真机执行结果为 32 项通过，0 失败、0 跳过。
合计播放 50 个动作文件，每个模型的物理配置均产生了输出参数变化。
随后补做 Pose 初始化后的截图复测，8 项 GPU 渲染测试均通过，截图已逐张检查。
另已打开 Ren 的系统壁纸预览并检查其离屏混合效果。

修复内容：Ren 的绘制顺序包含离屏对象，旧 R4 渲染器按 drawable 数组索引读取
会越界，现使用 R5 渲染器和完整离屏数据；Mark 的动作包含事件，独立测试播放器
显式注册事件接收器；测试在销毁 EGL 上下文前释放 shader，避免下一上下文删除旧
程序编号产生 GL_INVALID_VALUE。截图先执行应用的 Pose 初始化，避免备选手臂同时显示。

测试模型从仓库外指定，不随源码提交：

```powershell
./gradlew.bat :ping:connectedDebugAndroidTest `
  '-Plive2dTestAssets=PATH_TO_CubismNativeSamples/Samples/Resources' `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.storyteller_f.ping.Live2DDeviceTest'
```

须先取得设备独占使用权。测试会在测试应用私有目录复制各模型资源，并输出
`live2d-模型名.png` 到应用外部文件目录；截图已转换为正常屏幕方向。
以上为指定 8 个模型的实测结果，不代表所有第三方模型都与官方 Core 像素等价。

Debug 应用、设备测试包构建及 lintDebug 已通过。APK 的 ARM64、x86、x86_64
原生 Core 库均由本地 Purism 构建产出，且包含 Purism Core 的 MIT 许可文本。

### 实际应用到桌面

2026-11-07 已在 Android 15 真机点击系统“设置壁纸”，使用 Ren 实际作为桌面壁纸。
通过系统 wallpaper 状态确认活动组件为 PingBookService，并检查桌面实际画面、
自动动作、点击无交互、打开设置后返回、熄屏唤醒返回，以及已应用壁纸时再次
打开预览再返回桌面的表现。测试后恢复原系统默认静态壁纸。

此测试发现 R5 的 shader 和 offscreen manager 均为进程级单例，导致预览与桌面
两个 EGL 上下文复用不属于自己的 GPU 对象，出现持续黑屏。`cubism-framework`
构建任务从固定上游源文件生成按 GL 线程隔离这两个缓存的版本，保留内置的上游
源码原样；Delegate 在自己的 GL 线程释放对应缓存。修复后同时存在的桌面与预览
均有正常画面，返回桌面及熄屏恢复正常。此阶段首次编译 shader 约需 5 秒的问题，
已由下述按需编译改动继续优化。

### 桌面切换与生命周期复测

同日进一步在真机将 Ren、Mao、Mark、Natori、Rice、Wanko、Haru、Hiyori
依次通过系统选择器实际设置为桌面壁纸，逐个检查桌面画面及选择结果持久化。
完成 5 轮计算器、系统设置、通知栏、快捷设置、最近任务和桌面横向切换，
包括两次熄屏唤醒；这 5 轮中壁纸进程保持不变。另从最近任务划掉应用，
确认桌面壁纸继续显示。切换到系统静态壁纸后确认 Live2D 服务停止，再实际
设置 Haru，待首次 shader 编译完成后确认画面恢复。

本轮发现并修复：壁纸列表缺少 LayoutManager，导致列表不显示；已应用的引擎
错误读取预览模型，导致取消预览也改变桌面。现在只有预览引擎读取候选项，
成功应用才更新已选模型，成功或取消均清除候选项。服务在 onCreate 后开始
读取选择，确保 isPreview 已初始化；相同尺寸的 Surface 回调不再重复加载模型。
补充 4 项选择逻辑单元测试，均通过，Debug 构建和 lintDebug 亦通过。

点击事件保持禁用。本轮覆盖 Live2D 与系统静态壁纸之间的切换；视频及 3D
壁纸共用选择逻辑的改动已编译通过，未进行这两类渲染器的完整回归。
重新启用后再次验证取消其他模型预览及通知栏返回，仍保留 Haru。
测试结束已恢复原系统静态壁纸并移除临时设备测试包。

### 首帧与切换性能

Framework R5 原本为每个 GL 所有者同步初始化 482 个 shader 程序。构建任务现在
使用 `cubism-framework/patches/LazyShaders.java.inc` 替换生成源中的初始化方法，
保留上游源码、shader 文件及索引规则，首次绘制某种变体时才编译它。
旧式加算、乘算共用普通模式的程序，GPU 资源仍按 GL 线程隔离。
生成任务验证替换位置和调用数量；升级 Framework 时若结构改变会明确失败，
须复核补丁。生成目录不可手工编辑。

模型切换不再立即释放当前模型：先完成候选模型准备，再替换并释放旧模型；
加载失败保留当前模型且不在每帧重试。新建 Surface 的首帧先提交白色背景，
再准备模型资源；已有 Surface 切换时保留上一帧，避免主动提交空画面。
资源准备仍在 GL 线程串行执行，切换可能短暂冻结旧画面，并非完全异步加载。

2026-11-07，同一 ARM64 Android 15 真机、Debug 构建，Haru 三轮新进程测试：

| 指标 | 修改前平均 | 修改后平均 |
| --- | ---: | ---: |
| 请求到首帧 GPU 完成 | 5371 ms | 438 ms |
| Renderer 初始化 | 4951 ms | 3.4 ms |
| 模型内部准备（含动作、物理等） | 220 ms | 220 ms |
| 纹理处理 | 127 ms | 125 ms |
| 首帧绘制及 GPU 完成等待 | 31 ms | 37 ms |

首帧等待减少 91.8%，修改后三轮为 469、442、403 ms。
shader 编译移到实际使用时执行，首帧绘制的数值已包含所需 shader 的编译时间，
不能把 Renderer 初始化下降解释为完全取消 shader 编译。
冷启动未清除驱动或系统文件缓存；临时分段计时与首帧 glFinish 测量点均已撤回。

Ren 的系统预览与实际应用流程另录屏复测：画面中央 60% 高度区域，
黑色阈值 5%、覆盖率 98%、最短持续 0.1 秒，同条件检测的连续黑屏从
5.360 秒缩短到 0.267 秒；桌面切换阶段未检出满足该条件的黑屏。
仍存在短暂的初始过渡，不能声称所有设备上完全无黑屏。

41 项设备测试全部通过，覆盖 8 个模型的渲染、50 个动作、物理响应、选择持久化、
加载失败保留旧模型，以及所有 shader 变体的创建、旧模式复用和释放。
8 个模型首帧实际创建程序数：Haru 3、Hiyori 3、Mao 4、Mark 3、Natori 3、
Rice 4、Wanko 1、Ren 9。另补做 8 项 GPU 截图复测并检查画面。
这 8 张 512×512 截图与修改前相同 Pose 初始化条件下的基线逐像素比较，差异均为零。
Tegra 扩展分支未在对应硬件上实测。
运行整组测试时，将上述 instrumentation class 参数设为
`com.storyteller_f.ping.Live2DDeviceTest,com.storyteller_f.ping.ShaderVariantsDeviceTest`。

完整 UI 复测初轮受到 Debug 内存检测器自动堆转储影响，转储暂停进程约 33 秒并
触发 ANR，该轮应用流程不计为通过。录屏和后续 UI 复测临时关闭自动堆转储以
隔离调试干扰，未将此设置保留在源码中；不涉及生产版本的运行配置。
排除该干扰后，Haru → Mao → Hiyori → Ren 实际应用和保存均通过；
取消 Haru 预览后仍保留 Ren。两轮设置、通知栏、快捷设置、最近任务、桌面切换、
熄屏唤醒通过且进程保持不变。最后安装移除所有临时测量设置的最终 Debug APK，
确认 Ren 桌面画面正常，随后恢复系统原静态壁纸、卸载临时测试包。

### 系统预览剩余黑色过渡

同日进一步将录屏内 Winscope v2 帧时间与壁纸阶段日志对齐。复现的连续黑屏为
0.268556 秒，开始前约 122 ms，白色背景已经完成 GPU 绘制；EGL 上下文创建
约 1.5 ms，背景绘制及 GPU 等待约 4.6 ms。另试验等待 swap 提交再返回同步
重绘回调、保留不可见阶段 EGL Surface，均未消除此黑色过渡，试验改动全部撤回。

只读检查真机自带 LiveWallpapersPicker APK：其 `live_wallpaper_preview` 布局有
背景为 `#ff000000` 的 loading 遮罩；`engineShown` 动画固定延迟 300 ms，再以
220 ms 淡出。与 [Android 15 系统预览源码](https://android.googlesource.com/platform/packages/wallpapers/LivePicker/+/refs/heads/android15-release/src/com/android/wallpaper/livepicker/LiveWallpaperPreview.java#693)
及 [系统布局](https://android.googlesource.com/platform/packages/wallpapers/LivePicker/+/refs/heads/android15-release/res/layout/live_wallpaper_preview.xml#22)
一致。基线及两个对照试验中，黑屏检测结束均在系统报告已显示后的约 419～441 ms，
符合遮罩延时和淡出过程；录屏只统计满足黑色阈值的区间，不等于完整的 520 ms 动画。

这段剩余黑屏属于系统预览 Activity，普通壁纸应用不能修改该遮罩。继续优化 Core
或更早提交背景帧无法移除它；自建应用内预览也只能避开浏览阶段的遮罩，最终进入
系统设置确认仍受系统预览控制。本次未修改系统应用、全局动画设置或交互流程。


## 视频壁纸端到端验证

`VideoWallpaperE2ETest` 在系统实际桌面壁纸中运行 `PingPagerService`，通过真实
DataStore 选择、MediaPlayer 解码、SurfaceTexture 和 EGL 绘制链路验证生命周期。
它使用测试包内的三段合成 H.264 MP4：红色、蓝色均为 640×360，绿色为 360×640，
均为 4 秒、15 fps、无音轨，叠加移动的 `testsrc2` 图案。没有使用用户媒体。
断言包括桌面颜色、中心区域跨帧像素变化、播放器对象及进度、Surface 释放和 GL 线程退出。

测试会替换设备当前壁纸，因此默认跳过，须在独占的专用测试设备上显式启用。
它会恢复原壁纸组件及选择参数，并删除自己的应用内测试文件。静态壁纸恢复路径
面向系统使用 `com.android.systemui.wallpapers.ImageWallpaper` 的 Android 测试镜像。
测试以反射观察 framework 的活动 Engine 和应用状态，需要仅对本次 instrumentation
进程启用 `--no-hidden-api-checks`；不修改系统全局 hidden API 设置或生产应用权限。

先构建并安装原项目的两个 Debug APK，以下为 PowerShell 命令；替换 `DEVICE_SERIAL`：

```powershell
./gradlew.bat :ping:assembleDebug :ping:assembleDebugAndroidTest :ping:testDebugUnitTest :ping:lintDebug
adb -s DEVICE_SERIAL install -r ping/build/outputs/apk/debug/ping-debug.apk
adb -s DEVICE_SERIAL install -r ping/build/outputs/apk/androidTest/debug/ping-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument --no-hidden-api-checks -w -r `
  -e videoWallpaperE2E true `
  -e class "com.storyteller_f.ping.wallpaper.VideoWallpaperE2ETest,com.storyteller_f.ping.shader.WallpaperRendererTest" `
  com.storyteller_f.ping.debug.test/androidx.test.runner.AndroidJUnitRunner
```

测试截图保存在目标应用外部文件目录中，名称为 `video-switching.png`、
`hidden-resumed.png`、`engine-reapplied.png` 和 `egl-recreated.png`。
可通过 `adb pull /sdcard/Android/data/com.storyteller_f.ping.debug/files/ 本地输出目录`
取得。`am instrument` 的进程退出码不能单独证明通过，须检查 `OK (7 tests)`
及每个用例的状态；发生颜色超时时另存 `color-timeout.png`。

合成视频位于 `ping/src/androidTest/assets/video-wallpaper`。需要重新生成时，使用
FFmpeg 的 `color` 和 `testsrc2` 源，不需要网络或外部视频。例如红色视频：

```powershell
ffmpeg -y -f lavfi -i "color=c=red:s=640x360:r=15:d=4" `
  -f lavfi -i "testsrc2=s=120x120:r=15:d=4" `
  -filter_complex "[0:v][1:v]overlay=x='mod(t*120,main_w-overlay_w)':y='(main_h-overlay_h)/2':shortest=1" `
  -c:v libx264 -pix_fmt yuv420p -preset fast -crf 24 -movflags +faststart `
  ping/src/androidTest/assets/video-wallpaper/red.mp4
```

蓝色改用 `color=c=blue:s=640x360`，绿色改用 `color=c=lime:s=360x640`，
同时保留帧率和时长参数，分别输出 `blue.mp4`、`green.mp4`。

### 2026-11-08 验证结果

原项目 Debug APK 在 Android 16 / API 36.1、x86_64 模拟器上完成以下验证，
5 项系统壁纸测试及 2 项 ES2/ES3 渲染器测试全部通过：

| 场景 | 实际断言 |
| --- | --- |
| 视频切换 | 5 次切换，包含同尺寸和横竖尺寸变化；Engine 不替换，旧播放器释放，新画面颜色正确且持续变化 |
| 隐藏恢复 | 3 轮打开系统设置后返回桌面；隐藏时进度停止，返回后同一 Engine 和播放器恢复动态画面 |
| Engine 销毁 | 改用系统静态壁纸，验证播放器、Surface、GL 线程和订阅释放；修改选择不会唤醒旧 Engine，重新应用能播放新视频 |
| EGL context 重建 | 3 轮关闭 context 保留后通过实际可见性回调触发 pause/resume；旧输出失效，新输出绑定，同一播放器恢复动态画面 |
| 已暂停时销毁 | context 已销毁且旧生产队列仍待替换时，Engine 退出仍能释放 SurfaceTexture 并结束 GL 线程 |

本轮复现了旧实现的 context 恢复持续黑屏：先释放 SurfaceTexture 会废弃解码器仍在
使用的 BufferQueue；改为先解绑到 null 后，暂停中的视频解码器仍可能不能恢复产帧。
现在旧输出先失效，生产队列保留到新 Surface 直接替换或播放器关闭，再由 GL 线程释放。
`OutputLease` 原子协调取得、撤销和归还生产者所有权，防止交接与 context 销毁之间的竞争。
Engine 关闭先断开生产者，GL 清理事件完成后才退出线程，覆盖已经没有当前 context 的状态。

17 项原项目 JVM 单元测试通过；Debug 应用及测试包构建、`lintDebug` 通过，
Lint 为 0 错误、36 项警告。本轮不代表真机硬件解码器、GPU 驱动异常重置、其他视频
编码/旋转元数据或应用导入与系统确认按钮流程均已覆盖；context 测试通过关闭
`preserveEGLContextOnPause` 确定性触发真实 EGL 销毁及重建，没有模拟 GPU 驱动故障。
