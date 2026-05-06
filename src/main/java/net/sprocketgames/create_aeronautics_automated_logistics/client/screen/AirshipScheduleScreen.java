package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import com.mojang.math.Axis;
import com.simibubi.create.content.trains.schedule.DestinationSuggestions;
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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
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
    private DestinationSuggestions stationSuggestions;
    private AirshipSchedule localSchedule;
    private EditorMode editorMode = EditorMode.NONE;
    private int editorEntryIndex;
    private int editorConditionGroup;
    private int editorConditionIndex;
    private int selectedIndex;
    private int scrollOffset;
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
                112,
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
        guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(text.getString(), width - 12), x + 6, y + 4, TEXT_COLOR, true);
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
        guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(text.getString(), textLimit), x + (hasIcon ? 28 : 8), y + 4, TEXT_COLOR, true);
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
        blit(guiGraphics, PLAYER_INVENTORY, this.leftPos + 38, this.topPos + 122, 0, 0, 176, 108);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.leftPos + 46, this.topPos + 128, DARK_TEXT_COLOR, false);
        Component title = this.editorMode == EditorMode.STATION
                ? Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.instruction_editor")
                : Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.condition_editor");
        guiGraphics.drawString(this.font, title.getVisualOrderText(), this.leftPos + 124 - this.font.width(title) / 2, this.topPos + 44, DARK_TEXT_COLOR, false);

        if (this.editorMode == EditorMode.STATION) {
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
            renderEditorChoiceInput(guiGraphics, this.leftPos + 56, this.topPos + 65, 143, Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.travel_to_station"));
            blit(guiGraphics, SCHEDULE_EDITOR, x + 55, y + 47, 55, 47, 32, 18);
            guiGraphics.renderItem(ModItems.AIRSHIP_STATION.get().getDefaultInstance(), this.leftPos + 54, this.topPos + 88);
            renderDataAreaBox(guiGraphics, this.leftPos + 77, this.topPos + 88, 121, true);
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
            renderEditorChoiceInput(guiGraphics, this.leftPos + 56, this.topPos + 65, 143, Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.scheduled_delay"));
            blit(guiGraphics, SCHEDULE_EDITOR, this.leftPos + 53, this.topPos + 87, 55, 47, 32, 18);
            guiGraphics.renderItem(Items.REPEATER.getDefaultInstance(), this.leftPos + 54, this.topPos + 88);
            renderDataAreaBox(guiGraphics, this.leftPos + 77, this.topPos + 88, 31, true);
            renderDataAreaBox(guiGraphics, this.leftPos + 113, this.topPos + 88, 85, false);
            guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(currentCondition().map(this::conditionValueText).orElse(Component.literal("0")).getString(), 22), this.leftPos + 82, this.topPos + 92, TEXT_COLOR, true);
            guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(currentEntry().map(entry -> unitText(entry.waitUnit())).orElse(Component.empty()).getString(), 76), this.leftPos + 118, this.topPos + 92, TEXT_COLOR, true);
        }
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
        pose.popPose();
    }

    private boolean hasDisplayedIcon(net.minecraft.world.item.ItemStack icon) {
        return !icon.isEmpty() && icon.getItem() != Items.STRUCTURE_VOID;
    }

    private int fieldSize(int minSize, Component text, net.minecraft.world.item.ItemStack icon) {
        return Math.max((text == null ? 0 : this.font.width(text)) + (hasDisplayedIcon(icon) ? 20 : 0) + 16, minSize);
    }

    private void renderEditorChoiceInput(GuiGraphics guiGraphics, int x, int y, int width, Component text) {
        renderDataAreaBox(guiGraphics, x, y, width, false);
        guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(text.getString(), width - 12), x + 8, y + 4, TEXT_COLOR, false);
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
            if (inside(mx, my, 53, 87, 32, 18) || inside(mx, my, 77, 88, 31, 18)) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.duration").withStyle(ChatFormatting.BLUE),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.scroll_modify").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.shift_scroll_faster").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                );
            }
            if (inside(mx, my, 113, 88, 85, 18)) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_unit").withStyle(ChatFormatting.BLUE),
                        optionLine(WaitDurationUnit.TICKS),
                        optionLine(WaitDurationUnit.SECONDS),
                        optionLine(WaitDurationUnit.MINUTES),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.scroll_select").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                );
            }
        }
        return List.of();
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
        tooltip.add(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.scheduled_delay"));
        tooltip.add(Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.for_time",
                longConditionTimeText(condition, entry.waitUnit())
        ).withStyle(ChatFormatting.DARK_AQUA));
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
        if (this.editorMode == EditorMode.STATION) {
            if (inside(mx, my, 11, 87, 18, 18)) {
                removeSelectedLocally(currentSchedule());
                syncSchedule();
                closeEditor();
                return true;
            }
            if (inside(mx, my, 77, 88, 121, 18) || inside(mx, my, 53, 87, 32, 18)) {
                if (this.stationFilterBox != null) {
                    this.stationFilterBox.mouseClicked(mouseX, mouseY, button);
                    this.stationFilterBox.setFocused(true);
                    setFocused(this.stationFilterBox);
                    if (this.stationSuggestions != null) {
                        this.stationSuggestions.updateCommandInfo();
                    }
                }
                return true;
            }
        }
        if (inside(mx, my, 224, 87, 18, 18)) {
            confirmEditor();
            return true;
        }
        if (this.editorMode == EditorMode.CONDITION) {
            if (inside(mx, my, 11, 87, 18, 18) && canRemoveEditedCondition()) {
                removeConditionLocally(this.editorEntryIndex, this.editorConditionGroup, this.editorConditionIndex);
                syncSchedule();
                closeEditor();
                return true;
            }
            if (inside(mx, my, 77, 88, 31, 18) || inside(mx, my, 53, 87, 32, 18)) {
                return true;
            }
            if (inside(mx, my, 113, 88, 85, 18)) {
                pressAction(AirshipScheduleMenu.ACTION_CYCLE_WAIT_UNIT);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.stationSuggestions != null && this.stationSuggestions.mouseScrolled(Mth.clamp(scrollY, -1.0D, 1.0D))) {
            return true;
        }
        if (this.editorMode == EditorMode.CONDITION) {
            int mx = (int) mouseX - this.leftPos;
            int my = (int) mouseY - this.topPos;
            if (inside(mx, my, 77, 88, 31, 18) || inside(mx, my, 53, 87, 32, 18)) {
                int steps = hasShiftDown() ? 15 : 1;
                for (int i = 0; i < steps; i++) {
                    pressAction(scrollY > 0 ? AirshipScheduleMenu.ACTION_WAIT_UP : AirshipScheduleMenu.ACTION_WAIT_DOWN);
                }
                return true;
            }
            if (inside(mx, my, 113, 88, 85, 18)) {
                int steps = Math.abs((int) Math.round(scrollY));
                if (steps == 0) {
                    steps = 1;
                }
                for (int i = 0; i < steps; i++) {
                    pressAction(AirshipScheduleMenu.ACTION_CYCLE_WAIT_UNIT);
                }
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
        this.titleBox.visible = false;
        this.titleBox.active = false;
        this.stationFilterBox.visible = true;
        this.stationFilterBox.active = true;
        this.stationFilterBox.setValue(currentEntry().map(AirshipScheduleEntry::targetStationName).orElse(""));
        this.stationFilterBox.setFocused(false);
        this.stationFilterBox.setSuggestion("");
        this.stationSuggestions = createStationSuggestions();
        setFocused(null);
    }

    private void openConditionEditor(int index, int conditionGroup, int conditionIndex) {
        this.editorMode = EditorMode.CONDITION;
        this.editorEntryIndex = index;
        this.editorConditionGroup = conditionGroup;
        this.editorConditionIndex = conditionIndex;
        this.titleBox.visible = false;
        this.titleBox.active = false;
        this.stationFilterBox.visible = false;
        this.stationFilterBox.active = false;
        this.stationFilterBox.setFocused(false);
        this.stationFilterBox.setSuggestion("");
        this.stationSuggestions = null;
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

    private void closeEditor() {
        this.editorMode = EditorMode.NONE;
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
            List<AirshipStationSnapshot> matches = filteredStations();
            if (!matches.isEmpty()) {
                selectStation(matches.getFirst());
                return;
            }
        }
        closeEditor();
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
        int currentTicks = condition.waitCondition().type() == WaitConditionType.TIMED ? condition.waitCondition().durationTicks() : 0;
        int nextTicks = Math.max(0, currentTicks + direction * entry.waitUnit().ticksPerStep() * 5);
        setEditedConditionWait(nextTicks == 0 ? WaitCondition.none() : WaitCondition.timed(nextTicks));
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

    private void setEditedConditionWait(WaitCondition waitCondition) {
        updateConditionGroups(this.editorEntryIndex, groups -> {
            if (this.editorConditionGroup < 0 || this.editorConditionGroup >= groups.size()) {
                return;
            }
            List<AirshipScheduleCondition> group = groups.get(this.editorConditionGroup);
            if (this.editorConditionIndex < 0 || this.editorConditionIndex >= group.size()) {
                return;
            }
            group.set(this.editorConditionIndex, AirshipScheduleCondition.scheduledDelay(waitCondition));
        });
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
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        String filter = this.stationFilterBox == null ? "" : this.stationFilterBox.getValue().trim().toLowerCase(Locale.ROOT);
        return AirshipStationRegistry.knownStations(this.minecraft.level.dimension()).stream()
                .filter(station -> filter.isBlank() || station.stationName().toLowerCase(Locale.ROOT).startsWith(filter))
                .toList();
    }

    private void selectStation(AirshipStationSnapshot station) {
        setEntryStationLocally(this.editorEntryIndex, station);
        syncSchedule();
        closeEditor();
    }

    private DestinationSuggestions createStationSuggestions() {
        if (this.minecraft == null || this.stationFilterBox == null) {
            return null;
        }
        DestinationSuggestions suggestions = new DestinationSuggestions(
                this.minecraft,
                this,
                this.stationFilterBox,
                this.font,
                viableStations(),
                false,
                this.topPos + 33
        );
        suggestions.setAllowSuggestions(true);
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
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.wait.short",
                waitAmount(condition.waitCondition(), unit),
                unitShortText(unit)
        );
    }

    private Component longConditionTimeText(AirshipScheduleCondition condition, WaitDurationUnit unit) {
        return Component.literal(waitAmount(condition.waitCondition(), unit) + " ").append(unitText(unit));
    }

    private Component conditionValueText(AirshipScheduleCondition condition) {
        WaitDurationUnit unit = currentEntry().map(AirshipScheduleEntry::waitUnit).orElse(WaitDurationUnit.SECONDS);
        return Component.literal(Integer.toString(waitAmount(condition.waitCondition(), unit)));
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
        return switch (waitUnit) {
            case TICKS -> waitCondition.durationTicks();
            case SECONDS -> waitCondition.durationTicks() / 20;
            case MINUTES -> waitCondition.durationTicks() / (20 * 60);
        };
    }

    private Component unitText(WaitDurationUnit unit) {
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.unit." + unit.name().toLowerCase(Locale.ROOT));
    }

    private Component unitShortText(WaitDurationUnit unit) {
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.unit_short." + unit.name().toLowerCase(Locale.ROOT));
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
        CONDITION
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
}


