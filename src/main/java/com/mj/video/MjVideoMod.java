package com.mj.video;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MjVideoMod implements ClientModInitializer {
    public static final String MOD_ID = "mjvideo";
    public static final Logger LOGGER = LoggerFactory.getLogger("mjvideo");

    /** 打开内置配置界面的快捷键，默认 K，可在「选项 → 按键控制」中自行修改 */
    private static KeyBinding openConfigKey;

    @Override
    public void onInitializeClient() {
        MjVideoConfig config = MjVideoConfig.load();
        VideoManager manager = new VideoManager(config);

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mjvideo.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.mjvideo"));

        HudRenderCallback.EVENT.register(manager::onHudRender);

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            try {
                manager.onChatMessage(message.getString(), sender == null ? null : sender.getName());
            } catch (Throwable t) {
                LOGGER.warn("[mjvideo] trigger check failed", t);
            }
        });

        // 每 tick 检查快捷键，游戏内无其他界面时打开配置页
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new MjVideoConfigScreen(null, MjVideoConfig.getInstance()));
                }
            }
        });

        LOGGER.info("[mjvideo] ready. effects={}, keywords={}, mode={}, requireAll={}",
                manager.getEffectCount(), config.keywords, config.triggerMode, config.requireAllKeywords);
    }
}
