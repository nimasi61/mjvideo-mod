# effects-src —— 放置原始视频素材

把你要用的视频（推荐 **带透明通道的 webm**，也支持 mp4 / mov / mkv / avi）放进这个目录，
文件名就是特效的 id（例如 `effect1.webm`、`spider.webm`），然后回到工程根目录执行：

```bash
pip install imageio-ffmpeg pillow
python tools/extract_frames.py
gradlew build
```

脚本会自动：
1. 按 `tools/extract_frames.py` 顶部的 `FPS`（默认 30）抽帧为 RGBA PNG；
2. 等比缩放到 `MAX_SIDE`（默认 640）最长边，保留透明通道；
3. 提取音轨为 ogg（没有音轨就跳过）；
4. 重写 `src/main/resources/assets/mjvideo/manifest.json` 与 `sounds.json`。

> 注意：脚本每次运行都会**清空并重建** `src/main/resources/assets/mjvideo/frames/`，
> 也就是说视频素材以本目录为准，改了这里再跑脚本就会整体覆盖旧帧。本目录的文件不会被脚本改动。
>
> 本包已内嵌两个特效的现成帧资源（`effect1` 386x640、`effect2` 640x640，各 83 帧 / 30fps），
> 只想换关键词或换 MC 版本的话，直接构建即可，不需要动这个目录。
