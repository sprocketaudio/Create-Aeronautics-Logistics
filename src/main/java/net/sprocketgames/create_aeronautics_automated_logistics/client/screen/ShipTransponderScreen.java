package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.network.UpdateIdentityNamePayload;
import org.lwjgl.glfw.GLFW;

public class ShipTransponderScreen extends AbstractContainerScreen<ShipTransponderMenu> {
    private EditBox nameBox;

    public ShipTransponderScreen(ShipTransponderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 230;
        this.imageHeight = 142;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        this.nameBox = new EditBox(
                this.font,
                this.leftPos + 16,
                this.topPos + 38,
                136,
                18,
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.name")
        );
        this.nameBox.setMaxLength(64);
        if (this.minecraft != null && this.minecraft.player != null) {
            this.nameBox.setValue(this.menu.shipName(this.minecraft.player));
        }
        addRenderableWidget(this.nameBox);

        addRenderableWidget(Button.builder(
                Component.translatable("gui.create_aeronautics_automated_logistics.identity.save"),
                button -> saveName()
        ).bounds(this.leftPos + 158, this.topPos + 37, 56, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        guiGraphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, 0xFF20242A);
        guiGraphics.fill(left + 2, top + 2, left + this.imageWidth - 2, top + this.imageHeight - 2, 0xFF323944);
        guiGraphics.fill(left + 10, top + 30, left + this.imageWidth - 10, top + this.imageHeight - 10, 0xFF252C35);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 10, 10, 0xFFFFFF, false);
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.name"),
                16,
                28,
                0xDDE4EE,
                false
        );
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.transponder_id", menu.shipIdText(this.minecraft.player)),
                16,
                68,
                0xC8D0DA,
                false
        );
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.runtime_id", menu.runtimeShipText(this.minecraft.player)),
                16,
                84,
                0xC8D0DA,
                false
        );
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.last_position", menu.lastKnownPositionText(this.minecraft.player)),
                16,
                100,
                0xC8D0DA,
                false
        );
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameBox != null && this.nameBox.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            return this.nameBox.keyPressed(keyCode, scanCode, modifiers) || this.nameBox.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.nameBox != null && this.nameBox.isFocused() && this.nameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void saveName() {
        PacketDistributor.sendToServer(new UpdateIdentityNamePayload(this.menu.transponderPos(), this.nameBox.getValue()));
    }
}
