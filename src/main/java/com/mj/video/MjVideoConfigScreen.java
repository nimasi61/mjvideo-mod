package com.mj.video;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/**
 * 游戏内配置界面：按快捷键打开（默认 K，可在「选项 → 按键控制 → MJ Video Overlay」里修改）。
 * 所有改动即时生效，并在关闭界面时写入 config/mjvideo.json。
 */
public class MjVideoConfigScreen extends Screen {
    private static final int WIDGET_W = 150;
    private static final int ROW_H = 24;
    private static final int TOP = 40;
    private static final int BOTTOM = 38;
    /** 标签显示宽度上限（像素） */
    private static final int LABEL_W = 148;

    private final Screen parent;
    private final MjVideoConfig config;

    private final List<ClickableWidget> widgets = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final List<Supplier<Text>> messages = new ArrayList<>();

    private int scroll;
    private boolean scrollable;

    public MjVideoConfigScreen(Screen parent, MjVideoConfig config) {
        super(Text.literal("MJ 视频配置"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        widgets.clear();
        labels.clear();
        messages.clear();

        // —— 开关 / 循环按钮 ——
        addButton("模组开关", () -> Text.literal(config.enabled ? "开启" : "关闭"),
                b -> {
                    config.enabled = !config.enabled;
                    persist();
                });
        addButton("触发来源", () -> Text.literal(config.triggerMode.label),
                b -> {
                    config.triggerMode = config.triggerMode.next();
                    persist();
                });
        addButton("触发关键词", () -> Text.literal(String.join(", ", config.keywords)),
                b -> {
                    if (this.client != null) {
                        this.client.setScreen(new KeywordScreen(this, config));
                    }
                });
        addButton("匹配方式", () -> Text.literal(config.requireAllKeywords ? "需包含全部" : "包含任一即可"),
                b -> {
                    config.requireAllKeywords = !config.requireAllKeywords;
                    persist();
                });
        addButton("避免连续重复", () -> Text.literal(config.avoidRepeat ? "开启" : "关闭"),
                b -> {
                    config.avoidRepeat = !config.avoidRepeat;
                    persist();
                });
        addButton("播放音效", () -> Text.literal(config.playSound ? "开启" : "关闭"),
                b -> {
                    config.playSound = !config.playSound;
                    persist();
                });

        // —— 滑条 ——
        addSlider("触发冷却", config.cooldownSeconds / 60D,
                v -> (int) Math.round(v * 60) + " 秒",
                v -> config.cooldownSeconds = (int) Math.round(v * 60));
        addSlider("音量", config.volume,
                v -> Math.round(v * 100) + "%",
                v -> config.volume = (float) v);
        addSlider("最大宽度", (config.maxWidthPercent - 10) / 90D,
                v -> Math.round(10 + v * 90) + "%",
                v -> config.maxWidthPercent = (int) Math.round(10 + v * 90));
        addSlider("最大高度", (config.maxHeightPercent - 10) / 90D,
                v -> Math.round(10 + v * 90) + "%",
                v -> config.maxHeightPercent = (int) Math.round(10 + v * 90));
        addSlider("顶部距离", config.topMarginPercent / 50D,
                v -> Math.round(v * 50) + "%",
                v -> config.topMarginPercent = (int) Math.round(v * 50));

        // —— 固定在底部的完成按钮 ——
        addDrawableChild(ButtonWidget.builder(Text.literal("完成"), b -> close())
                .dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());

        refreshLabels();
        layout();
    }

    private void addButton(String label, Supplier<Text> message, ButtonWidget.PressAction onPress) {
        ButtonWidget button = ButtonWidget.builder(message.get(), onPress)
                .dimensions(0, 0, WIDGET_W, 20).build();
        addDrawableChild(button);
        widgets.add(button);
        labels.add(label);
        messages.add(message);
    }

    private void addSlider(String label, double value, DoubleFunction<String> format, DoubleConsumer onChange) {
        ConfigSlider slider = new ConfigSlider(value, format, onChange);
        addDrawableChild(slider);
        widgets.add(slider);
        labels.add(label);
        messages.add(null);
    }

    /** 保存配置并刷新按钮文字 */
    private void persist() {
        config.save();
        refreshLabels();
    }

    private void refreshLabels() {
        for (int i = 0; i < widgets.size(); i++) {
            Supplier<Text> supplier = messages.get(i);
            if (supplier != null && widgets.get(i) instanceof ButtonWidget button) {
                button.setMessage(supplier.get());
            }
        }
    }

    /** 依据当前滚动位置摆放控件；滚出可视区的控件直接隐藏 */
    private void layout() {
        int contentHeight = TOP + widgets.size() * ROW_H;
        int maxScroll = Math.max(0, contentHeight - (this.height - BOTTOM));
        this.scrollable = maxScroll > 0;
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));

        for (int i = 0; i < widgets.size(); i++) {
            ClickableWidget widget = widgets.get(i);
            int y = TOP + i * ROW_H - this.scroll;
            widget.setX(this.width / 2 + 5);
            widget.setY(y);
            widget.visible = y >= TOP - 12 && y <= this.height - BOTTOM;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, 0xFFFFFF);

        for (int i = 0; i < widgets.size(); i++) {
            ClickableWidget widget = widgets.get(i);
            if (!widget.visible) {
                continue;
            }
            String label = this.textRenderer.trimToWidth(labels.get(i), LABEL_W);
            context.drawTextWithShadow(this.textRenderer, Text.literal(label),
                    widget.getX() - 155, widget.getY() + 6, 0xE0E0E0);
        }

        if (this.scrollable) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("滚轮滚动查看更多"),
                    4, this.height - 12, 0x808080);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (this.scrollable) {
            this.scroll -= (int) Math.round(amount * 14);
            layout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        config.save();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    /** 数值 0~1 的滑条，显示文案与写回逻辑由调用方给出 */
    private static class ConfigSlider extends SliderWidget {
        private final DoubleFunction<String> format;
        private final DoubleConsumer onChange;

        ConfigSlider(double value, DoubleFunction<String> format, DoubleConsumer onChange) {
            super(0, 0, WIDGET_W, 20, Text.empty(), value);
            this.format = format;
            this.onChange = onChange;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal(this.format.apply(this.value)));
        }

        @Override
        protected void applyValue() {
            this.onChange.accept(this.value);
        }
    }

    /** 关键词编辑子界面：逗号分隔 */
    private static class KeywordScreen extends Screen {
        private final Screen parent;
        private final MjVideoConfig config;
        private TextFieldWidget field;

        KeywordScreen(Screen parent, MjVideoConfig config) {
            super(Text.literal("编辑触发关键词"));
            this.parent = parent;
            this.config = config;
        }

        @Override
        protected void init() {
            this.field = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, this.height / 2 - 10,
                    200, 20, Text.literal("关键词"));
            this.field.setMaxLength(512);
            this.field.setText(String.join(", ", config.keywords));
            this.addDrawableChild(this.field);
            this.setInitialFocus(this.field);

            this.addDrawableChild(ButtonWidget.builder(Text.literal("保存"), b -> {
                applyKeywords(this.field.getText());
                if (this.client != null) {
                    this.client.setScreen(this.parent);
                }
            }).dimensions(this.width / 2 - 100, this.height / 2 + 20, 95, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("取消"), b -> {
                if (this.client != null) {
                    this.client.setScreen(this.parent);
                }
            }).dimensions(this.width / 2 + 5, this.height / 2 + 20, 95, 20).build());
        }

        private void applyKeywords(String raw) {
            List<String> parsed = new ArrayList<>();
            for (String part : raw.split("[,，]")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && !parsed.contains(trimmed)) {
                    parsed.add(trimmed);
                }
            }
            if (!parsed.isEmpty()) {
                config.keywords = parsed;
                config.save();
            }
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                    this.width / 2, this.height / 2 - 40, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("多个关键词用逗号分隔，大小写不敏感"),
                    this.width / 2, this.height / 2 + 50, 0x808080);
        }

        @Override
        public void close() {
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
        }
    }
}
