package dev.przxmus.nickhider.client;

import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Method;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import dev.przxmus.nickhider.NickHider;
import dev.przxmus.nickhider.config.ConfigValidationError;
import dev.przxmus.nickhider.config.ConfigValidator;
import dev.przxmus.nickhider.config.PrivacyConfig;

public final class PrivacyConfigScreen extends Screen {
    private final Screen parent;

    private boolean enabled;
    private boolean hideLocalName;
    private boolean hideLocalSkin;
    private boolean hideLocalCape;
    private boolean hideOtherNames;
    private boolean hideOtherSkins;
    private boolean hideOtherCapes;

    private EditBox localNameInput;
    private EditBox localSkinUserInput;
    private EditBox localCapeUserInput;
    private EditBox othersNameTemplateInput;
    private EditBox othersSkinUserInput;
    private EditBox othersCapeUserInput;

    private Button saveButton;
    private Component validationMessage = CommonComponents.EMPTY;
    private final List<ScrollableWidget> scrollableWidgets = new ArrayList<>();
    private final List<ScrollableLabel> scrollableLabels = new ArrayList<>();
    private int contentTop;
    private int contentBottom;
    private int contentHeight;
    private int scrollOffset;
    private int maxScroll;
    private int formLeft;
    private int formWidth;

    public PrivacyConfigScreen(Screen parent) {
        super(Component.translatable("nickhider.config.title"));
        this.parent = parent;

        PrivacyConfig config = NickHider.runtime().config();
        this.enabled = config.enabled;
        this.hideLocalName = config.hideLocalName;
        this.hideLocalSkin = config.hideLocalSkin;
        this.hideLocalCape = config.hideLocalCape;
        this.hideOtherNames = config.hideOtherNames;
        this.hideOtherSkins = config.hideOtherSkins;
        this.hideOtherCapes = config.hideOtherCapes;
    }

    @Override
    protected void init() {
        this.scrollableWidgets.clear();
        this.scrollableLabels.clear();
        this.scrollOffset = 0;

        int centerX = this.width / 2;
        this.formWidth = Math.max(220, Math.min(340, this.width - 20));
        this.formLeft = centerX - (this.formWidth / 2);

        int footerY = this.height - 28;
        this.contentTop = 42;
        this.contentBottom = Math.max(this.contentTop + 40, footerY - 18);

        int horizontalGap = 10;
        int toggleWidth = (this.formWidth - horizontalGap) / 2;
        int rightToggleX = this.formLeft + toggleWidth + horizontalGap;
        int y = 0;

        CycleButton<Boolean> enabledButton = CycleButton.onOffBuilder(this.enabled)
                .create(this.formLeft, this.contentTop, this.formWidth, 20, Component.translatable("nickhider.config.enabled"), (button, value) -> {
                    this.enabled = value;
                    refreshValidation();
                });
        this.addRenderableWidget(enabledButton);
        addScrollableWidget(enabledButton, y);
        y += 26;

        CycleButton<Boolean> hideLocalNameButton = CycleButton.onOffBuilder(this.hideLocalName)
                .create(this.formLeft, this.contentTop, toggleWidth, 20, Component.translatable("nickhider.config.hide_local_name"), (button, value) -> {
                    this.hideLocalName = value;
                    refreshValidation();
                });
        this.addRenderableWidget(hideLocalNameButton);
        addScrollableWidget(hideLocalNameButton, y);

        CycleButton<Boolean> hideLocalSkinButton = CycleButton.onOffBuilder(this.hideLocalSkin)
                .create(rightToggleX, this.contentTop, toggleWidth, 20, Component.translatable("nickhider.config.hide_local_skin"), (button, value) -> {
                    this.hideLocalSkin = value;
                    refreshValidation();
                });
        this.addRenderableWidget(hideLocalSkinButton);
        addScrollableWidget(hideLocalSkinButton, y);
        y += 26;

        CycleButton<Boolean> hideLocalCapeButton = CycleButton.onOffBuilder(this.hideLocalCape)
                .create(this.formLeft, this.contentTop, toggleWidth, 20, Component.translatable("nickhider.config.hide_local_cape"), (button, value) -> {
                    this.hideLocalCape = value;
                    refreshValidation();
                });
        this.addRenderableWidget(hideLocalCapeButton);
        addScrollableWidget(hideLocalCapeButton, y);

        CycleButton<Boolean> hideOtherNamesButton = CycleButton.onOffBuilder(this.hideOtherNames)
                .create(rightToggleX, this.contentTop, toggleWidth, 20, Component.translatable("nickhider.config.hide_other_names"), (button, value) -> {
                    this.hideOtherNames = value;
                    refreshValidation();
                });
        this.addRenderableWidget(hideOtherNamesButton);
        addScrollableWidget(hideOtherNamesButton, y);
        y += 26;

        CycleButton<Boolean> hideOtherSkinsButton = CycleButton.onOffBuilder(this.hideOtherSkins)
                .create(this.formLeft, this.contentTop, toggleWidth, 20, Component.translatable("nickhider.config.hide_other_skins"), (button, value) -> {
                    this.hideOtherSkins = value;
                    refreshValidation();
                });
        this.addRenderableWidget(hideOtherSkinsButton);
        addScrollableWidget(hideOtherSkinsButton, y);

        CycleButton<Boolean> hideOtherCapesButton = CycleButton.onOffBuilder(this.hideOtherCapes)
                .create(rightToggleX, this.contentTop, toggleWidth, 20, Component.translatable("nickhider.config.hide_other_capes"), (button, value) -> {
                    this.hideOtherCapes = value;
                    refreshValidation();
                });
        this.addRenderableWidget(hideOtherCapesButton);
        addScrollableWidget(hideOtherCapesButton, y);
        y += 40;

        this.localNameInput = addField(y, Component.translatable("nickhider.config.local_name"), 16, NickHider.runtime().config().localName);
        y += 36;
        this.localSkinUserInput = addField(y, Component.translatable("nickhider.config.local_skin_user"), 16, NickHider.runtime().config().localSkinUser);
        y += 36;
        this.localCapeUserInput = addField(y, Component.translatable("nickhider.config.local_cape_user"), 16, NickHider.runtime().config().localCapeUser);
        y += 36;
        this.othersNameTemplateInput = addField(y, Component.translatable("nickhider.config.others_name_template"), 16, NickHider.runtime().config().othersNameTemplate);
        y += 36;
        this.othersSkinUserInput = addField(y, Component.translatable("nickhider.config.others_skin_user"), 16, NickHider.runtime().config().othersSkinUser);
        y += 36;
        this.othersCapeUserInput = addField(y, Component.translatable("nickhider.config.others_cape_user"), 16, NickHider.runtime().config().othersCapeUser);
        y += 32;

        this.contentHeight = y;
        this.maxScroll = Math.max(0, this.contentHeight - (this.contentBottom - this.contentTop));

        this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("nickhider.config.save"), button -> saveAndClose())
                .bounds(this.formLeft, footerY, (this.formWidth - horizontalGap) / 2, 20)
                .build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(this.formLeft + ((this.formWidth - horizontalGap) / 2) + horizontalGap, footerY, (this.formWidth - horizontalGap) / 2, 20)
                .build());

        this.setInitialFocus(this.localNameInput);
        applyScrollLayout();
        refreshValidation();
    }

    private EditBox addField(int baseY, Component label, int maxLength, String value) {
        this.scrollableLabels.add(new ScrollableLabel(label, this.formLeft, baseY));

        EditBox box = new EditBox(this.font, this.formLeft, this.contentTop + baseY + 12, this.formWidth, 20, label);
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setResponder(ignored -> refreshValidation());
        this.addRenderableWidget(box);
        addScrollableWidget(box, baseY + 12);
        return box;
    }

    private void refreshValidation() {
        PrivacyConfig candidate = collectConfig();
        List<ConfigValidationError> errors = ConfigValidator.validate(candidate);

        this.saveButton.active = errors.isEmpty();
        this.validationMessage = errors.isEmpty()
                ? CommonComponents.EMPTY
                : Component.translatable(errors.get(0).translationKey());
    }

    private PrivacyConfig collectConfig() {
        PrivacyConfig config = new PrivacyConfig();
        config.enabled = this.enabled;
        config.hideLocalName = this.hideLocalName;
        config.hideLocalSkin = this.hideLocalSkin;
        config.hideLocalCape = this.hideLocalCape;
        config.hideOtherNames = this.hideOtherNames;
        config.hideOtherSkins = this.hideOtherSkins;
        config.hideOtherCapes = this.hideOtherCapes;
        config.localName = this.localNameInput.getValue().trim();
        config.localSkinUser = this.localSkinUserInput.getValue().trim();
        config.localCapeUser = this.localCapeUserInput.getValue().trim();
        config.othersNameTemplate = this.othersNameTemplateInput.getValue().trim();
        config.othersSkinUser = this.othersSkinUserInput.getValue().trim();
        config.othersCapeUser = this.othersCapeUserInput.getValue().trim();
        return config;
    }

    private void saveAndClose() {
        PrivacyConfig config = collectConfig();
        List<ConfigValidationError> errors = ConfigValidator.validate(config);
        if (!errors.isEmpty()) {
            this.validationMessage = Component.translatable(errors.get(0).translationKey());
            return;
        }

        NickHider.runtime().saveConfig(config);
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return handleMouseScroll(mouseX, mouseY, delta);
    }

    /*? if >=1.20.2 {*/
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return handleMouseScroll(mouseX, mouseY, verticalAmount);
    }
    /*?}*/

    private boolean handleMouseScroll(double mouseX, double mouseY, double delta) {
        if (mouseY < this.contentTop || mouseY > this.contentBottom || this.maxScroll <= 0) {
            return false;
        }

        int nextOffset = this.scrollOffset - (int) Math.round(delta * 18.0);
        this.scrollOffset = clamp(nextOffset, 0, this.maxScroll);
        applyScrollLayout();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackgroundCompat(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int footerY = this.height - 28;

        graphics.drawCenteredString(this.font, this.title, centerX, 16, 0xFFFFFF);
        for (ScrollableLabel label : this.scrollableLabels) {
            int y = this.contentTop + label.baseY - this.scrollOffset;
            if (y + this.font.lineHeight > this.contentTop && y < this.contentBottom) {
                graphics.drawString(this.font, label.text, label.x, y, 0xE0E0E0);
            }
        }

        if (!this.validationMessage.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.validationMessage, centerX, footerY - 12, 0xFF5555);
        }

        if (this.maxScroll > 0) {
            drawScrollbar(graphics);
        }
    }

    private void renderBackgroundCompat(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try {
            Method modern = Screen.class.getMethod("renderBackground", GuiGraphics.class, int.class, int.class, float.class);
            modern.invoke(this, graphics, mouseX, mouseY, partialTick);
            return;
        } catch (ReflectiveOperationException ignored) {}

        try {
            Method legacy = Screen.class.getMethod("renderBackground", GuiGraphics.class);
            legacy.invoke(this, graphics);
        } catch (ReflectiveOperationException ignored) {
            // No known background method available on this version.
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int trackX = this.formLeft + this.formWidth + 3;
        int trackWidth = 4;
        int viewportHeight = this.contentBottom - this.contentTop;
        int thumbHeight = Math.max(18, (int) (viewportHeight * (viewportHeight / (double) this.contentHeight)));
        int thumbRange = viewportHeight - thumbHeight;
        int thumbY = this.contentTop + (int) (thumbRange * (this.scrollOffset / (double) this.maxScroll));

        graphics.fill(trackX, this.contentTop, trackX + trackWidth, this.contentBottom, 0xFF2A2A2A);
        graphics.fill(trackX, thumbY, trackX + trackWidth, thumbY + thumbHeight, 0xFF8A8A8A);
    }

    private void addScrollableWidget(AbstractWidget widget, int baseY) {
        this.scrollableWidgets.add(new ScrollableWidget(widget, baseY));
    }

    private void applyScrollLayout() {
        for (ScrollableWidget entry : this.scrollableWidgets) {
            int y = this.contentTop + entry.baseY - this.scrollOffset;
            entry.widget.setY(y);
            boolean visible = y + entry.widget.getHeight() > this.contentTop && y < this.contentBottom;
            entry.widget.visible = visible;
            entry.widget.active = visible;
        }
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private record ScrollableWidget(AbstractWidget widget, int baseY) {}
    private record ScrollableLabel(Component text, int x, int baseY) {}
}
