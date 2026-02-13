package pl.przxmus.nickhider.client;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import pl.przxmus.nickhider.NickHider;
import pl.przxmus.nickhider.config.ConfigValidator;
import pl.przxmus.nickhider.config.PrivacyConfig;

public final class PrivacyConfigScreen extends Screen {
    private final Screen parent;

    private boolean hideLocalName;
    private boolean hideLocalSkin;
    private boolean hideOtherNames;
    private boolean hideOtherSkins;

    private EditBox localNameInput;
    private EditBox localSkinUserInput;
    private EditBox othersNameTemplateInput;
    private EditBox othersSkinUserInput;

    private Button saveButton;
    private Component validationMessage = CommonComponents.EMPTY;

    public PrivacyConfigScreen(Screen parent) {
        super(Component.translatable("nickhider.config.title"));
        this.parent = parent;

        PrivacyConfig config = NickHider.runtime().config();
        this.hideLocalName = config.hideLocalName;
        this.hideLocalSkin = config.hideLocalSkin;
        this.hideOtherNames = config.hideOtherNames;
        this.hideOtherSkins = config.hideOtherSkins;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = 42;

        this.addRenderableWidget(CycleButton.onOffBuilder(this.hideLocalName)
                .create(centerX - 155, top, 150, 20, Component.translatable("nickhider.config.hide_local_name"), (button, value) -> {
                    this.hideLocalName = value;
                    refreshValidation();
                }));

        this.addRenderableWidget(CycleButton.onOffBuilder(this.hideLocalSkin)
                .create(centerX + 5, top, 150, 20, Component.translatable("nickhider.config.hide_local_skin"), (button, value) -> {
                    this.hideLocalSkin = value;
                    refreshValidation();
                }));

        this.addRenderableWidget(CycleButton.onOffBuilder(this.hideOtherNames)
                .create(centerX - 155, top + 26, 150, 20, Component.translatable("nickhider.config.hide_other_names"), (button, value) -> {
                    this.hideOtherNames = value;
                    refreshValidation();
                }));

        this.addRenderableWidget(CycleButton.onOffBuilder(this.hideOtherSkins)
                .create(centerX + 5, top + 26, 150, 20, Component.translatable("nickhider.config.hide_other_skins"), (button, value) -> {
                    this.hideOtherSkins = value;
                    refreshValidation();
                }));

        int fieldTop = top + 68;

        this.localNameInput = addField(centerX - 155, fieldTop, Component.translatable("nickhider.config.local_name"), 16, NickHider.runtime().config().localName);
        this.localSkinUserInput = addField(centerX - 155, fieldTop + 36, Component.translatable("nickhider.config.local_skin_user"), 16, NickHider.runtime().config().localSkinUser);
        this.othersNameTemplateInput = addField(centerX - 155, fieldTop + 72, Component.translatable("nickhider.config.others_name_template"), 16, NickHider.runtime().config().othersNameTemplate);
        this.othersSkinUserInput = addField(centerX - 155, fieldTop + 108, Component.translatable("nickhider.config.others_skin_user"), 16, NickHider.runtime().config().othersSkinUser);

        this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("nickhider.config.save"), button -> saveAndClose())
                .bounds(centerX - 155, this.height - 28, 150, 20)
                .build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX + 5, this.height - 28, 150, 20)
                .build());

        this.setInitialFocus(this.localNameInput);
        refreshValidation();
    }

    private EditBox addField(int x, int y, Component label, int maxLength, String value) {
        EditBox box = new EditBox(this.font, x, y + 12, 310, 20, label);
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setResponder(ignored -> refreshValidation());
        this.addRenderableWidget(box);
        return box;
    }

    private void refreshValidation() {
        PrivacyConfig candidate = collectConfig();
        List<String> errors = ConfigValidator.validate(candidate);

        this.saveButton.active = errors.isEmpty();
        this.validationMessage = errors.isEmpty()
                ? CommonComponents.EMPTY
                : Component.literal(errors.get(0)).withStyle(ChatFormatting.RED);
    }

    private PrivacyConfig collectConfig() {
        PrivacyConfig config = new PrivacyConfig();
        config.hideLocalName = this.hideLocalName;
        config.hideLocalSkin = this.hideLocalSkin;
        config.hideOtherNames = this.hideOtherNames;
        config.hideOtherSkins = this.hideOtherSkins;
        config.localName = this.localNameInput.getValue().trim();
        config.localSkinUser = this.localSkinUserInput.getValue().trim();
        config.othersNameTemplate = this.othersNameTemplateInput.getValue().trim();
        config.othersSkinUser = this.othersSkinUserInput.getValue().trim();
        return config;
    }

    private void saveAndClose() {
        PrivacyConfig config = collectConfig();
        List<String> errors = ConfigValidator.validate(config);
        if (!errors.isEmpty()) {
            this.validationMessage = Component.literal(errors.get(0)).withStyle(ChatFormatting.RED);
            return;
        }

        NickHider.runtime().saveConfig(config);
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int fieldTop = 110;

        graphics.drawCenteredString(this.font, this.title, centerX, 16, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("nickhider.config.local_name"), centerX - 155, fieldTop, 0xE0E0E0);
        graphics.drawString(this.font, Component.translatable("nickhider.config.local_skin_user"), centerX - 155, fieldTop + 36, 0xE0E0E0);
        graphics.drawString(this.font, Component.translatable("nickhider.config.others_name_template"), centerX - 155, fieldTop + 72, 0xE0E0E0);
        graphics.drawString(this.font, Component.translatable("nickhider.config.others_skin_user"), centerX - 155, fieldTop + 108, 0xE0E0E0);

        if (!this.validationMessage.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.validationMessage, centerX, this.height - 40, 0xFF5555);
        }
    }
}
