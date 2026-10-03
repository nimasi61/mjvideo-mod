package com.mj.video;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 客户端配置，存放于 .minecraft/config/mjvideo.json
 */
public class MjVideoConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("mjvideo.json");

    /** 触发来源 */
    public enum TriggerMode {
        /** 任何玩家发的消息都会触发本机播放 */
        ALL("所有玩家"),
        /** 只有自己发出的消息才触发本机播放 */
        SELF("仅自己发送");

        public final String label;

        TriggerMode(String label) {
            this.label = label;
        }

        public TriggerMode next() {
            return this == ALL ? SELF : ALL;
        }
    }

    private static MjVideoConfig instance;

    /** 总开关 */
    public boolean enabled = true;
    /** 触发来源：所有玩家 / 仅自己发送 */
    public TriggerMode triggerMode = TriggerMode.ALL;
    /** 视频最大宽度占屏幕比例(%) */
    public int maxWidthPercent = 90;
    /** 视频最大高度占屏幕比例(%) */
    public int maxHeightPercent = 80;
    /** 距屏幕顶部距离占屏幕比例(%) */
    public int topMarginPercent = 5;
    /** 是否播放音效 */
    public boolean playSound = true;
    /** 音量 0~1 */
    public float volume = 1.0F;
    /** true=需要同时包含所有关键词，false=包含任意一个即可（默认） */
    public boolean requireAllKeywords = false;
    /** 是否避免连续两次播放同一个特效 */
    public boolean avoidRepeat = true;
    /** 触发冷却（秒），0 表示不限制 */
    public int cooldownSeconds = 3;
    /** 触发关键词 */
    public List<String> keywords = new ArrayList<>(Arrays.asList("mj", "蜘蛛侠"));

    public static MjVideoConfig load() {
        MjVideoConfig config = new MjVideoConfig();
        try {
            if (Files.exists(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                    MjVideoConfig read = GSON.fromJson(reader, MjVideoConfig.class);
                    if (read != null) {
                        config = read;
                    }
                }
            }
            config.clamp();
            config.save();
        } catch (Exception e) {
            MjVideoMod.LOGGER.warn("[mjvideo] 配置读取失败，使用默认配置", e);
        }
        instance = config;
        return config;
    }

    /** 全局配置实例（游戏内配置界面改动的就是它） */
    public static MjVideoConfig getInstance() {
        return instance != null ? instance : load();
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            MjVideoMod.LOGGER.warn("[mjvideo] 配置写入失败", e);
        }
    }

    public void clamp() {
        maxWidthPercent = Math.max(5, Math.min(100, maxWidthPercent));
        maxHeightPercent = Math.max(5, Math.min(100, maxHeightPercent));
        topMarginPercent = Math.max(0, Math.min(100, topMarginPercent));
        volume = Math.max(0F, Math.min(1F, volume));
        cooldownSeconds = Math.max(0, Math.min(600, cooldownSeconds));
        if (triggerMode == null) {
            triggerMode = TriggerMode.ALL;
        }
        if (keywords == null || keywords.isEmpty()) {
            keywords = new ArrayList<>(Arrays.asList("mj", "蜘蛛侠"));
        }
    }
}
