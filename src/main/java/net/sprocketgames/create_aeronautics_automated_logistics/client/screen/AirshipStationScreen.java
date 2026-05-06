package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipStationMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.network.UpdateIdentityNamePayload;
import org.lwjgl.glfw.GLFW;

public class AirshipStationScreen extends AbstractContainerScreen<AirshipStationMenu> {
    private static final int PREVIEW_X = 10;
    private static final int PREVIEW_Y = 190;
    private static final int PREVIEW_W = 236;
    private static final int PREVIEW_H = 54;
    private static final int CONTENT_WIDTH = 236;
    private EditBox nameBox;

    public AirshipStationScreen(AirshipStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 254;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        int left = this.leftPos + 10;
        this.nameBox = new EditBox(
                this.font,
                this.leftPos + 10,
                this.topPos + 26,
                164,
                18,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.name")
        );
        this.nameBox.setMaxLength(64);
        if (this.minecraft != null && this.minecraft.player != null) {
            this.nameBox.setValue(this.menu.stationName(this.minecraft.player));
        }
        addRenderableWidget(this.nameBox);

        addRenderableWidget(Button.builder(
                Component.translatable("gui.create_aeronautics_automated_logistics.identity.save"),
                button -> saveName()
        ).bounds(this.leftPos + 181, this.topPos + 25, 65, 20).build());

        int top = this.topPos + 54;
        int width = 113;
        int height = 20;

        addRenderableWidget(Button.builder(
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.select_ship"),
                button -> pressAction(AirshipStationMenu.ACTION_SELECT_SHIP)
        ).bounds(left, top, width, height).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.record_segment"),
                button -> pressAction(AirshipStationMenu.ACTION_START_SEGMENT_RECORDING)
        ).bounds(left + 123, top, width, height).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.finish_segment"),
                button -> pressAction(AirshipStationMenu.ACTION_FINISH_SEGMENT_RECORDING)
        ).bounds(left, top + 24, CONTENT_WIDTH, height).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.run_schedule"),
                button -> pressAction(AirshipStationMenu.ACTION_RUN_SCHEDULE)
        ).bounds(left, top + 48, width, height).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.stop_schedule"),
                button -> pressAction(AirshipStationMenu.ACTION_STOP_SCHEDULE)
        ).bounds(left + 123, top + 48, width, height).build());

    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        guiGraphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, 0xFF22252C);
        guiGraphics.fill(left + 2, top + 2, left + this.imageWidth - 2, top + this.imageHeight - 2, 0xFF313742);
        guiGraphics.fill(left + PREVIEW_X, top + PREVIEW_Y, left + PREVIEW_X + PREVIEW_W, top + PREVIEW_Y + PREVIEW_H, 0xFF1D2229);
        guiGraphics.fill(left + PREVIEW_X + 1, top + PREVIEW_Y + 1, left + PREVIEW_X + PREVIEW_W - 1, top + PREVIEW_Y + PREVIEW_H - 1, 0xFF12171D);

        if (this.minecraft == null || this.minecraft.player == null || !AutomatedLogisticsConfig.SHOW_ROUTE_PREVIEW.get()) {
            return;
        }

        List<Vec3> points = menu.previewPoints(this.minecraft.player);
        if (points.size() >= 2) {
            drawRoutePreview(guiGraphics, points);
        }
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
        guiGraphics.drawWordWrap(
                this.font,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status", menu.statusText(this.minecraft.player)),
                10,
                124,
                CONTENT_WIDTH,
                0xE5E8EE
        );
        guiGraphics.drawWordWrap(
                this.font,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.selected_ship", menu.selectedShipText(this.minecraft.player)),
                10,
                136,
                CONTENT_WIDTH,
                0xD0D6E0
        );
        guiGraphics.drawString(
                this.font,
                menu.segmentSummary(this.minecraft.player),
                10,
                150,
                0xAEB8C5,
                false
        );
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.station_id", menu.stationIdText(this.minecraft.player)),
                10,
                162,
                0xAEB8C5,
                false
        );
        Component stopSummary = menu.stopSummary(this.minecraft.player);
        if (!stopSummary.getString().isBlank()) {
            guiGraphics.drawWordWrap(this.font, stopSummary, 118, 162, 128, 0xD0D6E0);
        }

        Component failure = menu.failureText(this.minecraft.player);
        if (!failure.getString().isBlank()) {
            guiGraphics.drawWordWrap(this.font, failure, 10, 174, CONTENT_WIDTH, 0xFFB4B4);
        }

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.preview"),
                PREVIEW_X + 3,
                PREVIEW_Y - 8,
                0xD0D6E0,
                false
        );
        if (!AutomatedLogisticsConfig.SHOW_ROUTE_PREVIEW.get()) {
            guiGraphics.drawString(
                    this.font,
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.preview_disabled"),
                    PREVIEW_X + 4,
                    PREVIEW_Y + 16,
                    0x8D96A3,
                    false
            );
        } else if (this.minecraft != null && this.minecraft.player != null && menu.previewPoints(this.minecraft.player).size() < 2) {
            List<Component> segmentLines = menu.segmentLines(this.minecraft.player);
            if (!segmentLines.isEmpty()) {
                for (int i = 0; i < segmentLines.size(); i++) {
                    guiGraphics.drawString(
                            this.font,
                            segmentLines.get(i),
                            PREVIEW_X + 4,
                            PREVIEW_Y + 6 + i * 11,
                            0xAFC7DE,
                            false
                    );
                }
            } else {
                Component emptyText = menu.isRecording(this.minecraft.player)
                        ? Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.preview_recording")
                        : Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.preview_empty");
                guiGraphics.drawString(
                        this.font,
                        emptyText,
                        PREVIEW_X + 4,
                        PREVIEW_Y + 16,
                        0x8D96A3,
                        false
                );
            }
        }
    }

    private void pressAction(int actionId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, actionId);
        }
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
        PacketDistributor.sendToServer(new UpdateIdentityNamePayload(this.menu.stationPos(), this.nameBox.getValue()));
    }

    private void drawRoutePreview(GuiGraphics guiGraphics, List<Vec3> points) {
        int left = this.leftPos + PREVIEW_X + 2;
        int top = this.topPos + PREVIEW_Y + 2;
        int width = PREVIEW_W - 4;
        int height = PREVIEW_H - 4;

        int maxPoints = Math.min(points.size(), 300);
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < maxPoints; i++) {
            Vec3 point = points.get(i);
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minZ = Math.min(minZ, point.z);
            maxZ = Math.max(maxZ, point.z);
        }

        double spanX = Math.max(maxX - minX, 1.0D);
        double spanZ = Math.max(maxZ - minZ, 1.0D);
        int prevX = -1;
        int prevY = -1;
        for (int i = 0; i < maxPoints; i++) {
            Vec3 point = points.get(i);
            int px = left + (int) ((point.x - minX) / spanX * (width - 1));
            int py = top + (int) ((point.z - minZ) / spanZ * (height - 1));
            if (prevX >= 0) {
                drawSegment(guiGraphics, prevX, prevY, px, py, 0xFF76C5FF);
            }
            prevX = px;
            prevY = py;
        }
    }

    private void drawSegment(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) {
            guiGraphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            return;
        }

        for (int i = 0; i <= steps; i++) {
            int x = x0 + dx * i / steps;
            int y = y0 + dy * i / steps;
            guiGraphics.fill(x, y, x + 1, y + 1, color);
        }
    }
}
