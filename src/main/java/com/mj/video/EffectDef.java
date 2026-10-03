package com.mj.video;

import net.minecraft.sound.SoundEvent;

/**
 * 一个特效视频的元数据（来自 assets/mjvideo/manifest.json）
 */
public class EffectDef {
    public final String id;
    public final int frames;
    public final int width;
    public final int height;
    public final float fps;
    /** 对应音效事件，注册后回填 */
    public SoundEvent sound;

    public EffectDef(String id, int frames, int width, int height, float fps) {
        this.id = id;
        this.frames = Math.max(1, frames);
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.fps = fps <= 0F ? 30F : fps;
    }
}
