package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import com.mojang.math.Axis;
import com.simibubi.create.foundation.gui.ModularGuiLine;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScreenOverlay;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import com.simibubi.create.foundation.gui.widget.TooltipArea;
import com.simibubi.create.content.trains.schedule.DestinationSuggestions;
import com.simibubi.create.content.trains.schedule.condition.CargoThresholdCondition.Ops;
import com.simibubi.create.content.trains.schedule.condition.TimedWaitCondition.TimeUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.createmod.catnip.data.IntAttached;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipScheduleMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.network.UpdateAirshipSchedulePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModItems;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitDurationUnit;
import org.lwjgl.glfw.GLFW;

public class AirshipScheduleScreen extends AbstractContainerScreen<AirshipScheduleMenu> {
    private static final ResourceLocation SCHEDULE_BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/schedule.png");
    private static final ResourceLocation SCHEDULE_EDITOR =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/schedule_2.png");
    private static final ResourceLocation CREATE_ICONS =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png");
    private static final ResourceLocation CREATE_WIDGETS =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/widgets.png");
    private static final ResourceLocation PLAYER_INVENTORY =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/player_inventory.png");
    private static final ResourceLocation DISPLAY_LINK =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/display_link.png");
    private static final int CARD_HEADER = 22;
    private static final int CARD_WIDTH = 195;
    private static final int TEXT_COLOR = 0xE8E8E8;
    private static final int DARK_TEXT_COLOR = 0x505050;
    private static final int MUTED_TEXT_COLOR = 0xB8B8B8;
    private static final int ACTION_FIELD_MAX_WIDTH = 150;
    private EditBox titleBox;
    private EditBox stationFilterBox;
    private EditBox editorStationBox;
    private ScrollInput editorDurationInput;
    private SelectionScrollInput editorUnitInput;
    private StationSuggestions stationSuggestions;
    private final EditorSubWidgets editorSubWidgets = new EditorSubWidgets();
    private final CompoundTag editorData = new CompoundTag();
    private AirshipSchedule localSchedule;
    private EditorMode editorMode = EditorMode.NONE;
    private int editorEntryIndex;
    private int editorConditionGroup;
    private int editorConditionIndex;
    private int selectedIndex;
    private int scrollOffset;
    private final List<RouteSegment> routeChoices = new ArrayList<>();
    private int routeChoiceSelected;
    private int routeChoiceScroll;
    private boolean noRoutePopupOpen;
    private final List<Component> noRoutePopupLines = new ArrayList<>();
    private boolean leftMouseDown;
    private final Map<Integer, Integer> conditionScrollColumns = new HashMap<>();
    private final List<Slot> playerInventorySlots = new ArrayList<>();
    private boolean showPlayerInventorySlots;

    public AirshipScheduleScreen(AirshipScheduleMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 226;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        if (this.localSchedule == null) {
            this.localSchedule = this.minecraft != null && this.minecraft.player != null
                    ? this.menu.schedule(this.minecraft.player)
                    : AirshipSchedule.empty();
            this.selectedIndex = this.minecraft != null && this.minecraft.player != null
                    ? this.menu.selectedIndex(this.minecraft.player)
                    : 0;
            clampSelectedIndex();
        }
        AirshipSchedule schedule = currentSchedule();
        this.titleBox = new EditBox(
                this.font,
                this.leftPos + 74,
                this.topPos + 4,
                108,
                14,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.title")
        );
        this.titleBox.setBordered(false);
        this.titleBox.setTextColor(DARK_TEXT_COLOR);
        this.titleBox.setMaxLength(64);
        this.titleBox.setValue(schedule.title());
        this.titleBox.visible = false;
        this.titleBox.active = false;
        addRenderableWidget(this.titleBox);

        this.stationFilterBox = new EditBox(
                this.font,
                this.leftPos + 82,
                this.topPos + 92,
                107,
                8,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_filter")
        );
        this.stationFilterBox.setBordered(false);
        this.stationFilterBox.setTextColor(TEXT_COLOR);
        this.stationFilterBox.setTextColorUneditable(TEXT_COLOR);
        this.stationFilterBox.setMaxLength(64);
        this.stationFilterBox.setResponder(value -> {
            if (this.stationSuggestions != null) {
                this.stationSuggestions.updateCommandInfo();
            }
        });
        this.stationFilterBox.visible = false;
        this.stationFilterBox.active = false;
        addRenderableWidget(this.stationFilterBox);
        addRenderableWidget(this.editorSubWidgets);

        cachePlayerInventorySlots();
        this.showPlayerInventorySlots = false;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(SCHEDULE_BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        Component title = Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.title");
        guiGraphics.drawString(this.font, title.getVisualOrderText(), this.leftPos + 124 - this.font.width(title) / 2, this.topPos + 4, DARK_TEXT_COLOR, false);
        renderSchedule(guiGraphics, mouseX, mouseY);
        if (this.editorMode == EditorMode.NONE) {
            renderFooterButtons(guiGraphics, mouseX, mouseY);
        }
        if (this.editorMode != EditorMode.NONE) {
            renderEditor(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.showPlayerInventorySlots = this.editorMode != EditorMode.NONE;
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (this.stationSuggestions != null) {
            var pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(0, 0, 500);
            this.stationSuggestions.render(guiGraphics, mouseX, mouseY);
            pose.popPose();
        }
        renderHover(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.stationSuggestions != null) {
            this.stationSuggestions.tick();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (!this.showPlayerInventorySlots && this.playerInventorySlots.contains(slot)) {
            return;
        }
        super.renderSlot(guiGraphics, slot);
    }

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        if (this.editorMode == EditorMode.NONE && isPlayerInventoryRegion(x, y, width, height)) {
            return false;
        }
        return super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    private void renderSchedule(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        AirshipSchedule schedule = currentSchedule();
        int selectedIndex = selectedIndex();
        int absoluteLeft = this.leftPos;
        int absoluteTop = this.topPos;

        blitStretch(guiGraphics, SCHEDULE_BACKGROUND, absoluteLeft + 33, absoluteTop + 16, 3, 173, 5, 235, 3, 1);
        guiGraphics.enableScissor(absoluteLeft + 16, absoluteTop + 16, absoluteLeft + 236, absoluteTop + 189);

        int y = 25 - this.scrollOffset;
        List<AirshipScheduleEntry> entries = schedule.entries();
        for (int i = 0; i <= entries.size(); i++) {
            if (selectedIndex == i && !entries.isEmpty()) {
                int expectedY = absoluteTop + y + 4;
                int actualY = Mth.clamp(expectedY, absoluteTop + 18, absoluteTop + 170);
                if (expectedY == actualY) {
                    blit(guiGraphics, SCHEDULE_BACKGROUND, absoluteLeft, actualY, 185, 239, 21, 16);
                } else {
                    blit(guiGraphics, SCHEDULE_BACKGROUND, absoluteLeft, actualY, 171, 239, 13, 16);
                }
            }
            if (i == 0 || entries.isEmpty()) {
                blitStretch(guiGraphics, SCHEDULE_BACKGROUND, absoluteLeft + 33, absoluteTop + 16, 3, 10, 5, 237, 3, 1);
            }
            if (i == entries.size()) {
                if (i > 0) {
                    y += 9;
                }
                blit(guiGraphics, SCHEDULE_BACKGROUND, absoluteLeft + 29, absoluteTop + y, 34, 239, 11, 16);
                renderAddCard(guiGraphics, absoluteLeft + 43, absoluteTop + y);
                break;
            }

            AirshipScheduleEntry entry = entries.get(i);
            int cardHeight = cardHeight(entry);
            renderEntryCard(guiGraphics, entry, i, selectedIndex == i, absoluteLeft + 25, absoluteTop + y, cardHeight);
            y += cardHeight;
            if (i + 1 < entries.size()) {
                renderDottedStrip(guiGraphics, absoluteLeft + 33, absoluteTop + y - 2);
                y += 10;
            }
        }

        guiGraphics.disableScissor();
        guiGraphics.fillGradient(absoluteLeft + 16, absoluteTop + 16, absoluteLeft + 236, absoluteTop + 26, 200, 0x77000000, 0x00000000);
        guiGraphics.fillGradient(absoluteLeft + 16, absoluteTop + 179, absoluteLeft + 236, absoluteTop + 189, 200, 0x00000000, 0x77000000);

        if (entries.isEmpty()) {
            guiGraphics.drawWordWrap(
                    this.font,
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.empty"),
                    absoluteLeft + 62,
                    absoluteTop + 78,
                    145,
                    0xFFB0B0B0
            );
        }
    }

    private void renderEntryCard(GuiGraphics guiGraphics, AirshipScheduleEntry entry, int index, boolean selected, int x, int y, int height) {
        blitStretch(guiGraphics, SCHEDULE_BACKGROUND, x, y + 1, CARD_WIDTH, height - 2, 7, 233, 1, 1);
        blitStretch(guiGraphics, SCHEDULE_BACKGROUND, x + 1, y, CARD_WIDTH - 2, height, 7, 233, 1, 1);
        blitStretch(guiGraphics, SCHEDULE_BACKGROUND, x + 1, y + 1, CARD_WIDTH - 2, height - 2, 5, 233, 1, 1);
        blitStretch(guiGraphics, SCHEDULE_BACKGROUND, x + 2, y + 2, CARD_WIDTH - 4, height - 4, 6, 233, 1, 1);
        blitStretch(guiGraphics, SCHEDULE_BACKGROUND, x + 2, y + 2, CARD_WIDTH - 4, CARD_HEADER, 7, 233, 1, 1);
        blitStretch(guiGraphics, SCHEDULE_BACKGROUND, x + 8, y, 3, height + 10, 5, 237, 3, 1);
        blit(guiGraphics, SCHEDULE_BACKGROUND, x + 4, y + 6, 12, 239, 11, 16);
        blit(guiGraphics, SCHEDULE_BACKGROUND, x + 4, y + 28, 1, 239, 11, 16);
        Component actionText = Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.entry.travel",
                entry.displayStationName()
        );
        int actionFieldWidth = Math.min(ACTION_FIELD_MAX_WIDTH, Math.max(100, this.font.width(actionText) + 36));
        renderScheduleInput(guiGraphics, x + 26, y + 5, actionFieldWidth, false, actionText, ModItems.AIRSHIP_STATION.get().getDefaultInstance());

        blit(guiGraphics, SCHEDULE_BACKGROUND, x + CARD_WIDTH - 14, y + 2, 51, 243, 12, 12);
        blit(guiGraphics, SCHEDULE_BACKGROUND, x + CARD_WIDTH - 14, y + height - 14, 65, 243, 12, 12);
        if (index > 0) {
            blit(guiGraphics, SCHEDULE_BACKGROUND, x + CARD_WIDTH, y + CARD_HEADER - 14, 51, 230, 12, 12);
        }
        if (index < currentSchedule().entries().size() - 1) {
            blit(guiGraphics, SCHEDULE_BACKGROUND, x + CARD_WIDTH, y + CARD_HEADER, 65, 230, 12, 12);
        }

        renderConditions(guiGraphics, entry, index, x, y, height);
    }

    private void renderConditions(GuiGraphics guiGraphics, AirshipScheduleEntry entry, int entryIndex, int x, int y, int cardHeight) {
        int scrollColumns = conditionScrollColumns.getOrDefault(entryIndex, 0);
        int scrollPixels = conditionScrollPixels(entry, scrollColumns);
        int maxRows = entry.conditionGroups().stream().mapToInt(List::size).max().orElse(1);
        int clipTop = y + 24;
        int clipBottom = y + CARD_HEADER + 24 + maxRows * 18;
        int clipLeft = x + 18;
        int clipRight = x + CARD_WIDTH - 16;
        guiGraphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(-scrollPixels, 0, 0);
        int groupX = x + 26;
        for (List<AirshipScheduleCondition> group : entry.conditionGroups()) {
            int groupWidth = conditionColumnWidth(group, entry.waitUnit());
            int row = 0;
            for (; row < group.size(); row++) {
                renderScheduleInput(guiGraphics, groupX, y + 29 + row * 18, groupWidth, row != 0, conditionWaitText(group.get(row), entry.waitUnit()), Items.STRUCTURE_VOID.getDefaultInstance());
            }
            blit(guiGraphics, SCHEDULE_BACKGROUND, groupX + (groupWidth - 10) / 2, y + 29 + row * 18, 150, 245, 10, 10);
            groupX += groupWidth + 10;
        }
        blit(guiGraphics, SCHEDULE_BACKGROUND, groupX - 3, y + 29, 96, 239, 19, 16);
        pose.popPose();
        guiGraphics.disableScissor();

        if (isConditionAreaScrollable(entry)) {
            int center = y + (cardHeight - 8 + CARD_HEADER) / 2;
            if (scrollColumns > 0) {
                blit(guiGraphics, SCHEDULE_BACKGROUND, x + 15, center, 161, 247, 4, 8);
            }
            if (scrollColumns < Math.max(0, entry.conditionGroups().size() - 1)) {
                blit(guiGraphics, SCHEDULE_BACKGROUND, x + 178, center, 166, 247, 4, 8);
            }
            var fadePose = guiGraphics.pose();
            fadePose.pushPose();
            fadePose.translate(x, y, 0);
            fadePose.mulPose(Axis.ZP.rotationDegrees(-90));
            guiGraphics.fillGradient(-cardHeight + 2, 18, -2 - CARD_HEADER, 28, 200, 0x44000000, 0x00000000);
            guiGraphics.fillGradient(-cardHeight + 2, CARD_WIDTH - 26, -2 - CARD_HEADER, CARD_WIDTH - 16, 200, 0x00000000, 0x44000000);
            fadePose.popPose();
        }
    }

    private void renderAddCard(GuiGraphics guiGraphics, int x, int y) {
        blit(guiGraphics, SCHEDULE_BACKGROUND, x, y, 79, 239, 16, 16);
    }

    private void renderDottedStrip(GuiGraphics guiGraphics, int x, int y) {
        blit(guiGraphics, SCHEDULE_BACKGROUND, x - 4, y - 1, 23, 239, 11, 16);
    }

    private void renderField(GuiGraphics guiGraphics, int x, int y, int width, Component text) {
        guiGraphics.fill(x, y, x + width, y + 16, 0xFF8C8C8C);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 15, 0xFF6D6D6D);
        guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(text.getString(), width - 12), x + 6, y + 4, TEXT_COLOR, false);
    }

    private void renderScheduleInput(GuiGraphics guiGraphics, int x, int y, int width, boolean clean, Component text, net.minecraft.world.item.ItemStack icon) {
        blitStretch(guiGraphics, SCHEDULE_BACKGROUND, x, y, width, 16, 123, 239, 1, 16);
        blit(guiGraphics, SCHEDULE_BACKGROUND, clean ? x : x - 3, y, clean ? 147 : 116, 239, clean ? 2 : 6, 16);
        blit(guiGraphics, SCHEDULE_BACKGROUND, x + width - 2, y, 144, 239, 2, 16);
        boolean hasIcon = hasDisplayedIcon(icon);
        if (hasIcon) {
            blit(guiGraphics, SCHEDULE_BACKGROUND, x + 3, y, 125, 239, 18, 16);
            guiGraphics.renderItem(icon, x + 4, y);
        }
        int textLimit = Math.min(120, width - (hasIcon ? 36 : 12));
        guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(text.getString(), textLimit), x + (hasIcon ? 28 : 8), y + 4, TEXT_COLOR, false);
    }

    private void renderDataAreaBox(GuiGraphics guiGraphics, int x, int y, int width, boolean speechBubble) {
        blitStretch(guiGraphics, DISPLAY_LINK, x, y, width, 18, 3, 163, 1, 18);
        if (speechBubble) {
            blit(guiGraphics, DISPLAY_LINK, x - 3, y, 8, 163, 5, 18);
        } else {
            blit(guiGraphics, DISPLAY_LINK, x, y, 0, 163, 2, 18);
        }
        blit(guiGraphics, DISPLAY_LINK, x + width - 2, y, 5, 163, 2, 18);
    }

    private void renderFooterButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        AirshipSchedule schedule = currentSchedule();
        boolean resetActive = selectedIndex() > 0 && !schedule.entries().isEmpty();
        boolean skipActive = schedule.entries().size() > 1;
        renderIconButton(
                guiGraphics,
                this.leftPos + 21,
                this.topPos + 196,
                48,
                16,
                schedule.loop(),
                isHoveringButton(mouseX, mouseY, this.leftPos + 21, this.topPos + 196),
                isPressedButton(mouseX, mouseY, this.leftPos + 21, this.topPos + 196),
                true
        );
        renderIconButton(
                guiGraphics,
                this.leftPos + 45,
                this.topPos + 196,
                112,
                0,
                false,
                isHoveringButton(mouseX, mouseY, this.leftPos + 45, this.topPos + 196),
                isPressedButton(mouseX, mouseY, this.leftPos + 45, this.topPos + 196),
                resetActive
        );
        renderIconButton(
                guiGraphics,
                this.leftPos + 63,
                this.topPos + 196,
                80,
                0,
                false,
                isHoveringButton(mouseX, mouseY, this.leftPos + 63, this.topPos + 196),
                isPressedButton(mouseX, mouseY, this.leftPos + 63, this.topPos + 196),
                skipActive
        );
        renderIconButton(
                guiGraphics,
                this.leftPos + this.imageWidth - 42,
                this.topPos + this.imageHeight - 30,
                0,
                16,
                false,
                isHoveringButton(mouseX, mouseY, this.leftPos + this.imageWidth - 42, this.topPos + this.imageHeight - 30),
                isPressedButton(mouseX, mouseY, this.leftPos + this.imageWidth - 42, this.topPos + this.imageHeight - 30),
                true
        );
    }

    private void renderIconButton(GuiGraphics guiGraphics, int x, int y, int iconU, int iconV, boolean green, boolean hovered, boolean pressed, boolean active) {
        int u;
        int v;
        if (!active) {
            u = 90;
            v = 0;
        } else if (hovered && pressed) {
            u = 36;
            v = 0;
        } else if (hovered) {
            u = 18;
            v = 0;
        } else if (green) {
            u = 72;
            v = 0;
        } else {
            u = 0;
            v = 0;
        }
        guiGraphics.blit(CREATE_WIDGETS, x, y, 0, u, v, 18, 18, 256, 256);
        guiGraphics.blit(CREATE_ICONS, x + 1, y + 1, 0, iconU, iconV, 16, 16, 256, 256);
    }

    private boolean isHoveringButton(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
    }

    private boolean isPressedButton(int mouseX, int mouseY, int x, int y) {
        return this.leftMouseDown && isHoveringButton(mouseX, mouseY, x, y);
    }

    private void blit(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        guiGraphics.blit(texture, x, y, 0, u, v, width, height, 256, 256);
    }

    private void blitStretch(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int width, int height, int u, int v, int uWidth, int vHeight) {
        guiGraphics.blit(texture, x, y, width, height, (float) u, (float) v, uWidth, vHeight, 256, 256);
    }

    private int conditionColumnWidth(List<AirshipScheduleCondition> group, WaitDurationUnit waitUnit) {
        int width = 32;
        for (AirshipScheduleCondition condition : group) {
            width = Math.max(width, fieldSize(32, conditionWaitText(condition, waitUnit), Items.STRUCTURE_VOID.getDefaultInstance()));
        }
        return width;
    }

    private int conditionScrollPixels(AirshipScheduleEntry entry, int scrollColumns) {
        int pixels = 0;
        int maxColumns = Math.min(scrollColumns, Math.max(0, entry.conditionGroups().size() - 1));
        for (int i = 0; i < maxColumns; i++) {
            pixels += conditionColumnWidth(entry.conditionGroups().get(i), entry.waitUnit()) + 10;
        }
        return pixels;
    }

    private boolean isConditionAreaScrollable(AirshipScheduleEntry entry) {
        int totalWidth = 26;
        for (List<AirshipScheduleCondition> group : entry.conditionGroups()) {
            totalWidth += conditionColumnWidth(group, entry.waitUnit()) + 10;
        }
        return totalWidth + 16 > CARD_WIDTH - 26;
    }

    private void scrollConditionColumns(int entryIndex, int direction) {
        if (entryIndex < 0 || entryIndex >= currentSchedule().entries().size()) {
            return;
        }
        AirshipScheduleEntry entry = currentSchedule().entries().get(entryIndex);
        if (!isConditionAreaScrollable(entry)) {
            conditionScrollColumns.remove(entryIndex);
            return;
        }
        int max = Math.max(0, entry.conditionGroups().size() - 1);
        int next = Mth.clamp(conditionScrollColumns.getOrDefault(entryIndex, 0) + direction, 0, max);
        conditionScrollColumns.put(entryIndex, next);
    }

    private void renderEditor(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 200);
        guiGraphics.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
        int x = this.leftPos - 2;
        int y = this.topPos + 40;
        blit(guiGraphics, SCHEDULE_EDITOR, x, y, 0, 0, 256, 89);
        if (this.editorMode != EditorMode.ROUTE) {
            blit(guiGraphics, PLAYER_INVENTORY, this.leftPos + 38, this.topPos + 122, 0, 0, 176, 108);
            guiGraphics.drawString(this.font, this.playerInventoryTitle, this.leftPos + 46, this.topPos + 128, DARK_TEXT_COLOR, false);
        }
        Component title = switch (this.editorMode) {
            case STATION -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.instruction_editor");
            case CONDITION -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.condition_editor");
            case ROUTE -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.route_editor");
            case NONE -> Component.empty();
        };
        guiGraphics.drawString(this.font, title.getVisualOrderText(), this.leftPos + 124 - this.font.width(title) / 2, this.topPos + 44, DARK_TEXT_COLOR, false);

        if (this.editorMode == EditorMode.ROUTE) {
            renderRouteChoiceEditor(guiGraphics, mouseX, mouseY);
        } else if (this.editorMode == EditorMode.STATION) {
            renderIconButton(
                    guiGraphics,
                    this.leftPos + 11,
                    this.topPos + 87,
                    16,
                    0,
                    false,
                    isHoveringButton(mouseX, mouseY, this.leftPos + 11, this.topPos + 87),
                    isPressedButton(mouseX, mouseY, this.leftPos + 11, this.topPos + 87),
                    true
            );
            renderEditorChoiceText(guiGraphics, this.leftPos + 61, this.topPos + 69, 135, Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.travel_to_station"));
            guiGraphics.renderItem(ModItems.AIRSHIP_STATION.get().getDefaultInstance(), this.leftPos + 54, this.topPos + 88);
        } else {
            if (canRemoveEditedCondition()) {
                renderIconButton(
                        guiGraphics,
                        this.leftPos + 11,
                        this.topPos + 87,
                        16,
                        0,
                        false,
                        isHoveringButton(mouseX, mouseY, this.leftPos + 11, this.topPos + 87),
                        isPressedButton(mouseX, mouseY, this.leftPos + 11, this.topPos + 87),
                        true
                );
            }
            AirshipScheduleCondition condition = currentCondition()
                    .orElse(AirshipScheduleCondition.scheduledDelay(WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)));
            renderEditorChoiceText(guiGraphics, this.leftPos + 61, this.topPos + 69, 135, conditionActionText(condition));
            guiGraphics.renderItem(conditionIcon(condition), this.leftPos + 54, this.topPos + 88);
        }

        if (this.editorMode == EditorMode.STATION || this.editorMode == EditorMode.CONDITION) {
            var widgetsPose = guiGraphics.pose();
            widgetsPose.pushPose();
            widgetsPose.translate(0, this.topPos + 87, 0);
            this.editorSubWidgets.renderBg(this.leftPos + 77, guiGraphics);
            widgetsPose.popPose();
        }

        if (this.editorMode != EditorMode.NONE) {
            renderIconButton(
                    guiGraphics,
                    this.leftPos + 224,
                    this.topPos + 87,
                    0,
                    16,
                    false,
                    isHoveringButton(mouseX, mouseY, this.leftPos + 224, this.topPos + 87),
                    isPressedButton(mouseX, mouseY, this.leftPos + 224, this.topPos + 87),
                    true
            );
        }
        if (this.noRoutePopupOpen) {
            renderNoRoutePopup(guiGraphics, mouseX, mouseY);
        }
        pose.popPose();
    }

    private void renderNoRoutePopup(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int overlayLeft = this.leftPos + 4;
        int overlayTop = this.topPos + 40;
        int overlayRight = this.leftPos + 250;
        int overlayBottom = this.topPos + 155;
        guiGraphics.fill(overlayLeft, overlayTop, overlayRight, overlayBottom, 0xB0000000);

        int boxW = 188;
        int boxH = 64;
        int x = this.leftPos + (this.imageWidth - boxW) / 2;
        int y = this.topPos + 67;
        guiGraphics.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, 0xFF111111);
        guiGraphics.fill(x, y, x + boxW, y + boxH, 0xEE313842);
        guiGraphics.drawCenteredString(this.font, "No Route", x + boxW / 2, y + 7, 0xFFFFC66E);

        int lineY = y + 21;
        for (Component line : this.noRoutePopupLines) {
            guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(line.getString(), boxW - 16), x + 8, lineY, 0xFFD8DDE6, false);
            lineY += 11;
        }

        boolean okHovered = mouseX >= x + boxW / 2 - 20
                && mouseX < x + boxW / 2 - 2
                && mouseY >= y + boxH - 18
                && mouseY < y + boxH;
        renderIconButton(
                guiGraphics,
                x + boxW / 2 - 20,
                y + boxH - 18,
                0,
                16,
                false,
                okHovered,
                okHovered && this.leftMouseDown,
                true
        );
    }

    private void renderRouteChoiceEditor(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int listX = this.leftPos + 52;
        int listY = this.topPos + 83;
        int rowHeight = 18;
        int visibleRows = 2;
        int rowWidth = 149;
        int panelX = listX - 4;
        int panelY = this.topPos + 61;
        int panelW = rowWidth + 8;
        int panelH = rowHeight * visibleRows + 28;

        guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF16181B);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE333943);
        guiGraphics.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 18, 0xEE474D56);
        Component prompt = Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.choose_route");
        guiGraphics.drawCenteredString(this.font, prompt, panelX + panelW / 2, panelY + 5, 0xFFD8DDE6);

        guiGraphics.enableScissor(listX, listY, listX + rowWidth, listY + rowHeight * visibleRows);
        for (int i = 0; i < visibleRows; i++) {
            int routeIndex = this.routeChoiceScroll + i;
            if (routeIndex < 0 || routeIndex >= this.routeChoices.size()) {
                continue;
            }
            RouteSegment segment = this.routeChoices.get(routeIndex);
            boolean selected = routeIndex == this.routeChoiceSelected;
            int rowY = listY + i * rowHeight;
            boolean hovered = mouseX >= listX && mouseX < listX + rowWidth && mouseY >= rowY && mouseY < rowY + rowHeight;
            if (hovered) {
                guiGraphics.fill(listX, rowY, listX + rowWidth, rowY + rowHeight, 0xAA555B65);
            } else if (selected) {
                guiGraphics.fill(listX, rowY, listX + rowWidth, rowY + rowHeight, 0x884B4F57);
            }
            Component line = routeChoiceLine(segment);
            guiGraphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(line.getString(), 136),
                    listX + 6,
                    listY + i * rowHeight + 4,
                    selected ? 0xFFFFE066 : TEXT_COLOR,
                    false
            );
        }
        guiGraphics.disableScissor();
        if (this.routeChoiceScroll > 0) {
            guiGraphics.fillGradient(listX, listY, listX + rowWidth, listY + 5, 0xCC333943, 0x00333943);
        }
        if (this.routeChoiceScroll + visibleRows < this.routeChoices.size()) {
            int fadeBottomY = listY + rowHeight * visibleRows;
            guiGraphics.fillGradient(listX, fadeBottomY - 5, listX + rowWidth, fadeBottomY, 0x00333943, 0xCC333943);
        }

        if (this.routeChoiceScroll > 0) {
            blit(guiGraphics, SCHEDULE_BACKGROUND, this.leftPos + 202, this.topPos + 87, 51, 230, 12, 12);
        }
        if (this.routeChoiceScroll + visibleRows < this.routeChoices.size()) {
            blit(guiGraphics, SCHEDULE_BACKGROUND, this.leftPos + 202, this.topPos + 102, 65, 230, 12, 12);
        }
    }

    private boolean hasDisplayedIcon(net.minecraft.world.item.ItemStack icon) {
        return !icon.isEmpty() && icon.getItem() != Items.STRUCTURE_VOID;
    }

    private int fieldSize(int minSize, Component text, net.minecraft.world.item.ItemStack icon) {
        return Math.max((text == null ? 0 : this.font.width(text)) + (hasDisplayedIcon(icon) ? 20 : 0) + 16, minSize);
    }

    private void renderEditorChoiceText(GuiGraphics guiGraphics, int x, int y, int width, Component text) {
        guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(text.getString(), width), x, y, TEXT_COLOR, false);
    }

    private void renderHover(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<Component> tooltip = tooltipAt(mouseX, mouseY);
        if (!tooltip.isEmpty()) {
            guiGraphics.renderTooltip(this.font, tooltip.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
        } else if (this.editorMode != EditorMode.NONE) {
            renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        if (this.editorMode != EditorMode.NONE) {
            return editorTooltipAt(mouseX, mouseY);
        }
        int mx = mouseX - this.leftPos;
        int my = mouseY - this.topPos;
        if (inside(mx, my, 21, 196, 18, 18)) {
            return loopTooltip();
        }
        if (inside(mx, my, 45, 196, 18, 18)) {
            return List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.reset"));
        }
        if (inside(mx, my, 63, 196, 18, 18)) {
            return List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.skip"));
        }
        if (inside(mx, my, 214, 196, 18, 18)) {
            return List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.confirm"));
        }
        Hit hit = hitAt(mouseX, mouseY);
        return switch (hit.type) {
            case ADD_ENTRY -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.add_entry"));
            case STATION -> stopTooltip(hit.index);
            case REMOVE -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.remove_entry"));
            case DUPLICATE -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.duplicate"));
            case MOVE_UP -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.move_up"));
            case MOVE_DOWN -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.move_down"));
            case CONDITION_SCROLL_LEFT -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.scroll_left"));
            case CONDITION_SCROLL_RIGHT -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.scroll_right"));
            case CONDITION -> conditionTooltip(hit.index, hit.conditionGroup, hit.conditionIndex);
            case ADD_CONDITION -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.add_condition"));
            case ADD_ALTERNATIVE -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.alternative_condition"));
            default -> List.of();
        };
    }

    private List<Component> editorTooltipAt(int mouseX, int mouseY) {
        int mx = mouseX - this.leftPos;
        int my = mouseY - this.topPos;
        if (inside(mx, my, 224, 87, 18, 18)) {
            return List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.confirm"));
        }
        if (this.editorMode == EditorMode.ROUTE) {
            int hovered = routeChoiceAt(mx, my);
            if (hovered >= 0 && hovered < this.routeChoices.size()) {
                RouteSegment segment = this.routeChoices.get(hovered);
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.route_choice").withStyle(ChatFormatting.GOLD),
                        Component.literal(routeDisplayName(segment)),
                        Component.literal(segment.shipName()).withStyle(ChatFormatting.GRAY),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.route_points", segment.points().size()).withStyle(ChatFormatting.DARK_AQUA)
                );
            }
        }
        if (this.editorMode == EditorMode.STATION) {
            if (inside(mx, my, 11, 87, 18, 18)) {
                return List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.remove_entry"));
            }
            if (inside(mx, my, 56, 65, 143, 16)) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.next_action"),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.travel_to_station")
                );
            }
            if (inside(mx, my, 53, 87, 32, 18) || inside(mx, my, 77, 88, 121, 18)) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_name"),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_name_wildcard"),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_name_nearest")
                );
            }
        }
        if (this.editorMode == EditorMode.CONDITION) {
            if (inside(mx, my, 11, 87, 18, 18) && canRemoveEditedCondition()) {
                return List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.remove_entry"));
            }
            if (inside(mx, my, 56, 65, 143, 16)) {
                return conditionSelectorTooltip();
            }
            if (inside(mx, my, 53, 87, 18, 18) && currentCondition().map(AirshipScheduleCondition::waitCondition).map(this::isCargoWait).orElse(false)) {
                return List.of(
                        Component.translatable("create.schedule.condition.threshold.place_item"),
                        Component.translatable("create.schedule.condition.threshold.place_item_2").withStyle(ChatFormatting.GRAY),
                        Component.translatable("create.schedule.condition.threshold.place_item_3").withStyle(ChatFormatting.GRAY)
                );
            }
        }
        return List.of();
    }

    private List<Component> conditionSelectorTooltip() {
        WaitConditionType current = currentCondition()
                .map(AirshipScheduleCondition::waitCondition)
                .map(WaitCondition::type)
                .orElse(WaitConditionType.TIMED);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.continue_if_after").withStyle(ChatFormatting.BLUE));
        for (WaitConditionType type : conditionSelectorTypes()) {
            Component option = conditionActionText(AirshipScheduleCondition.fromWaitCondition(defaultWaitConditionForType(type)));
            tooltip.add(Component.literal((type == current ? "-> " : "> ")).append(option));
        }
        tooltip.add(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.scroll_select").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return tooltip;
    }

    private List<Component> loopTooltip() {
        return List.of(
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.loop_tooltip"),
                Component.translatable(currentSchedule().loop()
                                ? "gui.create_aeronautics_automated_logistics.airship_schedule.currently_enabled"
                                : "gui.create_aeronautics_automated_logistics.airship_schedule.currently_disabled")
                        .withStyle(currentSchedule().loop() ? ChatFormatting.DARK_GREEN : ChatFormatting.RED),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.loop_description").withStyle(ChatFormatting.GRAY)
        );
    }

    private List<Component> stopTooltip(int entryIndex) {
        AirshipScheduleEntry entry = currentSchedule().entries().get(entryIndex);
        return List.of(
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.next_stop").withStyle(ChatFormatting.GOLD),
                Component.literal("\"" + entry.displayStationName() + "\""),
                Component.empty(),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.left_click_edit").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        );
    }

    private List<Component> conditionTooltip(int entryIndex, int groupIndex, int conditionIndex) {
        AirshipScheduleEntry entry = currentSchedule().entries().get(entryIndex);
        if (groupIndex < 0 || groupIndex >= entry.conditionGroups().size()) {
            return List.of();
        }
        List<AirshipScheduleCondition> group = entry.conditionGroups().get(groupIndex);
        if (conditionIndex < 0 || conditionIndex >= group.size()) {
            return List.of();
        }
        AirshipScheduleCondition condition = group.get(conditionIndex);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.continue_if_after"));
        tooltip.add(conditionActionText(condition));
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_DOCKED
                || condition.waitCondition().type() == WaitConditionType.UNTIL_IDLE
                || condition.waitCondition().type() == WaitConditionType.UNTIL_ITEM_THRESHOLD
                || condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            tooltip.add(conditionWaitText(condition).copy()
                    .withStyle(ChatFormatting.DARK_AQUA));
        } else {
            tooltip.add(Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_schedule.for_time",
                    longConditionTimeText(condition, entry.waitUnit())
            ).withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.left_click_edit").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (canRemoveCondition(entry, group)) {
            tooltip.add(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.right_click_remove").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        return tooltip;
    }

    private boolean canRemoveCondition(AirshipScheduleEntry entry, List<AirshipScheduleCondition> group) {
        return group.size() > 1 || entry.conditionGroups().size() > 1;
    }

    private boolean canRemoveEditedCondition() {
        AirshipScheduleEntry entry = currentEntry().orElse(null);
        if (entry == null) {
            return false;
        }
        if (this.editorConditionGroup < 0 || this.editorConditionGroup >= entry.conditionGroups().size()) {
            return false;
        }
        List<AirshipScheduleCondition> group = entry.conditionGroups().get(this.editorConditionGroup);
        if (this.editorConditionIndex < 0 || this.editorConditionIndex >= group.size()) {
            return false;
        }
        return canRemoveCondition(entry, group);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.leftMouseDown = true;
        }
        if (this.noRoutePopupOpen) {
            int boxW = 188;
            int boxH = 64;
            int x = this.leftPos + (this.imageWidth - boxW) / 2;
            int y = this.topPos + 67;
            boolean onOk = mouseX >= x + boxW / 2 - 20
                    && mouseX < x + boxW / 2 - 2
                    && mouseY >= y + boxH - 18
                    && mouseY < y + boxH;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                closeNoRoutePopup();
                return true;
            }
            if (onOk) {
                closeNoRoutePopup();
                return true;
            }
            return true;
        }
        if (this.editorMode != EditorMode.NONE) {
            return mouseClickedEditor(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
        }

        int mx = (int) mouseX - this.leftPos;
        int my = (int) mouseY - this.topPos;
        if (inside(mx, my, 21, 196, 18, 18)) {
            pressAction(AirshipScheduleMenu.ACTION_TOGGLE_LOOP);
            return true;
        }
        if (inside(mx, my, 45, 196, 18, 18)) {
            pressAction(AirshipScheduleMenu.ACTION_SELECT_ENTRY_BASE);
            return true;
        }
        if (inside(mx, my, 63, 196, 18, 18)) {
            pressAction(AirshipScheduleMenu.ACTION_SELECT_NEXT);
            return true;
        }
        if (inside(mx, my, 214, 196, 18, 18)) {
            saveTitle();
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.closeContainer();
            }
            return true;
        }
        Hit hit = hitAt((int) mouseX, (int) mouseY);
        if (hit.type == HitType.NONE) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (hit.index >= 0) {
            pressAction(AirshipScheduleMenu.ACTION_SELECT_ENTRY_BASE + hit.index);
        }

        switch (hit.type) {
            case ADD_ENTRY -> {
                pressAction(AirshipScheduleMenu.ACTION_ADD_TRAVEL);
                openStationEditor(this.selectedIndex);
            }
            case STATION -> openStationEditor(hit.index);
            case REMOVE -> pressAction(AirshipScheduleMenu.ACTION_REMOVE);
            case DUPLICATE -> pressAction(AirshipScheduleMenu.ACTION_DUPLICATE);
            case MOVE_UP -> pressAction(AirshipScheduleMenu.ACTION_MOVE_UP);
            case MOVE_DOWN -> pressAction(AirshipScheduleMenu.ACTION_MOVE_DOWN);
            case CONDITION_SCROLL_LEFT -> scrollConditionColumns(hit.index, -1);
            case CONDITION_SCROLL_RIGHT -> scrollConditionColumns(hit.index, 1);
            case CONDITION -> {
                if (button == 1) {
                    removeConditionLocally(hit.index, hit.conditionGroup, hit.conditionIndex);
                    syncSchedule();
                } else {
                    openConditionEditor(hit.index, hit.conditionGroup, hit.conditionIndex);
                }
            }
            case ADD_CONDITION -> {
                addConditionLocally(hit.index, hit.conditionGroup);
                syncSchedule();
                openConditionEditorForLast(hit.index, hit.conditionGroup);
            }
            case ADD_ALTERNATIVE -> {
                int newGroupIndex = currentSchedule().entries().get(hit.index).conditionGroups().size();
                addAlternativeConditionLocally(hit.index);
                syncSchedule();
                openConditionEditorForLast(hit.index, newGroupIndex);
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.leftMouseDown = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean mouseClickedEditor(double mouseX, double mouseY, int button) {
        if (this.stationSuggestions != null && this.stationSuggestions.mouseClicked((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        int mx = (int) mouseX - this.leftPos;
        int my = (int) mouseY - this.topPos;
        if (this.editorMode == EditorMode.ROUTE) {
            int routeIndex = routeChoiceAt(mx, my);
            if (routeIndex >= 0 && routeIndex < this.routeChoices.size()) {
                this.routeChoiceSelected = routeIndex;
                return true;
            }
            if (inside(mx, my, 202, 87, 12, 12) && this.routeChoiceScroll > 0) {
                this.routeChoiceScroll--;
                return true;
            }
            if (inside(mx, my, 202, 102, 12, 12) && this.routeChoiceScroll + 2 < this.routeChoices.size()) {
                this.routeChoiceScroll++;
                return true;
            }
        }
        if (this.editorMode == EditorMode.STATION) {
            if (inside(mx, my, 11, 87, 18, 18)) {
                removeSelectedLocally(currentSchedule());
                syncSchedule();
                closeEditor();
                return true;
            }
            if (this.editorStationBox != null && inside(mx, my, 77, 88, 121, 18)) {
                this.editorStationBox.mouseClicked(mouseX, mouseY, button);
                this.editorStationBox.setFocused(true);
                setFocused(this.editorStationBox);
                if (this.stationSuggestions != null) {
                    this.stationSuggestions.forceShow();
                }
                return true;
            }
        }
        if (inside(mx, my, 224, 87, 18, 18)) {
            confirmEditor();
            return true;
        }
        if (this.editorMode == EditorMode.CONDITION) {
            if (inside(mx, my, 56, 65, 143, 16)) {
                cycleEditedConditionType();
                return true;
            }
            if (inside(mx, my, 53, 87, 18, 18) && currentCondition().map(AirshipScheduleCondition::waitCondition).map(this::isCargoWait).orElse(false)) {
                setEditedConditionFilter(this.menu.getCarried());
                return true;
            }
            if (inside(mx, my, 11, 87, 18, 18) && canRemoveEditedCondition()) {
                removeConditionLocally(this.editorEntryIndex, this.editorConditionGroup, this.editorConditionIndex);
                syncSchedule();
                closeEditor();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (this.stationSuggestions != null && this.stationSuggestions.mouseScrolled(Mth.clamp(scrollY, -1.0D, 1.0D))) {
            return true;
        }
        if (this.editorMode == EditorMode.ROUTE) {
            int max = Math.max(0, this.routeChoices.size() - 2);
            this.routeChoiceScroll = Mth.clamp(this.routeChoiceScroll + (scrollY > 0 ? -1 : 1), 0, max);
            this.routeChoiceSelected = Mth.clamp(this.routeChoiceSelected, this.routeChoiceScroll, Math.min(this.routeChoices.size() - 1, this.routeChoiceScroll + 1));
            return true;
        }
        if (this.editorMode == EditorMode.CONDITION) {
            int mx = (int) mouseX - this.leftPos;
            int my = (int) mouseY - this.topPos;
            if (inside(mx, my, 56, 65, 143, 16)) {
                cycleEditedConditionType(scrollY > 0 ? -1 : 1);
                return true;
            }
        }
        if (this.editorMode != EditorMode.NONE) {
            return true;
        }
        Hit hoverHit = hitAt((int) mouseX, (int) mouseY);
        if (hoverHit.type == HitType.CONDITION
                || hoverHit.type == HitType.ADD_CONDITION
                || hoverHit.type == HitType.ADD_ALTERNATIVE
                || hoverHit.type == HitType.CONDITION_SCROLL_LEFT
                || hoverHit.type == HitType.CONDITION_SCROLL_RIGHT) {
            AirshipScheduleEntry entry = hoverHit.index >= 0 && hoverHit.index < currentSchedule().entries().size()
                    ? currentSchedule().entries().get(hoverHit.index)
                    : null;
            if (entry != null && isConditionAreaScrollable(entry)) {
                scrollConditionColumns(hoverHit.index, scrollY > 0 ? -1 : 1);
                return true;
            }
        }
        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            this.scrollOffset = Mth.clamp((int) (this.scrollOffset - scrollY * 12), 0, maxScroll);
            return true;
        }
        this.scrollOffset = 0;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.noRoutePopupOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                closeNoRoutePopup();
            }
            return true;
        }
        if (this.stationSuggestions != null && this.stationSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.editorMode != EditorMode.NONE) {
            closeEditor();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.editorMode != EditorMode.NONE) {
                confirmEditor();
                return true;
            }
            saveTitle();
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
            if (this.titleBox != null && this.titleBox.isFocused()) {
                return this.titleBox.keyPressed(keyCode, scanCode, modifiers) || this.titleBox.canConsumeInput();
            }
            if (this.stationFilterBox != null && this.stationFilterBox.isFocused()) {
                return this.stationFilterBox.keyPressed(keyCode, scanCode, modifiers) || this.stationFilterBox.canConsumeInput();
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (super.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (this.titleBox != null && this.titleBox.isFocused() && this.titleBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (this.stationFilterBox != null && this.stationFilterBox.isFocused() && this.stationFilterBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        this.leftMouseDown = false;
        this.showPlayerInventorySlots = true;
        saveTitle();
        super.removed();
    }

    private void cachePlayerInventorySlots() {
        if (!this.playerInventorySlots.isEmpty()) {
            return;
        }
        int count = this.menu.slots.size();
        int start = Math.max(0, count - 36);
        for (int i = start; i < count; i++) {
            this.playerInventorySlots.add(this.menu.slots.get(i));
        }
    }

    private boolean isPlayerInventoryRegion(int x, int y, int width, int height) {
        if (width != 16 || height != 16) {
            return false;
        }
        for (Slot slot : this.playerInventorySlots) {
            if (slot.x == x && slot.y == y) {
                return true;
            }
        }
        return false;
    }

    private Hit hitAt(int mouseX, int mouseY) {
        int x = mouseX - this.leftPos - 25;
        int y = mouseY - this.topPos - 25 + this.scrollOffset;
        if (x < 0 || x >= 210 || y < 0 || y >= scheduleContentHeight() + 28) {
            return Hit.NONE;
        }

        List<AirshipScheduleEntry> entries = currentSchedule().entries();
        for (int i = 0; i < entries.size(); i++) {
            AirshipScheduleEntry entry = entries.get(i);
            int cardHeight = cardHeight(entry);
            if (y >= cardHeight + 5) {
                y -= cardHeight + 10;
                if (y < 0) {
                    return Hit.NONE;
                }
                continue;
            }

            if (x > 25 && x <= 155 && y > 4 && y <= 20) {
                return new Hit(HitType.STATION, i);
            }
            if (x > 180 && x <= 193) {
                if (y > 0 && y <= 15) {
                    return new Hit(HitType.REMOVE, i);
                }
                if (y > cardHeight - 15) {
                    return new Hit(HitType.DUPLICATE, i);
                }
            }
            if (x > 194) {
                if (y > 7 && y <= 20 && i > 0) {
                    return new Hit(HitType.MOVE_UP, i);
                }
                if (y > 20 && y <= 33 && i < entries.size() - 1) {
                    return new Hit(HitType.MOVE_DOWN, i);
                }
            }
            if (isConditionAreaScrollable(entry)) {
                int center = (cardHeight - 8 + CARD_HEADER) / 2;
                if (x >= 12 && x <= 26 && y >= CARD_HEADER && y <= cardHeight - 4) {
                    return conditionScrollColumns.getOrDefault(i, 0) > 0
                            ? new Hit(HitType.CONDITION_SCROLL_LEFT, i)
                            : Hit.NONE;
                }
                if (x >= 168 && x <= 184 && y >= CARD_HEADER && y <= cardHeight - 4) {
                    return conditionScrollColumns.getOrDefault(i, 0) < Math.max(0, entry.conditionGroups().size() - 1)
                            ? new Hit(HitType.CONDITION_SCROLL_RIGHT, i)
                            : Hit.NONE;
                }
                if (x >= 15 && x < 19 && y >= center && y < center + 8 && conditionScrollColumns.getOrDefault(i, 0) > 0) {
                    return new Hit(HitType.CONDITION_SCROLL_LEFT, i);
                }
                if (x >= 178 && x < 182 && y >= center && y < center + 8
                        && conditionScrollColumns.getOrDefault(i, 0) < Math.max(0, entry.conditionGroups().size() - 1)) {
                    return new Hit(HitType.CONDITION_SCROLL_RIGHT, i);
                }
            }
            int conditionX = x - 26;
            int conditionY = y - 29;
            conditionX += conditionScrollPixels(entry, conditionScrollColumns.getOrDefault(i, 0));
            int groupX = 0;
            for (int groupIndex = 0; groupIndex < entry.conditionGroups().size(); groupIndex++) {
                List<AirshipScheduleCondition> group = entry.conditionGroups().get(groupIndex);
                int groupWidth = conditionColumnWidth(group, entry.waitUnit());
                if (conditionX >= groupX && conditionX < groupX + groupWidth) {
                    int row = conditionY / 18;
                    int rowOffset = conditionY % 18;
                    if (row >= 0 && row < group.size() && rowOffset <= 16) {
                        return new Hit(HitType.CONDITION, i, groupIndex, row);
                    }
                    int addY = group.size() * 18;
                    if (conditionY > addY && conditionY <= addY + 10 && conditionX >= groupX + groupWidth / 2 - 5 && conditionX < groupX + groupWidth / 2 + 5) {
                        return new Hit(HitType.ADD_CONDITION, i, groupIndex, -1);
                    }
                    return Hit.NONE;
                }
                groupX += groupWidth + 10;
            }
            if (conditionX >= groupX - 3 && conditionX <= groupX + 13 && conditionY >= 0 && conditionY <= 20) {
                return new Hit(HitType.ADD_ALTERNATIVE, i, entry.conditionGroups().size(), -1);
            }
            return Hit.NONE;
        }

        if (x >= 18 && x <= 50 && y >= 0 && y <= 20) {
            return new Hit(HitType.ADD_ENTRY, -1);
        }
        return Hit.NONE;
    }

    private void openStationEditor(int index) {
        this.editorMode = EditorMode.STATION;
        this.editorEntryIndex = index;
        this.editorStationBox = null;
        this.editorDurationInput = null;
        this.editorUnitInput = null;
        this.titleBox.visible = false;
        this.titleBox.active = false;
        this.stationFilterBox.visible = false;
        this.stationFilterBox.active = false;
        this.stationFilterBox.setValue("");
        this.stationFilterBox.setFocused(false);
        this.stationFilterBox.setSuggestion("");
        buildStationEditorWidgets();
        setFocused(null);
    }

    private void openConditionEditor(int index, int conditionGroup, int conditionIndex) {
        this.editorMode = EditorMode.CONDITION;
        this.editorEntryIndex = index;
        this.editorConditionGroup = conditionGroup;
        this.editorConditionIndex = conditionIndex;
        this.editorStationBox = null;
        this.editorDurationInput = null;
        this.editorUnitInput = null;
        this.titleBox.visible = false;
        this.titleBox.active = false;
        this.stationFilterBox.visible = false;
        this.stationFilterBox.active = false;
        this.stationFilterBox.setFocused(false);
        this.stationFilterBox.setSuggestion("");
        this.stationSuggestions = null;
        buildConditionEditorWidgets();
        setFocused(null);
    }

    private void openConditionEditorForLast(int entryIndex, int conditionGroup) {
        if (entryIndex < 0 || entryIndex >= currentSchedule().entries().size()) {
            return;
        }
        AirshipScheduleEntry entry = currentSchedule().entries().get(entryIndex);
        if (conditionGroup < 0 || conditionGroup >= entry.conditionGroups().size()) {
            return;
        }
        List<AirshipScheduleCondition> group = entry.conditionGroups().get(conditionGroup);
        if (group.isEmpty()) {
            return;
        }
        openConditionEditor(entryIndex, conditionGroup, group.size() - 1);
    }

    private void buildStationEditorWidgets() {
        this.editorSubWidgets.reset();
        this.editorData.remove("Text");
        this.editorData.remove("Duration");
        this.editorData.remove("Unit");
        this.editorData.putString("Text", currentEntry().map(AirshipScheduleEntry::targetStationName).orElse(""));

        ModularGuiLineBuilder builder = this.editorSubWidgets.newLineBuilder(this.font, this.leftPos + 77, this.topPos + 92)
                .speechBubble();
        builder.addTextInput(0, 121, (box, tooltip) -> {
            this.editorStationBox = box;
            box.setHint(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_search_hint"));
            box.setMaxLength(64);
            box.setResponder(this::onStationEditorTextChanged);
            tooltip.withTooltip(List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_name"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_name_wildcard"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_name_nearest")
            ));
        }, "Text");
        this.editorSubWidgets.load(this.editorData);
        this.stationSuggestions = createStationSuggestions(this.editorStationBox);
        onStationEditorTextChanged(this.editorStationBox == null ? "" : this.editorStationBox.getValue());
    }

    private void buildConditionEditorWidgets() {
        this.editorSubWidgets.reset();
        this.editorData.remove("Text");
        this.editorData.remove("Duration");
        this.editorData.remove("Unit");
        this.editorData.remove("Operator");
        this.editorData.remove("Measure");
        AirshipScheduleCondition condition = currentCondition().orElse(AirshipScheduleCondition.scheduledDelay(WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)));
        WaitDurationUnit waitUnit = currentEntry().map(AirshipScheduleEntry::waitUnit).orElse(WaitDurationUnit.SECONDS);
        boolean itemThreshold = condition.waitCondition().type() == WaitConditionType.UNTIL_ITEM_THRESHOLD;
        boolean fluidThreshold = condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_THRESHOLD;
        if (itemThreshold || fluidThreshold) {
            this.editorData.putString("Duration", Integer.toString(waitAmount(condition.waitCondition(), waitUnit)));
        } else {
            this.editorData.putInt("Duration", waitAmount(condition.waitCondition(), waitUnit));
        }
        this.editorData.putInt("Unit", waitUnit.ordinal());
        this.editorData.putInt("Operator", condition.waitCondition().cargoOperator());
        this.editorData.putInt("Measure", condition.waitCondition().cargoMeasure());

        ModularGuiLineBuilder builder = this.editorSubWidgets.newLineBuilder(this.font, this.leftPos + 77, this.topPos + 92)
                .speechBubble();
        if (itemThreshold || fluidThreshold) {
            builder.addSelectionScrollInput(0, 24, (input, label) -> {
                input.forOptions(Ops.translatedOptions())
                        .titled(Component.translatable("create.schedule.condition.threshold.train_holds", ""));
                input.format(state -> Component.literal(" " + Ops.values()[Mth.clamp(state, 0, Ops.values().length - 1)].formatted));
                label.withShadow();
            }, "Operator");
            builder.addIntegerTextInput(29, 41, (box, tooltip) -> {
            }, "Duration");
            builder.addSelectionScrollInput(71, 50, (input, label) -> {
                List<Component> options = itemThreshold
                        ? List.of(
                                Component.translatable("create.schedule.condition.threshold.items"),
                                Component.translatable("create.schedule.condition.threshold.stacks")
                        )
                        : List.of(Component.translatable("create.schedule.condition.threshold.buckets"));
                this.editorUnitInput = (SelectionScrollInput) input.forOptions(options);
                input.titled(itemThreshold
                        ? Component.translatable("create.schedule.condition.threshold.item_measure")
                        : null);
                label.withShadow();
            }, "Measure");
        } else {
            builder.addScrollInput(0, 31, (input, label) -> {
                this.editorDurationInput = input.withRange(0, 10001)
                        .titled(Component.translatable("create.generic.duration"))
                        .withShiftStep(15)
                        .calling(state -> {
                        });
                input.lockedTooltipX = -15;
                input.lockedTooltipY = 35;
                label.withShadow();
            }, "Duration");
            builder.addSelectionScrollInput(36, 85, (input, label) -> {
                this.editorUnitInput = (SelectionScrollInput) input.forOptions(TimeUnit.translatedOptions());
                input.titled(Component.translatable("create.generic.timeUnit"));
                label.withShadow();
            }, "Unit");
        }
        this.editorSubWidgets.load(this.editorData);
    }

    private void onStationEditorTextChanged(String value) {
        if (this.stationSuggestions != null) {
            this.stationSuggestions.updateCommandInfo();
        }
        if (this.editorStationBox == null) {
            return;
        }
        if (!this.editorStationBox.isFocused() || value == null || value.isBlank()) {
            this.editorStationBox.setSuggestion("");
            return;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        String suggestion = AirshipStationRegistry.knownStations(this.minecraft.level.dimension()).stream()
                .map(AirshipStationSnapshot::stationName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .filter(name -> name.length() > value.length())
                .findFirst()
                .map(name -> name.substring(value.length()))
                .orElse("");
        this.editorStationBox.setSuggestion(suggestion);
    }

    private void closeEditor() {
        this.editorMode = EditorMode.NONE;
        this.routeChoices.clear();
        this.routeChoiceSelected = 0;
        this.routeChoiceScroll = 0;
        closeNoRoutePopup();
        this.editorSubWidgets.reset();
        this.editorStationBox = null;
        this.editorDurationInput = null;
        this.editorUnitInput = null;
        this.titleBox.visible = false;
        this.titleBox.active = false;
        this.stationFilterBox.visible = false;
        this.stationFilterBox.active = false;
        this.stationFilterBox.setFocused(false);
        this.stationFilterBox.setSuggestion("");
        this.stationSuggestions = null;
        setFocused(null);
    }

    private void confirmEditor() {
        if (this.editorMode == EditorMode.STATION) {
            saveEditorData();
            List<AirshipStationSnapshot> matches = filteredStations(this.editorData.getString("Text"));
            if (!matches.isEmpty()) {
                selectStation(matches.getFirst());
                return;
            }
        }
        if (this.editorMode == EditorMode.CONDITION) {
            applyEditedConditionFromWidgets();
            syncSchedule();
            closeEditor();
            return;
        }
        if (this.editorMode == EditorMode.ROUTE) {
            pinSelectedRouteChoice();
            return;
        }
        closeEditor();
    }

    private void saveEditorData() {
        this.editorSubWidgets.save(this.editorData);
    }

    private void pressAction(int actionId) {
        applyLocalAction(actionId);
        if (actionId < AirshipScheduleMenu.ACTION_SELECT_ENTRY_BASE) {
            syncSchedule();
        }
    }

    private void saveTitle() {
        if (this.titleBox != null) {
            this.localSchedule = currentSchedule().withTitle(this.titleBox.getValue());
            syncSchedule();
        }
    }

    private void syncSchedule() {
        PacketDistributor.sendToServer(new UpdateAirshipSchedulePayload(AirshipScheduleNbtSerializer.write(currentSchedule())));
    }

    private void applyLocalAction(int actionId) {
        if (actionId >= AirshipScheduleMenu.ACTION_SELECT_ENTRY_BASE) {
            this.selectedIndex = Math.max(0, Math.min(actionId - AirshipScheduleMenu.ACTION_SELECT_ENTRY_BASE, Math.max(0, currentSchedule().entries().size() - 1)));
            return;
        }

        AirshipSchedule schedule = currentSchedule();
        clampSelectedIndex();
        switch (actionId) {
            case AirshipScheduleMenu.ACTION_ADD_TRAVEL -> addTravelLocally(schedule);
            case AirshipScheduleMenu.ACTION_REMOVE -> removeSelectedLocally(schedule);
            case AirshipScheduleMenu.ACTION_DUPLICATE -> duplicateSelectedLocally(schedule);
            case AirshipScheduleMenu.ACTION_MOVE_UP -> moveSelectedLocally(schedule, -1);
            case AirshipScheduleMenu.ACTION_MOVE_DOWN -> moveSelectedLocally(schedule, 1);
            case AirshipScheduleMenu.ACTION_WAIT_DOWN -> {
                if (this.editorMode == EditorMode.CONDITION) {
                    adjustEditedConditionWaitLocally(schedule, -1);
                } else {
                    adjustSelectedWaitLocally(schedule, -1);
                }
            }
            case AirshipScheduleMenu.ACTION_WAIT_UP -> {
                if (this.editorMode == EditorMode.CONDITION) {
                    adjustEditedConditionWaitLocally(schedule, 1);
                } else {
                    adjustSelectedWaitLocally(schedule, 1);
                }
            }
            case AirshipScheduleMenu.ACTION_TOGGLE_LOOP -> this.localSchedule = schedule.withLoop(!schedule.loop());
            case AirshipScheduleMenu.ACTION_SELECT_PREVIOUS -> selectLocally(-1);
            case AirshipScheduleMenu.ACTION_SELECT_NEXT -> selectLocally(1);
            case AirshipScheduleMenu.ACTION_CYCLE_TARGET_STATION -> cycleTargetStationLocally(schedule);
            case AirshipScheduleMenu.ACTION_TOGGLE_WAIT -> {
                if (this.editorMode == EditorMode.CONDITION) {
                    toggleEditedConditionWaitLocally(schedule);
                } else {
                    toggleSelectedWaitLocally(schedule);
                }
            }
            case AirshipScheduleMenu.ACTION_CYCLE_WAIT_UNIT -> cycleSelectedWaitUnitLocally(schedule);
            case AirshipScheduleMenu.ACTION_ADD_CONDITION -> addConditionLocally(schedule);
            case AirshipScheduleMenu.ACTION_ADD_ALTERNATIVE_CONDITION -> addAlternativeConditionLocally(schedule);
            default -> {
            }
        }
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
    }

    private void addTravelLocally(AirshipSchedule schedule) {
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        int insertIndex = entries.isEmpty() ? 0 : Math.min(this.selectedIndex + 1, entries.size());
        entries.add(insertIndex, AirshipScheduleEntry.blankTravel());
        this.selectedIndex = insertIndex;
        this.localSchedule = schedule.withEntries(entries);
    }

    private void removeSelectedLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.remove(this.selectedIndex);
        this.selectedIndex = Math.max(0, Math.min(this.selectedIndex, entries.size() - 1));
        this.localSchedule = schedule.withEntries(entries);
    }

    private void duplicateSelectedLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.add(this.selectedIndex + 1, entries.get(this.selectedIndex));
        this.selectedIndex++;
        this.localSchedule = schedule.withEntries(entries);
    }

    private void moveSelectedLocally(AirshipSchedule schedule, int direction) {
        if (schedule.entries().size() < 2) {
            return;
        }
        int targetIndex = this.selectedIndex + direction;
        if (targetIndex < 0 || targetIndex >= schedule.entries().size()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry selected = entries.remove(this.selectedIndex);
        entries.add(targetIndex, selected);
        this.selectedIndex = targetIndex;
        this.localSchedule = schedule.withEntries(entries);
    }

    private void adjustSelectedWaitLocally(AirshipSchedule schedule, int direction) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry entry = entries.get(this.selectedIndex);
        int currentTicks = entry.waitCondition().type() == WaitConditionType.TIMED ? entry.waitCondition().durationTicks() : 0;
        int nextTicks = Math.max(0, currentTicks + direction * entry.waitUnit().ticksPerStep() * 5);
        entries.set(this.selectedIndex, entry.withWaitCondition(nextTicks == 0 ? WaitCondition.none() : WaitCondition.timed(nextTicks)));
        this.localSchedule = schedule.withEntries(entries);
    }

    private void toggleSelectedWaitLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry entry = entries.get(this.selectedIndex);
        WaitCondition next = entry.waitCondition().type() == WaitConditionType.NONE
                ? WaitCondition.timed(Math.max(entry.waitUnit().ticksPerStep() * 5, WaitCondition.DEFAULT_TIMED_WAIT_TICKS))
                : WaitCondition.none();
        entries.set(this.selectedIndex, entry.withWaitCondition(next));
        this.localSchedule = schedule.withEntries(entries);
    }

    private void cycleSelectedWaitUnitLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry entry = entries.get(this.selectedIndex);
        entries.set(this.selectedIndex, entry.withWaitUnit(entry.waitUnit().next()));
        this.localSchedule = schedule.withEntries(entries);
    }

    private void addConditionLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        addConditionLocally(this.selectedIndex, 0);
    }

    private void addAlternativeConditionLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        addAlternativeConditionLocally(this.selectedIndex);
    }

    private void addConditionLocally(int entryIndex, int groupIndex) {
        updateConditionGroups(entryIndex, groups -> {
            if (groups.isEmpty()) {
                groups.add(new ArrayList<>());
            }
            int targetGroup = Math.max(0, Math.min(groupIndex, groups.size() - 1));
            WaitCondition wait = groups.get(targetGroup).isEmpty()
                    ? WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)
                    : groups.get(targetGroup).getLast().waitCondition();
            groups.get(targetGroup).add(AirshipScheduleCondition.scheduledDelay(wait));
        });
    }

    private void addAlternativeConditionLocally(int entryIndex) {
        updateConditionGroups(entryIndex, groups -> groups.add(new ArrayList<>(
                List.of(AirshipScheduleCondition.scheduledDelay(WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)))
        )));
    }

    private void removeConditionLocally(int entryIndex, int groupIndex, int conditionIndex) {
        updateConditionGroups(entryIndex, groups -> {
            if (groupIndex < 0 || groupIndex >= groups.size()) {
                return;
            }
            List<AirshipScheduleCondition> group = groups.get(groupIndex);
            if (conditionIndex < 0 || conditionIndex >= group.size()) {
                return;
            }
            if (groups.size() == 1 && group.size() == 1) {
                return;
            }
            group.remove(conditionIndex);
            if (group.isEmpty()) {
                groups.remove(groupIndex);
            }
        });
    }

    private void adjustEditedConditionWaitLocally(AirshipSchedule schedule, int direction) {
        AirshipScheduleEntry entry = currentEntry().orElse(null);
        AirshipScheduleCondition condition = currentCondition().orElse(null);
        if (entry == null || condition == null) {
            return;
        }
        int currentTicks = switch (condition.waitCondition().type()) {
            case TIMED -> condition.waitCondition().durationTicks();
            case UNTIL_DOCKED -> condition.waitCondition().runtimeTicks();
            case UNTIL_IDLE -> condition.waitCondition().idleTicks();
            case UNTIL_ITEM_THRESHOLD, UNTIL_FLUID_THRESHOLD -> condition.waitCondition().durationTicks();
            default -> 0;
        };
        int step = switch (condition.waitCondition().type()) {
            case UNTIL_ITEM_THRESHOLD -> condition.waitCondition().cargoMeasure() == 1 ? 1 : 16;
            case UNTIL_FLUID_THRESHOLD -> 1;
            default -> entry.waitUnit().ticksPerStep() * 5;
        };
        int nextTicks = Math.max(0, currentTicks + direction * step);
        setEditedConditionWait(waitConditionWithTicks(condition.waitCondition().type(), nextTicks));
    }

    private void toggleEditedConditionWaitLocally(AirshipSchedule schedule) {
        AirshipScheduleEntry entry = currentEntry().orElse(null);
        AirshipScheduleCondition condition = currentCondition().orElse(null);
        if (entry == null || condition == null) {
            return;
        }
        WaitCondition next = condition.waitCondition().type() == WaitConditionType.NONE
                ? WaitCondition.timed(Math.max(entry.waitUnit().ticksPerStep() * 5, WaitCondition.DEFAULT_TIMED_WAIT_TICKS))
                : WaitCondition.none();
        setEditedConditionWait(next);
    }

    private void cycleEditedConditionType() {
        cycleEditedConditionType(1);
    }

    private void cycleEditedConditionType(int direction) {
        AirshipScheduleCondition condition = currentCondition().orElse(null);
        if (condition == null) {
            return;
        }
        List<WaitConditionType> types = conditionSelectorTypes();
        int current = Math.max(0, types.indexOf(condition.waitCondition().type()));
        int next = Math.floorMod(current + Integer.signum(direction), types.size());
        WaitConditionType nextType = types.get(next);
        setEditedConditionWait(defaultWaitConditionForType(nextType));
        buildConditionEditorWidgets();
    }

    private List<WaitConditionType> conditionSelectorTypes() {
        return List.of(
                WaitConditionType.TIMED,
                WaitConditionType.UNTIL_DOCKED,
                WaitConditionType.UNTIL_IDLE
        );
    }

    private WaitCondition defaultWaitConditionForType(WaitConditionType type) {
        return switch (type) {
            case UNTIL_DOCKED -> WaitCondition.untilDocked(WaitCondition.DEFAULT_TIMED_WAIT_TICKS);
            case UNTIL_IDLE -> WaitCondition.untilIdle(WaitCondition.DEFAULT_TIMED_WAIT_TICKS, 0);
            case UNTIL_FLUID_THRESHOLD -> WaitCondition.fluidThreshold(10, 0);
            case UNTIL_ITEM_THRESHOLD -> WaitCondition.itemThreshold(10, 0);
            default -> WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS);
        };
    }

    private void setEditedConditionWait(WaitCondition waitCondition) {
        updateConditionGroups(this.editorEntryIndex, groups -> {
            if (this.editorConditionGroup < 0 || this.editorConditionGroup >= groups.size()) {
                return;
            }
            List<AirshipScheduleCondition> group = groups.get(this.editorConditionGroup);
            if (this.editorConditionIndex < 0 || this.editorConditionIndex >= group.size()) {
                return;
            }
            group.set(this.editorConditionIndex, AirshipScheduleCondition.fromWaitCondition(waitCondition));
        });
    }

    private boolean isCargoWait(WaitCondition waitCondition) {
        return waitCondition.type() == WaitConditionType.UNTIL_ITEM_THRESHOLD
                || waitCondition.type() == WaitConditionType.UNTIL_FLUID_THRESHOLD;
    }

    private void setEditedConditionFilter(ItemStack stack) {
        AirshipScheduleCondition condition = currentCondition().orElse(null);
        if (condition == null || !isCargoWait(condition.waitCondition())) {
            return;
        }
        ItemStack filter = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        WaitCondition current = condition.waitCondition();
        WaitCondition next = current.type() == WaitConditionType.UNTIL_ITEM_THRESHOLD
                ? WaitCondition.itemThreshold(current.durationTicks(), current.maxTicks(), current.cargoOperator(), current.cargoMeasure(), filter)
                : WaitCondition.fluidThreshold(current.durationTicks(), current.maxTicks(), current.cargoOperator(), current.cargoMeasure(), filter);
        setEditedConditionWait(next);
    }

    private void applyEditedConditionFromWidgets() {
        saveEditorData();
        WaitDurationUnit unit = WaitDurationUnit.values()[Mth.clamp(this.editorData.getInt("Unit"), 0, WaitDurationUnit.values().length - 1)];
        WaitConditionType type = currentCondition()
                .map(AirshipScheduleCondition::waitCondition)
                .map(WaitCondition::type)
                .orElse(WaitConditionType.TIMED);
        int amount = isCargoWaitType(type)
                ? parsePositiveInt(this.editorData.getString("Duration"), 10)
                : Math.max(0, this.editorData.getInt("Duration"));
        int operator = Mth.clamp(this.editorData.getInt("Operator"), 0, Ops.values().length - 1);
        int measure = Math.max(0, this.editorData.getInt("Measure"));
        ItemStack filter = currentCondition()
                .map(AirshipScheduleCondition::waitCondition)
                .map(WaitCondition::cargoFilter)
                .orElse(ItemStack.EMPTY);
        int ticks = switch (type) {
            case UNTIL_ITEM_THRESHOLD, UNTIL_FLUID_THRESHOLD -> amount;
            default -> switch (unit) {
                case TICKS -> amount;
                case SECONDS -> amount * 20;
                case MINUTES -> amount * 20 * 60;
            };
        };

        AirshipSchedule schedule = currentSchedule();
        if (this.editorEntryIndex < 0 || this.editorEntryIndex >= schedule.entries().size()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry entry = entries.get(this.editorEntryIndex).withWaitUnit(unit);
        entries.set(this.editorEntryIndex, entry);
        this.localSchedule = schedule.withEntries(entries);
        setEditedConditionWait(waitConditionWithTicks(type, ticks, operator, measure, filter));
    }

    private boolean isCargoWaitType(WaitConditionType type) {
        return type == WaitConditionType.UNTIL_ITEM_THRESHOLD || type == WaitConditionType.UNTIL_FLUID_THRESHOLD;
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private WaitCondition waitConditionWithTicks(WaitConditionType type, int ticks) {
        AirshipScheduleCondition condition = currentCondition().orElse(null);
        WaitCondition current = condition == null ? WaitCondition.none() : condition.waitCondition();
        return waitConditionWithTicks(type, ticks, current.cargoOperator(), current.cargoMeasure(), current.cargoFilter());
    }

    private WaitCondition waitConditionWithTicks(WaitConditionType type, int ticks, int operator, int measure, ItemStack filter) {
        return switch (type) {
            case UNTIL_DOCKED -> WaitCondition.untilDocked(ticks);
            case UNTIL_IDLE -> WaitCondition.untilIdle(ticks, 0);
            case UNTIL_ITEM_THRESHOLD -> WaitCondition.itemThreshold(ticks, 0, operator, measure, filter);
            case UNTIL_FLUID_THRESHOLD -> WaitCondition.fluidThreshold(ticks, 0, operator, measure, filter);
            default -> ticks == 0 ? WaitCondition.none() : WaitCondition.timed(ticks);
        };
    }

    private void updateConditionGroups(int entryIndex, java.util.function.Consumer<List<List<AirshipScheduleCondition>>> updater) {
        AirshipSchedule schedule = currentSchedule();
        if (entryIndex < 0 || entryIndex >= schedule.entries().size()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry entry = entries.get(entryIndex);
        List<List<AirshipScheduleCondition>> groups = mutableConditionGroups(entry.conditionGroups());
        updater.accept(groups);
        if (groups.isEmpty()) {
            groups.add(new ArrayList<>(List.of(AirshipScheduleCondition.scheduledDelay(WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)))));
        }
        entries.set(entryIndex, entryWithConditionGroups(entry, groups));
        this.localSchedule = schedule.withEntries(entries);
    }

    private List<List<AirshipScheduleCondition>> mutableConditionGroups(List<List<AirshipScheduleCondition>> source) {
        List<List<AirshipScheduleCondition>> groups = new ArrayList<>();
        for (List<AirshipScheduleCondition> group : source) {
            groups.add(new ArrayList<>(group));
        }
        return groups;
    }

    private AirshipScheduleEntry entryWithConditionGroups(AirshipScheduleEntry entry, List<List<AirshipScheduleCondition>> groups) {
        WaitCondition primaryWait = groups.stream()
                .filter(group -> !group.isEmpty())
                .findFirst()
                .map(group -> group.getFirst().waitCondition())
                .orElse(entry.waitCondition());
        return new AirshipScheduleEntry(
                entry.type(),
                entry.targetStationId(),
                entry.targetStationName(),
                primaryWait,
                entry.waitUnit(),
                entry.pinnedSegmentId(),
                groups
        );
    }

    private void selectLocally(int direction) {
        if (currentSchedule().entries().isEmpty()) {
            this.selectedIndex = 0;
            return;
        }
        this.selectedIndex = Math.floorMod(this.selectedIndex + direction, currentSchedule().entries().size());
    }

    private void cycleTargetStationLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty() || this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        List<AirshipStationSnapshot> stations = AirshipStationRegistry.knownStations(this.minecraft.level.dimension());
        if (stations.isEmpty()) {
            return;
        }
        AirshipScheduleEntry entry = schedule.entries().get(this.selectedIndex);
        Optional<UUID> current = entry.targetStationId();
        int currentIndex = -1;
        if (current.isPresent()) {
            for (int i = 0; i < stations.size(); i++) {
                if (stations.get(i).stationId().equals(current.get())) {
                    currentIndex = i;
                    break;
                }
            }
        }
        AirshipStationSnapshot selectedStation = stations.get((currentIndex + 1) % stations.size());
        setEntryStationLocally(this.selectedIndex, selectedStation);
    }

    private void selectFilteredStationLocally(int entryIndex, String filter) {
        if (this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<AirshipStationSnapshot> stations = AirshipStationRegistry.knownStations(this.minecraft.level.dimension()).stream()
                .filter(station -> normalizedFilter.isBlank() || station.stationName().toLowerCase(Locale.ROOT).contains(normalizedFilter))
                .toList();
        if (!stations.isEmpty()) {
            setEntryStationLocally(entryIndex, stations.getFirst());
        }
    }

    private List<AirshipStationSnapshot> filteredStations() {
        return filteredStations(this.stationFilterBox == null ? "" : this.stationFilterBox.getValue());
    }

    private List<AirshipStationSnapshot> filteredStations(String rawFilter) {
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        String filter = rawFilter == null ? "" : rawFilter.trim().toLowerCase(Locale.ROOT);
        return AirshipStationRegistry.knownStations(this.minecraft.level.dimension()).stream()
                .filter(station -> filter.isBlank() || station.stationName().toLowerCase(Locale.ROOT).startsWith(filter))
                .toList();
    }

    private void selectStation(AirshipStationSnapshot station) {
        Optional<UUID> startStationId = previousStationId(this.editorEntryIndex);
        List<RouteSegment> candidates = routeCandidatesFor(this.editorEntryIndex, station.stationId());
        if (candidates.isEmpty()) {
            openNoRoutePopup(startStationId, station);
            return;
        }
        setEntryStationLocally(this.editorEntryIndex, station);
        openRouteChoiceEditor(candidates);
        syncSchedule();
    }

    private void openNoRoutePopup(Optional<UUID> startStationId, AirshipStationSnapshot targetStation) {
        this.noRoutePopupOpen = true;
        this.noRoutePopupLines.clear();
        String target = targetStation.stationName();
        if (startStationId.isPresent()) {
            String start = resolveStationName(startStationId.get(), previousStationNameFallback(this.editorEntryIndex));
            this.noRoutePopupLines.add(Component.literal("No recorded route from"));
            this.noRoutePopupLines.add(Component.literal(start + " -> " + target + "."));
            this.noRoutePopupLines.add(Component.literal("Record that route first."));
        } else {
            this.noRoutePopupLines.add(Component.literal("No recorded route ends at"));
            this.noRoutePopupLines.add(Component.literal(target + "."));
            this.noRoutePopupLines.add(Component.literal("Record an inbound route first."));
        }
    }

    private void closeNoRoutePopup() {
        this.noRoutePopupOpen = false;
        this.noRoutePopupLines.clear();
    }

    private StationSuggestions createStationSuggestions(EditBox targetBox) {
        if (this.minecraft == null || targetBox == null) {
            return null;
        }
        StationSuggestions suggestions = new StationSuggestions(targetBox, viableStations(), this.topPos + 33);
        suggestions.updateCommandInfo();
        return suggestions;
    }

    private List<IntAttached<String>> viableStations() {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
            return List.of();
        }
        Vec3 playerPosition = this.minecraft.player.position();
        Set<String> seen = new HashSet<>();
        return AirshipStationRegistry.knownStations(this.minecraft.level.dimension()).stream()
                .filter(station -> seen.add(station.stationName()))
                .map(station -> IntAttached.with(
                        (int) Vec3.atCenterOf(station.stationPos()).distanceTo(playerPosition),
                        station.stationName()))
                .toList();
    }

    private void setEntryStationLocally(int entryIndex, AirshipStationSnapshot selectedStation) {
        AirshipSchedule schedule = currentSchedule();
        if (entryIndex < 0 || entryIndex >= schedule.entries().size()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.set(entryIndex, entries.get(entryIndex).withTargetStation(selectedStation.stationId(), selectedStation.stationName()));
        this.localSchedule = schedule.withEntries(entries);
    }

    private void setEntryPinnedSegmentLocally(int entryIndex, Optional<RouteSegmentId> segmentId) {
        AirshipSchedule schedule = currentSchedule();
        if (entryIndex < 0 || entryIndex >= schedule.entries().size()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.set(entryIndex, entries.get(entryIndex).withPinnedSegment(segmentId));
        this.localSchedule = schedule.withEntries(entries);
    }

    private void openRouteChoiceEditor(List<RouteSegment> candidates) {
        this.editorMode = EditorMode.ROUTE;
        this.editorSubWidgets.reset();
        this.stationFilterBox.visible = false;
        this.stationFilterBox.active = false;
        this.stationFilterBox.setFocused(false);
        this.stationFilterBox.setSuggestion("");
        this.editorStationBox = null;
        this.editorDurationInput = null;
        this.editorUnitInput = null;
        this.stationSuggestions = null;
        this.routeChoices.clear();
        this.routeChoices.addAll(candidates);
        this.routeChoiceSelected = 0;
        this.routeChoiceScroll = 0;
        setFocused(null);
    }

    private void pinSelectedRouteChoice() {
        if (this.routeChoices.isEmpty()) {
            closeEditor();
            return;
        }
        int selected = Mth.clamp(this.routeChoiceSelected, 0, this.routeChoices.size() - 1);
        setEntryPinnedSegmentLocally(this.editorEntryIndex, Optional.of(this.routeChoices.get(selected).id()));
        syncSchedule();
        closeEditor();
    }

    private List<RouteSegment> routeCandidatesFor(int entryIndex, UUID endStationId) {
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        Optional<UUID> startStationId = previousStationId(entryIndex);
        if (startStationId.isPresent()) {
            return RouteSegmentRegistry.matching(
                    startStationId.get(),
                    endStationId,
                    this.minecraft.level.dimension(),
                    Optional.empty()
            );
        }
        return RouteSegmentRegistry.endingAt(
                endStationId,
                this.minecraft.level.dimension(),
                Optional.empty()
        );
    }

    private Optional<UUID> previousStationId(int entryIndex) {
        if (entryIndex <= 0) {
            return Optional.empty();
        }
        List<AirshipScheduleEntry> entries = currentSchedule().entries();
        for (int i = entryIndex - 1; i >= 0; i--) {
            Optional<UUID> stationId = entries.get(i).targetStationId();
            if (stationId.isPresent()) {
                return stationId;
            }
        }
        return Optional.empty();
    }

    private String previousStationNameFallback(int entryIndex) {
        if (entryIndex <= 0) {
            return "Unknown";
        }
        List<AirshipScheduleEntry> entries = currentSchedule().entries();
        for (int i = entryIndex - 1; i >= 0; i--) {
            AirshipScheduleEntry entry = entries.get(i);
            if (entry.targetStationId().isPresent()) {
                return entry.displayStationName();
            }
        }
        return "Unknown";
    }

    private Component routeChoiceLine(RouteSegment segment) {
        return Component.literal(resolveStationName(segment.startStationId(), segment.startStationName()))
                .append(" -> ")
                .append(resolveStationName(segment.endStationId(), segment.endStationName()))
                .append(" / ")
                .append(segment.shipName());
    }

    private String routeDisplayName(RouteSegment segment) {
        return resolveStationName(segment.startStationId(), segment.startStationName())
                + " -> "
                + resolveStationName(segment.endStationId(), segment.endStationName());
    }

    private String resolveStationName(UUID stationId, String fallbackName) {
        return AirshipStationRegistry.snapshot(stationId)
                .map(AirshipStationSnapshot::stationName)
                .filter(name -> !name.isBlank())
                .orElse(fallbackName);
    }

    private int routeChoiceAt(int mx, int my) {
        int x = mx - 58;
        int y = my - 88;
        if (x < 0 || x >= 140 || y < 0 || y >= 36) {
            return -1;
        }
        return this.routeChoiceScroll + y / 18;
    }

    private void clampSelectedIndex() {
        if (currentSchedule().entries().isEmpty()) {
            this.selectedIndex = 0;
            return;
        }
        this.selectedIndex = Math.max(0, Math.min(this.selectedIndex, currentSchedule().entries().size() - 1));
    }

    private AirshipSchedule currentSchedule() {
        return this.localSchedule == null ? AirshipSchedule.empty() : this.localSchedule;
    }

    private java.util.Optional<AirshipScheduleEntry> currentEntry() {
        List<AirshipScheduleEntry> entries = currentSchedule().entries();
        if (this.editorEntryIndex < 0 || this.editorEntryIndex >= entries.size()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(entries.get(this.editorEntryIndex));
    }

    private java.util.Optional<AirshipScheduleCondition> currentCondition() {
        return currentEntry().flatMap(entry -> {
            if (this.editorConditionGroup < 0 || this.editorConditionGroup >= entry.conditionGroups().size()) {
                return java.util.Optional.empty();
            }
            List<AirshipScheduleCondition> group = entry.conditionGroups().get(this.editorConditionGroup);
            if (this.editorConditionIndex < 0 || this.editorConditionIndex >= group.size()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(group.get(this.editorConditionIndex));
        });
    }

    private int selectedIndex() {
        clampSelectedIndex();
        return this.selectedIndex;
    }

    private int cardHeight(AirshipScheduleEntry entry) {
        int maxRows = 1;
        for (List<AirshipScheduleCondition> group : entry.conditionGroups()) {
            maxRows = Math.max(maxRows, group.size());
        }
        return CARD_HEADER + 24 + maxRows * 18;
    }

    private int scheduleContentHeight() {
        int height = 25;
        List<AirshipScheduleEntry> entries = currentSchedule().entries();
        for (int i = 0; i < entries.size(); i++) {
            height += cardHeight(entries.get(i));
            if (i + 1 < entries.size()) {
                height += 10;
            }
        }
        if (!entries.isEmpty()) {
            height += 9;
        }
        height += 20;
        return height;
    }

    private int maxScroll() {
        return Math.max(0, scheduleContentHeight() - 173);
    }

    private Component waitText(AirshipScheduleEntry entry) {
        if (entry.waitCondition().type() == WaitConditionType.NONE) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.wait.none");
        }
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.wait.short",
                waitAmount(entry),
                unitShortText(entry.waitUnit())
        );
    }

    private Component conditionWaitText(AirshipScheduleCondition condition) {
        WaitDurationUnit unit = currentEntry().map(AirshipScheduleEntry::waitUnit).orElse(WaitDurationUnit.SECONDS);
        return conditionWaitText(condition, unit);
    }

    private Component conditionWaitText(AirshipScheduleCondition condition, WaitDurationUnit unit) {
        if (condition.waitCondition().type() == WaitConditionType.NONE) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.wait.none");
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_DOCKED) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_schedule.docked_time.short",
                    formatCreateTime(condition.waitCondition(), unit, true)
            );
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_IDLE) {
            return Component.translatable("create.schedule.condition.idle_short", formatCreateTime(condition.waitCondition(), unit, true));
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_ITEM_THRESHOLD) {
            return cargoSummaryText(condition);
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            return cargoSummaryText(condition);
        }
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.wait.short",
                waitAmount(condition.waitCondition(), unit),
                unitShortText(unit)
        );
    }

    private Component longConditionTimeText(AirshipScheduleCondition condition, WaitDurationUnit unit) {
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_DOCKED) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_schedule.docked_time.long",
                    formatCreateTime(condition.waitCondition(), unit, false)
            );
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_IDLE) {
            return Component.translatable("create.schedule.condition.for_x_time", formatCreateTime(condition.waitCondition(), unit, false));
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_ITEM_THRESHOLD) {
            return cargoLongText(condition);
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            return cargoLongText(condition);
        }
        return Component.literal(waitAmount(condition.waitCondition(), unit) + " ").append(unitText(unit));
    }

    private Component conditionValueText(AirshipScheduleCondition condition) {
        WaitDurationUnit unit = currentEntry().map(AirshipScheduleEntry::waitUnit).orElse(WaitDurationUnit.SECONDS);
        return Component.literal(Integer.toString(waitAmount(condition.waitCondition(), unit)));
    }

    private Component conditionActionText(AirshipScheduleCondition condition) {
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_DOCKED) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.until_docked");
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_IDLE) {
            return Component.translatable("create.schedule.condition.idle");
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_ITEM_THRESHOLD) {
            return Component.translatable("create.schedule.condition.item_threshold");
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            return Component.translatable("create.schedule.condition.fluid_threshold");
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.scheduled_delay");
    }

    private net.minecraft.world.item.ItemStack conditionIcon(AirshipScheduleCondition condition) {
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_DOCKED) {
            return ModItems.SHIP_TRANSPONDER.get().getDefaultInstance();
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_IDLE) {
            return Items.HOPPER.getDefaultInstance();
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_ITEM_THRESHOLD) {
            return condition.waitCondition().cargoFilter().isEmpty()
                    ? ItemStack.EMPTY
                    : condition.waitCondition().cargoFilter();
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            return condition.waitCondition().cargoFilter().isEmpty()
                    ? ItemStack.EMPTY
                    : condition.waitCondition().cargoFilter();
        }
        return Items.REPEATER.getDefaultInstance();
    }

    private Component cargoSummaryText(AirshipScheduleCondition condition) {
        WaitCondition wait = condition.waitCondition();
        Ops[] ops = Ops.values();
        Ops operator = ops[Mth.clamp(wait.cargoOperator(), 0, ops.length - 1)];
        return Component.literal(operator.formatted + " " + wait.durationTicks()).append(cargoUnitText(wait));
    }

    private Component cargoLongText(AirshipScheduleCondition condition) {
        WaitCondition wait = condition.waitCondition();
        Ops[] ops = Ops.values();
        Ops operator = ops[Mth.clamp(wait.cargoOperator(), 0, ops.length - 1)];
        String operatorId = operator.name().toLowerCase(Locale.ROOT);
        return Component.translatable(
                        "create.schedule.condition.threshold.train_holds",
                        Component.translatable("create.schedule.condition.threshold." + operatorId)
                )
                .append(" ")
                .append(Component.translatable(
                        "create.schedule.condition.threshold.x_units_of_item",
                        wait.durationTicks(),
                        cargoMeasureText(wait),
                        cargoFilterText(wait)
                ).withStyle(ChatFormatting.DARK_AQUA));
    }

    private Component cargoUnitText(WaitCondition wait) {
        if (wait.type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            return Component.literal("b");
        }
        return Component.literal(wait.cargoMeasure() == 1 ? "\u25A4" : "");
    }

    private Component cargoMeasureText(WaitCondition wait) {
        if (wait.type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            return Component.translatable("create.schedule.condition.threshold.buckets");
        }
        return Component.translatable("create.schedule.condition.threshold." + (wait.cargoMeasure() == 1 ? "stacks" : "items"));
    }

    private Component cargoFilterText(WaitCondition wait) {
        if (wait.cargoFilter().isEmpty()) {
            return Component.translatable("create.schedule.condition.threshold.anything");
        }
        return wait.cargoFilter().getHoverName();
    }

    private Component optionLine(WaitDurationUnit unit) {
        WaitDurationUnit current = currentEntry().map(AirshipScheduleEntry::waitUnit).orElse(WaitDurationUnit.SECONDS);
        return Component.literal(current == unit ? "-> " : "> ")
                .append(unitText(unit))
                .withStyle(current == unit ? ChatFormatting.WHITE : ChatFormatting.GRAY);
    }

    private int waitAmount(AirshipScheduleEntry entry) {
        return waitAmount(entry.waitCondition(), entry.waitUnit());
    }

    private int waitAmount(WaitCondition waitCondition, WaitDurationUnit waitUnit) {
        int ticks = switch (waitCondition.type()) {
            case UNTIL_DOCKED -> waitCondition.runtimeTicks();
            case UNTIL_IDLE -> waitCondition.idleTicks();
            case UNTIL_ITEM_THRESHOLD, UNTIL_FLUID_THRESHOLD -> waitCondition.durationTicks();
            default -> waitCondition.durationTicks();
        };
        if (waitCondition.type() == WaitConditionType.UNTIL_ITEM_THRESHOLD
                || waitCondition.type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            return ticks;
        }
        return switch (waitUnit) {
            case TICKS -> ticks;
            case SECONDS -> ticks / 20;
            case MINUTES -> ticks / (20 * 60);
        };
    }

    private Component unitText(WaitDurationUnit unit) {
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.unit." + unit.name().toLowerCase(Locale.ROOT));
    }

    private Component thresholdUnitText(AirshipScheduleCondition condition) {
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.unit.millibuckets");
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.unit.items");
    }

    private Component unitShortText(WaitDurationUnit unit) {
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.unit_short." + unit.name().toLowerCase(Locale.ROOT));
    }

    private Component formatCreateTime(WaitCondition waitCondition, WaitDurationUnit unit, boolean compact) {
        int amount = waitAmount(waitCondition, unit);
        if (compact) {
            return Component.literal(amount + switch (unit) {
                case TICKS -> "t";
                case SECONDS -> "s";
                case MINUTES -> "min";
            });
        }
        return Component.literal(amount + " ").append(Component.translatable(switch (unit) {
            case TICKS -> "create.generic.unit.ticks";
            case SECONDS -> "create.generic.unit.seconds";
            case MINUTES -> "create.generic.unit.minutes";
        }));
    }

    private Component statusText(AirshipScheduleEntry entry) {
        if (entry.targetStationId().isEmpty()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.status.missing_station");
        }
        if (entry.pinnedSegmentId().isPresent()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.status.pinned");
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.status.unpinned");
    }

    private Component conditionText(AirshipScheduleEntry entry) {
        int conditionCount = entry.conditionGroups().stream().mapToInt(List::size).sum();
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.conditions",
                entry.conditionGroups().size(),
                conditionCount
        );
    }

    private static boolean inside(int mx, int my, int x, int y, int width, int height) {
        return mx >= x && my >= y && mx < x + width && my < y + height;
    }

    private enum EditorMode {
        NONE,
        STATION,
        CONDITION,
        ROUTE
    }

    private enum HitType {
        NONE,
        ADD_ENTRY,
        STATION,
        REMOVE,
        DUPLICATE,
        MOVE_UP,
        MOVE_DOWN,
        CONDITION_SCROLL_LEFT,
        CONDITION_SCROLL_RIGHT,
        CONDITION,
        ADD_CONDITION,
        ADD_ALTERNATIVE
    }

    private record Hit(HitType type, int index, int conditionGroup, int conditionIndex) {
        private static final Hit NONE = new Hit(HitType.NONE, -1);

        private Hit(HitType type, int index) {
            this(type, index, -1, -1);
        }
    }

    private static final class EditorSubWidgets extends ScreenOverlay {
        private final ModularGuiLine line;

        private EditorSubWidgets() {
            super(200);
            this.line = new ModularGuiLine();
        }

        private void save(CompoundTag data) {
            this.line.saveValues(data);
        }

        private void load(CompoundTag data) {
            this.line.loadValues(data, this::add, this::addRenderableOnly);
        }

        private void reset() {
            this.line.forEach(this::remove);
            this.line.clear();
        }

        private ModularGuiLineBuilder newLineBuilder(net.minecraft.client.gui.Font font, int x, int y) {
            return new ModularGuiLineBuilder(font, this.line, x, y);
        }

        private void renderBg(int guiLeft, GuiGraphics graphics) {
            this.line.renderWidgetBG(guiLeft, graphics);
        }
    }

    private final class StationSuggestions {
        private final EditBox textBox;
        private final List<IntAttached<String>> viableStations;
        private final int yOffset;
        private final DestinationSuggestions delegate;
        private String previous = "<>";

        private StationSuggestions(EditBox textBox, List<IntAttached<String>> viableStations, int yOffset) {
            this.textBox = textBox;
            this.viableStations = viableStations;
            this.yOffset = yOffset;
            this.delegate = new DestinationSuggestions(
                    AirshipScheduleScreen.this.minecraft,
                    AirshipScheduleScreen.this,
                    textBox,
                    AirshipScheduleScreen.this.font,
                    viableStations,
                    false,
                    yOffset
            );
            this.delegate.setAllowSuggestions(true);
        }

        private void tick() {
            this.delegate.tick();
        }

        private void updateCommandInfo() {
            String value = currentValue();
            if (value.equals(this.previous)) {
                return;
            }
            this.previous = value;
            this.delegate.updateCommandInfo();
        }

        private boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return this.delegate.keyPressed(keyCode, scanCode, modifiers);
        }

        private boolean mouseClicked(int mouseX, int mouseY, int button) {
            return this.delegate.mouseClicked(mouseX, mouseY, button);
        }

        private boolean mouseScrolled(double scrollY) {
            return this.delegate.mouseScrolled(scrollY);
        }

        private void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
            this.delegate.render(guiGraphics, mouseX, mouseY);
        }

        private String currentValue() {
            String value = this.textBox.getValue();
            int cursor = Math.min(this.textBox.getCursorPosition(), value.length());
            return value.substring(0, cursor);
        }

        private void forceShow() {
            this.textBox.setFocused(true);
            AirshipScheduleScreen.this.setFocused(this.textBox);
            this.previous = "<force>";
            updateCommandInfo();
        }
    }
}


