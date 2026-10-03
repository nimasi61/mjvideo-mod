# -*- coding: utf-8 -*-
"""
从 webm（或其他 ffmpeg 支持的视频）重新生成模组贴图资源。

生成内容：
  src/main/resources/assets/mjvideo/frames/<名字>/%04d.png   逐帧 RGBA 图片（带透明通道）
  src/main/resources/assets/mjvideo/sounds/<名字>.ogg        音轨（无音轨则跳过）
  src/main/resources/assets/mjvideo/manifest.json            特效清单（帧数/宽高/fps）
  src/main/resources/assets/mjvideo/sounds.json              音效索引

用法：
    pip install imageio-ffmpeg pillow
    python tools/extract_frames.py

依赖说明：imageio-ffmpeg 自带 ffmpeg 可执行文件，无需系统另装 ffmpeg。
改素材：把新的 webm 丢进 effects-src/ 再跑一次本脚本即可（文件名 = 特效 id）。
"""

import json
import os
import shutil
import subprocess
import sys

# ============ 可调参数 ============
MOD_ID = "mjvideo"          # 与 fabric.mod.json 里的 id 保持一致
SOURCE_DIR = "effects-src"  # 原始视频目录（相对工程根目录，或写绝对路径）
MAX_SIDE = 640              # 贴图最长边像素：越大越清晰、jar 越大（建议 480~800）
FPS = 30                    # 抽帧帧率，需与游戏内播放帧率一致
KEEP_ASCII_ID = True        # 特效 id 只保留字母数字下划线（中文名会被改成拼音式 fallback）
# =================================

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", MOD_ID)
VIDEO_EXT = (".webm", ".mp4", ".mov", ".mkv", ".avi")


def get_ffmpeg():
    try:
        import imageio_ffmpeg
    except ImportError:
        sys.exit("缺少依赖，请先执行: pip install imageio-ffmpeg pillow")
    return imageio_ffmpeg.get_ffmpeg_exe()


def safe_id(name):
    if not KEEP_ASCII_ID:
        return name
    cleaned = "".join(c if (c.isalnum() and c.isascii()) else "_" for c in name)
    cleaned = cleaned.strip("_")
    return cleaned or "effect"


def probe_size(ffmpeg, video):
    """读取视频宽高（解析 ffmpeg -i 的 stderr）。"""
    p = subprocess.run([ffmpeg, "-i", video], capture_output=True, text=True,
                       encoding="utf-8", errors="ignore")
    for line in p.stderr.splitlines():
        if "Video:" in line and "x" in line:
            for token in line.split(","):
                token = token.strip().split(" ")[0]
                if "x" in token and token.replace("x", "").isdigit():
                    w, h = token.split("x")[:2]
                    return int(w), int(h)
    return None, None


def extract(video, out_dir):
    """抽帧 + 抽音效，返回 (帧数, 宽, 高, 是否有音轨)。"""
    ffmpeg = get_ffmpeg()
    frames_dir = os.path.join(out_dir, "frames")
    os.makedirs(frames_dir, exist_ok=True)
    for f in os.listdir(frames_dir):
        os.remove(os.path.join(frames_dir, f))

    # 等比缩放到最长边 = MAX_SIDE，宽高都用表达式算，避免变形
    if MAX_SIDE:
        vf = ("fps=%d,scale='if(gt(iw,ih),%d,-2)':'if(gt(iw,ih),-2,%d)'"
              % (FPS, MAX_SIDE, MAX_SIDE))
    else:
        vf = "fps=%d" % FPS

    cmd = [ffmpeg, "-y", "-i", video, "-vf", vf, "-pix_fmt", "rgba",
           os.path.join(out_dir, "frames", "%04d.png")]
    print("  [帧] " + " ".join(cmd[:6]) + " ...")
    if subprocess.run(cmd, capture_output=True).returncode != 0:
        sys.exit("抽帧失败：%s" % video)

    frame_files = sorted(os.listdir(frames_dir))
    if not frame_files:
        sys.exit("没有生成任何帧：%s" % video)

    # 音轨 -> ogg（没有音轨就静默跳过）
    sound = os.path.join(out_dir, "sounds", safe_id(os.path.basename(video).rsplit(".", 1)[0]) + ".ogg")
    has_sound = False
    if os.path.exists(sound):
        os.remove(sound)
    r = subprocess.run([ffmpeg, "-y", "-i", video, "-vn", "-c:a", "libvorbis",
                        "-q:a", "5", sound], capture_output=True)
    if r.returncode == 0 and os.path.exists(sound) and os.path.getsize(sound) > 0:
        has_sound = True
    elif os.path.exists(sound):
        os.remove(sound)

    return len(frame_files), has_sound


def main():
    src = SOURCE_DIR if os.path.isabs(SOURCE_DIR) else os.path.join(ROOT, SOURCE_DIR)
    if not os.path.isdir(src):
        sys.exit("找不到素材目录：%s" % src)

    videos = [f for f in sorted(os.listdir(src)) if f.lower().endswith(VIDEO_EXT)]
    if not videos:
        sys.exit("素材目录里没有视频文件（支持 %s）：%s" % ("/".join(VIDEO_EXT), src))

    print("工程根目录：%s" % ROOT)
    print("目标资源目录：%s" % RES)
    print("发现 %d 个视频，开始处理（最长边 %s px / %d fps）\n" % (len(videos), MAX_SIDE, FPS))

    entries, sounds = [], {}

    # 清掉旧帧，避免残留上一个素材的帧造成串帧
    frames_root = os.path.join(RES, "frames")
    if os.path.isdir(frames_root):
        shutil.rmtree(frames_root)

    for v in videos:
        name = safe_id(os.path.basename(v).rsplit(".", 1)[0])
        vpath = os.path.join(src, v)
        out_dir = os.path.join(RES, name)
        os.makedirs(os.path.join(out_dir, "sounds"), exist_ok=True)
        print("[%s] <- %s" % (name, v))

        count, has_sound = extract(vpath, out_dir)

        # 取首帧实际尺寸作为 manifest 里的宽高
        import struct
        first = os.path.join(out_dir, "frames", "0001.png")
        try:
            from PIL import Image
            with Image.open(first) as im:
                w, h = im.size
        except ImportError:
            with open(first, "rb") as fp:
                fp.read(16)
                w, h = struct.unpack(">II", fp.read(8))

        entries.append({"id": name, "frames": count, "width": w, "height": h, "fps": FPS})
        if has_sound:
            # 键 = 音效事件 id 的 path 部分（代码里注册 mjvideo:<id>）
            sounds[name] = {"sounds": ["%s:%s" % (MOD_ID, name)]}
        print("      -> %d 帧, %dx%d%s\n" % (count, w, h, "，含音效" if has_sound else "，无音效"))

    # manifest 结构须与 VideoManager#loadManifest 的解析保持一致
    manifest = os.path.join(RES, "manifest.json")
    with open(manifest, "w", encoding="utf-8") as f:
        json.dump({"fps": FPS, "effects": entries}, f, ensure_ascii=False, indent=2)
    with open(os.path.join(RES, "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(sounds, f, ensure_ascii=False, indent=2)

    print("完成：%d 个特效，已写入" % len(entries))
    print("  " + manifest)
    print("  " + os.path.join(RES, "sounds.json"))
    print("\n接下来执行构建：gradlew build")


if __name__ == "__main__":
    main()
