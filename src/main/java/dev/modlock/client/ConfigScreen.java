package dev.modlock.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.modlock.item.ItemConfigs;
import dev.modlock.item.KeyData;
import dev.modlock.net.ModLockNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Configuration screen of the unlock items (Mod Key, Recipe Scroll, Mundane Tome, Machine License).
 * The candidates come from the server; search filters them here. "Lock" sends the answer, which the server
 * re-validates completely.
 */
public class ConfigScreen extends Screen {
    private static final int ROW = 18;

    private record Row(String id, Component label, ItemStack icon, String search) {}

    private final ModLockNetwork.OpenConfigPayload payload;
    private final ItemConfigs.Kind kind;
    private final boolean multi;
    private final List<Row> all = new ArrayList<>();
    private List<Row> shown = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();

    private String filter = "";
    private String name = "";
    private double scroll;
    private KeyData.Mode keyMode = KeyData.Mode.ALL;
    private int amount = 1;
    private boolean permanent;
    private int minutes;
    private boolean curable;

    private Button lock;
    private Button modeButton;
    private int listX, listY, listW, listH, ctrlX, ctrlW;
    private int amountY = -1;

    public ConfigScreen(ModLockNetwork.OpenConfigPayload payload) {
        super(Component.translatable("screen.modlock.config.title." + payload.kind().toLowerCase(Locale.ROOT)));
        this.payload = payload;
        ItemConfigs.Kind k = ItemConfigs.Kind.byName(payload.kind());
        this.kind = k == null ? ItemConfigs.Kind.SCROLL : k;
        this.multi = kind == ItemConfigs.Kind.KEY;
        this.minutes = payload.defaultMinutes();
        this.curable = payload.defaultCurable();
        this.permanent = has(ItemConfigs.FLAG_PERMANENT) && !has(ItemConfigs.FLAG_TEMPORARY);
        for (ModLockNetwork.ConfigEntry e : payload.entries()) all.add(row(e));
        selected.addAll(payload.preselected());
        refilter();
    }

    public static void open(ModLockNetwork.OpenConfigPayload payload) {
        Minecraft.getInstance().setScreen(new ConfigScreen(payload));
    }

    private boolean has(int flag) {
        return (payload.flags() & flag) != 0;
    }

    private Row row(ModLockNetwork.ConfigEntry e) {
        Component label;
        ItemStack icon = ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(e.id());
        switch (kind) {
            case SCROLL -> {
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                    icon = new ItemStack(BuiltInRegistries.ITEM.get(rl));
                    label = icon.getHoverName();
                } else {
                    label = Component.literal(e.id());
                }
            }
            case LICENSE -> {
                if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) {
                    var block = BuiltInRegistries.BLOCK.get(rl);
                    icon = new ItemStack(block.asItem());
                    label = block.getName();
                } else {
                    label = Component.literal(e.id());
                }
            }
            default -> {
                if (e.id().startsWith("bundle:")) {
                    label = Component.literal("■ " + (e.label().isEmpty() ? e.id().substring(7) : e.label()));
                } else {
                    label = Component.translatableWithFallback("modlock.mod_name." + e.id(), e.label().isEmpty() ? e.id() : e.label());
                }
            }
        }
        return new Row(e.id(), label, icon, (label.getString() + " " + e.id()).toLowerCase(Locale.ROOT));
    }

    private void refilter() {
        String f = filter.toLowerCase(Locale.ROOT).strip();
        shown = f.isEmpty() ? all : all.stream().filter(r -> r.search().contains(f)).toList();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() * ROW - listH)));
    }

    @Override
    protected void init() {
        int panelW = Math.min(width - 20, 460);
        int left = (width - panelW) / 2;
        ctrlW = 160;
        listX = left;
        listY = 44;
        listW = panelW - ctrlW - 8;
        listH = height - listY - 34;
        ctrlX = listX + listW + 8;

        EditBox search = new EditBox(font, listX, 26, listW, 14, Component.translatable("screen.modlock.config.search"));
        search.setHint(Component.translatable("screen.modlock.config.search"));
        search.setValue(filter);
        search.setResponder(s -> {
            filter = s;
            refilter();
        });
        addRenderableWidget(search);

        int y = listY;
        if (kind == ItemConfigs.Kind.KEY) {
            addRenderableWidget(Button.builder(Component.translatable("screen.modlock.config.select_all"), b -> {
                boolean allSel = shown.stream().allMatch(r -> selected.contains(r.id()));
                for (Row r : shown) {
                    if (allSel) selected.remove(r.id());
                    else selected.add(r.id());
                }
                updateLock();
            }).bounds(ctrlX, y, ctrlW, 20).build());
            y += 24;
            modeButton = addRenderableWidget(Button.builder(modeLabel(), b -> {
                keyMode = KeyData.Mode.values()[(keyMode.ordinal() + 1) % KeyData.Mode.values().length];
                b.setMessage(modeLabel());
            }).bounds(ctrlX, y, ctrlW, 20).build());
            y += 24;
            amountY = y;
            addRenderableWidget(Button.builder(Component.literal("-"), b -> amount = Math.max(1, amount - step()))
                    .bounds(ctrlX, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> amount = Math.min(64, amount + step()))
                    .bounds(ctrlX + ctrlW - 20, y, 20, 20).build());
            y += 36;
            EditBox nameBox = new EditBox(font, ctrlX, y, ctrlW, 16, Component.translatable("screen.modlock.config.name"));
            nameBox.setMaxLength(ItemConfigs.MAX_NAME);
            nameBox.setHint(Component.translatable("screen.modlock.config.name"));
            nameBox.setValue(name);
            nameBox.setResponder(s -> name = s);
            addRenderableWidget(nameBox);
        } else {
            if (has(ItemConfigs.FLAG_PERMANENT) && has(ItemConfigs.FLAG_TEMPORARY)) {
                modeButton = addRenderableWidget(Button.builder(modeLabel(), b -> {
                    permanent = !permanent;
                    b.setMessage(modeLabel());
                }).bounds(ctrlX, y, ctrlW, 20).build());
                y += 24;
            }
            if (has(ItemConfigs.FLAG_DURATION)) {
                amountY = y;
                addRenderableWidget(Button.builder(Component.literal("-"),
                        b -> minutes = Math.max(1, minutes - step())).bounds(ctrlX, y, 20, 20).build());
                addRenderableWidget(Button.builder(Component.literal("+"),
                        b -> minutes = Math.min(payload.maxMinutes(), minutes + step())).bounds(ctrlX + ctrlW - 20, y, 20, 20).build());
                y += 24;
            }
            if (has(ItemConfigs.FLAG_MILK)) {
                addRenderableWidget(Checkbox.builder(Component.translatable("screen.modlock.config.milk"), font)
                        .pos(ctrlX, y).selected(curable).onValueChange((cb, v) -> curable = v).build());
            }
        }

        lock = addRenderableWidget(Button.builder(Component.translatable("screen.modlock.config.lock"), b -> submit())
                .bounds(width / 2 - 104, height - 26, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(width / 2 + 4, height - 26, 100, 20).build());
        refilter();
        updateLock();
    }

    private static int step() {
        return hasShiftDown() ? 10 : 1;
    }

    private Component modeLabel() {
        if (kind == ItemConfigs.Kind.KEY) {
            return Component.translatable("screen.modlock.config.mode",
                    Component.translatable("screen.modlock.config.mode." + keyMode.name().toLowerCase(Locale.ROOT)));
        }
        return Component.translatable("screen.modlock.config.mode",
                Component.translatable(permanent ? "screen.modlock.config.mode.permanent" : "screen.modlock.config.mode.temporary"));
    }

    private void updateLock() {
        if (lock != null) lock.active = multi ? !selected.isEmpty() : selected.size() == 1;
    }

    private void submit() {
        if (!lock.active) return;
        String mode = kind == ItemConfigs.Kind.KEY ? keyMode.name() : (permanent ? "PERMANENT" : "TEMPORARY");
        PacketDistributor.sendToServer(new ModLockNetwork.ConfigAnswerPayload(payload.kind(), payload.mainHand(),
                new ArrayList<>(selected), mode, amount, minutes, curable, name));
        onClose();
    }

    // ---------------- input ----------------

    private int rowAt(double mx, double my) {
        if (mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) return -1;
        int i = (int) ((my - listY + scroll) / ROW);
        return i >= 0 && i < shown.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = rowAt(mx, my);
        if (i >= 0) {
            String id = shown.get(i).id();
            if (selected.contains(id)) {
                selected.remove(id);
            } else {
                if (!multi) selected.clear();
                selected.add(id);
            }
            updateLock();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= listX && mx < listX + listW) {
            double max = Math.max(0, shown.size() * ROW - listH);
            scroll = Math.max(0, Math.min(max, scroll - sy * ROW * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // ---------------- rendering ----------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0xFF000000);
        g.fill(listX, listY, listX + listW, listY + listH, 0xC0101010);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int hover = rowAt(mx, my);
        int first = (int) (scroll / ROW);
        for (int i = first; i < shown.size(); i++) {
            int y = listY + i * ROW - (int) scroll;
            if (y > listY + listH) break;
            Row r = shown.get(i);
            boolean sel = selected.contains(r.id());
            if (sel) g.fill(listX, y, listX + listW, y + ROW, 0xFF2E5E2E);
            else if (i == hover) g.fill(listX, y, listX + listW, y + ROW, 0xFF2A2A2A);
            int x = listX + 3;
            g.fill(x, y + 4, x + 10, y + 14, 0xFF8B8B8B);
            g.fill(x + 1, y + 5, x + 9, y + 13, 0xFF202020);
            if (sel) g.drawString(font, "✔", x + 1, y + 5, 0x55FF55);
            x += 14;
            if (!r.icon().isEmpty()) {
                g.renderItem(r.icon(), x, y + 1);
                x += 18;
            }
            g.drawString(font, font.plainSubstrByWidth(r.label().getString(), listX + listW - x - 4), x, y + 5, 0xFFFFFF);
        }
        g.disableScissor();

        int infoY = height - 40;
        g.drawString(font, Component.translatable("screen.modlock.config.selected", selected.size(), all.size()),
                ctrlX, infoY, 0xAAAAAA);
        if (amountY >= 0) {
            Component v;
            if (kind == ItemConfigs.Kind.KEY) {
                v = Component.translatable("screen.modlock.config.amount", amount);
            } else if (permanent) {
                v = Component.translatable("screen.modlock.config.mode.permanent");
            } else {
                v = Component.translatable("tooltip.modlock.minutes", minutes);
            }
            g.drawCenteredString(font, v, ctrlX + ctrlW / 2, amountY + 6, 0xFFFFFF);
        }
        if (kind == ItemConfigs.Kind.KEY) {
            g.drawString(font, Component.translatable("screen.modlock.config.name"), ctrlX, amountY + 26, 0xAAAAAA);
        }
        if (hover >= 0) {
            g.renderTooltip(font, Component.literal(shown.get(hover).id()), mx, my);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
