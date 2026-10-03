package com.mj.video;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 单次播放：按帧序号把 PNG 帧贴到一张动态纹理上，并绘制在屏幕顶部居中位置。
 * 帧由后台线程按需解码，且只保留播放点前后少量帧，避免占用过多内存。
 */
public class Playback {
    private static final Identifier TEXTURE_ID = new Identifier(MjVideoMod.MOD_ID, "dynamic/current");
    /** 后台解码领先播放点的帧数 */
    private static final int AHEAD = 12;
    /** 播放点之后保留的帧数 */
    private static final int KEEP_BEHIND = 2;

    private final EffectDef def;
    private final MjVideoConfig config;
    private final AtomicReferenceArray<NativeImage> frames;
    private final NativeImage display;
    private final NativeImageBackedTexture texture;
    private final long startNanos;

    private volatile int readIndex;
    private volatile boolean stopped;
    private int lastUploaded = -1;
    private int releasedUpTo = -1;
    private boolean prepared;

    public Playback(EffectDef def, MjVideoConfig config) throws Exception {
        this.def = def;
        this.config = config;
        this.frames = new AtomicReferenceArray<>(def.frames);
        this.frames.set(0, loadFrame(0));
        this.readIndex = 0;

        this.display = new NativeImage(NativeImage.Format.RGBA, def.width, def.height, false);
        this.texture = new NativeImageBackedTexture(display);

        MinecraftClient client = MinecraftClient.getInstance();
        client.getTextureManager().registerTexture(TEXTURE_ID, texture);

        startLoader();

        if (config.playSound && def.sound != null && config.volume > 0F) {
            client.getSoundManager().play(PositionedSoundInstance.master(def.sound, 1.0F, Math.max(0.0001F, config.volume)));
        }
        this.startNanos = System.nanoTime();
    }

    /** @return false 表示播放已结束，调用方应释放资源 */
    public boolean render(DrawContext context) {
        float elapsed = (System.nanoTime() - startNanos) / 1_000_000_000F;
        int index = (int) (elapsed * def.fps);
        if (index >= def.frames || stopped) {
            return false;
        }
        if (index != lastUploaded) {
            NativeImage frame = frames.get(index);
            if (frame != null) {
                display.copyFrom(frame);
                texture.bindTexture();
                if (!prepared) {
                    TextureUtil.prepareImage(texture.getGlId(), 0, def.width, def.height);
                    prepared = true;
                }
                texture.upload();
                lastUploaded = index;
                readIndex = index;
                releaseOldFrames(index);
            }
        }
        if (lastUploaded < 0) {
            return true; // 首帧尚未就绪，本帧先不画
        }
        draw(context);
        return true;
    }

    private void draw(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        float maxWidth = screenWidth * (config.maxWidthPercent / 100F);
        float maxHeight = screenHeight * (config.maxHeightPercent / 100F);
        float scale = Math.min(maxWidth / def.width, maxHeight / def.height);

        float width = Math.max(1F, def.width * scale);
        float height = Math.max(1F, def.height * scale);

        // 顶部居中，并做一次边界收敛，保证视频完全在屏幕内
        float x = (screenWidth - width) / 2F;
        float y = screenHeight * (config.topMarginPercent / 100F);
        if (x + width > screenWidth) {
            x = screenWidth - width;
        }
        if (y + height > screenHeight) {
            y = screenHeight - height;
        }
        if (x < 0F) {
            x = 0F;
        }
        if (y < 0F) {
            y = 0F;
        }

        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        context.drawTexture(TEXTURE_ID, Math.round(x), Math.round(y), Math.round(width), Math.round(height),
                0F, 0F, def.width, def.height, def.width, def.height);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        if (!blendWasEnabled) {
            RenderSystem.disableBlend();
        }
    }

    private void releaseOldFrames(int index) {
        int releaseUpTo = index - KEEP_BEHIND;
        while (releasedUpTo < releaseUpTo) {
            releasedUpTo++;
            NativeImage old = frames.getAndSet(releasedUpTo, null);
            if (old != null) {
                old.close();
            }
        }
    }

    private void startLoader() {
        Thread loader = new Thread(() -> {
            for (int i = 1; i < def.frames && !stopped; i++) {
                while (!stopped && i - readIndex > AHEAD) {
                    try {
                        Thread.sleep(5L);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                if (stopped) {
                    return;
                }
                try {
                    frames.set(i, loadFrame(i));
                } catch (Exception e) {
                    MjVideoMod.LOGGER.warn("[mjvideo] 第 {} 帧解码失败", i, e);
                }
            }
        }, "mjvideo-frame-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private NativeImage loadFrame(int index) throws Exception {
        String path = "/assets/mjvideo/frames/" + def.id + "/" + String.format("%04d.png", index + 1);
        try (InputStream in = Playback.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("缺少帧资源 " + path);
            }
            return NativeImage.read(in);
        }
    }

    public void dispose() {
        stopped = true;
        try {
            texture.close();
        } catch (Exception e) {
            MjVideoMod.LOGGER.warn("[mjvideo] 纹理释放异常", e);
        }
        for (int i = 0; i < frames.length(); i++) {
            NativeImage image = frames.getAndSet(i, null);
            if (image != null) {
                image.close();
            }
        }
    }
}
