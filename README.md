# MJ Video Overlay

Minecraft Fabric 1.20.1 客户端模组：聊天栏出现指定关键词时，在屏幕上方居中播放随机特效视频（PNG 帧序列 + 透明通道叠加在 HUD 上）。

## 环境要求

- Minecraft 1.20.1 + Fabric Loader >= 0.14.21
- Fabric API（必需）
- Java 17+

## 构建

```bat
gradlew.bat build
```

产物：`build/libs/mjvideo-1.0.0.jar`（同时生成 `-sources.jar`）。

## 安装

1. 把 `mjvideo-1.0.0.jar` 放入 `.minecraft/mods/`（客户端模组，服务端无需安装）
2. 确保已安装 Fabric API
3. 启动游戏即可

## 配置界面（内置，无需 Mod Menu）

- 默认快捷键 **K** 打开配置界面（游戏内、无其他界面时生效）
- 改键：`选项 → 按键控制 → MJ Video Overlay → 打开 MJ 视频配置`
- 界面上所有改动即时生效，关闭界面时写入 `config/mjvideo.json`
- 打开配置界面时游戏不会暂停（多人游戏中也可放心改）

### 可配置项

| 选项 | 说明 | 默认值 |
| --- | --- | --- |
| 模组开关 | 总开关 | 开启 |
| 触发来源 | 所有玩家 / 仅自己发送 | 所有玩家 |
| 触发关键词 | 逗号分隔，大小写不敏感 | `mj, 蜘蛛侠` |
| 匹配方式 | 包含任一即可 / 需包含全部 | 包含任一即可 |
| 避免连续重复 | 连续触发时不播同一个特效 | 开启 |
| 播放音效 | 是否播放特效音效 | 开启 |
| 触发冷却 | 两次触发的最小间隔 | 3 秒 |
| 音量 | 音效音量 | 100% |
| 最大宽度 / 最大高度 | 视频占屏幕比例 | 90% / 80% |
| 顶部距离 | 距屏幕顶部比例 | 5% |

## 触发逻辑

1. 监听客户端聊天消息（`ClientReceiveMessageEvents.CHAT`）
2. 剥离 `<玩家名> ` 前缀后按「匹配方式」检查关键词
3. 「仅自己发送」模式只响应自己发的消息
4. 冷却结束且当前没有正在播放的特效时，随机播放一个特效
5. 播放期间 HUD 按 `elapsed * fps` 取帧绘制，缩放后居中并保证不出屏

## 目录结构

```
src/main/java/com/mj/video/
  MjVideoMod.java           模组入口：注册快捷键、聊天监听、HUD 渲染
  MjVideoConfig.java        配置数据 + mjvideo.json 读写
  MjVideoConfigScreen.java  内置配置界面（滑条/开关/关键词编辑）
  VideoManager.java         触发判定与特效调度
  Playback.java             单次播放状态（帧、时间、音频）
  EffectDef.java            特效定义（帧数、fps、尺寸）
src/main/resources/
  fabric.mod.json           模组元数据（含 icon）
  assets/mjvideo/icon.png   模组图标
  assets/mjvideo/lang/      中英文语言文件（快捷键显示名）
  assets/mjvideo/frames/    各特效帧序列（%04d.png）
  assets/mjvideo/manifest.json  帧清单（特效名/帧数/fps/宽高）
tools/extract_frames.py     从 webm 拆帧 + 抽音频的脚本
```

## 重新生成帧资源

```bat
python tools/extract_frames.py <输入.webm> <特效名>
```
