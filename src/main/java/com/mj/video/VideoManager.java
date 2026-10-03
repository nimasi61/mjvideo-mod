package com.mj.video;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 特效管理器：负责解析资源清单、匹配聊天关键词、调度播放与渲染。
 * 所有方法均在客户端主线程调用（聊天事件会切回主线程）。
 */
public class VideoManager {
    private static final String MANIFEST_PATH = "/assets/mjvideo/manifest.json";

    private final MjVideoConfig config;
    private final List<EffectDef> effects = new ArrayList<>();
    private final Random random = new Random();

    private Playback playback;
    private int lastIndex = -1;
    private long lastTriggerAt;

    public VideoManager(MjVideoConfig config) {
        this.config = config;
        loadManifest();
    }

    private void loadManifest() {
        try (InputStream in = VideoManager.class.getResourceAsStream(MANIFEST_PATH)) {
            if (in == null) {
                MjVideoMod.LOGGER.error("[mjvideo] 未找到资源清单 {}", MANIFEST_PATH);
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            float defaultFps = root.has("fps") ? root.get("fps").getAsFloat() : 30F;
            for (JsonElement element : root.getAsJsonArray("effects")) {
                JsonObject obj = element.getAsJsonObject();
                String id = obj.get("id").getAsString();
                EffectDef def = new EffectDef(
                        id,
                        obj.get("frames").getAsInt(),
                        obj.get("width").getAsInt(),
                        obj.get("height").getAsInt(),
                        obj.has("fps") ? obj.get("fps").getAsFloat() : defaultFps);
                Identifier soundId = new Identifier(MjVideoMod.MOD_ID, id);
                def.sound = SoundEvent.of(soundId);
                Registry.register(Registries.SOUND_EVENT, soundId, def.sound);
                effects.add(def);
            }
            MjVideoMod.LOGGER.info("[mjvideo] 已加载 {} 个特效", effects.size());
        } catch (Exception e) {
            MjVideoMod.LOGGER.error("[mjvideo] 资源清单解析失败", e);
        }
    }

    public int getEffectCount() {
        return effects.size();
    }

    /**
     * 收到一条聊天消息（原始文本，含玩家名前缀）。命中关键词则触发播放。
     */
    public void onChatMessage(String rawText, String senderName) {
        if (!config.enabled || effects.isEmpty()) {
            return;
        }
        if (playback != null) {
            return; // 正在播放，忽略重复触发
        }
        if (rawText == null) {
            return;
        }
        // 仅自己发送的消息才触发
        if (config.triggerMode == MjVideoConfig.TriggerMode.SELF) {
            String self = MinecraftClient.getInstance().getSession().getUsername();
            if (self == null || senderName == null || !self.equalsIgnoreCase(senderName)) {
                return;
            }
        }
        // 触发冷却
        long now = System.currentTimeMillis();
        if (config.cooldownSeconds > 0 && now - lastTriggerAt < config.cooldownSeconds * 1000L) {
            return;
        }
        String text = rawText.toLowerCase(Locale.ROOT);
        boolean matched = config.requireAllKeywords
                ? config.keywords.stream().allMatch(k -> k != null && !k.isEmpty()
                        && text.contains(k.toLowerCase(Locale.ROOT)))
                : config.keywords.stream().anyMatch(k -> k != null && !k.isEmpty()
                        && text.contains(k.toLowerCase(Locale.ROOT)));
        if (!matched) {
            return;
        }
        lastTriggerAt = now;
        MjVideoMod.LOGGER.info("[mjvideo] 触发特效：{}", rawText.trim());
        MinecraftClient.getInstance().execute(this::playRandom);
    }

    /** 随机播放一个特效，不与上一次重复 */
    public void playRandom() {
        if (playback != null || effects.isEmpty()) {
            return;
        }
        int index = effects.size() == 1 ? 0 : random.nextInt(effects.size());
        while (config.avoidRepeat && effects.size() > 1 && index == lastIndex) {
            index = random.nextInt(effects.size());
        }
        lastIndex = index;
        try {
            playback = new Playback(effects.get(index), config);
        } catch (Exception e) {
            MjVideoMod.LOGGER.error("[mjvideo] 播放失败", e);
            playback = null;
        }
    }

    /** HUD 渲染回调，播放结束后自动收尾 */
    public void onHudRender(DrawContext context, float tickDelta) {
        Playback current = this.playback;
        if (current == null) {
            return;
        }
        boolean keepPlaying;
        try {
            keepPlaying = current.render(context);
        } catch (Throwable t) {
            MjVideoMod.LOGGER.error("[mjvideo] 渲染异常", t);
            keepPlaying = false;
        }
        if (!keepPlaying) {
            this.playback = null;
            current.dispose();
        }
    }
}
