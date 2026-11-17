package dev.modlock.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.platform.NativeImage;

import dev.modlock.ModLock;
import dev.modlock.core.StartingChoice;
import dev.modlock.net.ModLockNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Starting choice: list of bundles / single mods on the left, details of the highlighted one on the
 * right (picture, description, mods it contains). The player marks exactly {@code count} entries.
 */
public class ChoiceScreen extends Screen {
    private static final int ROW = 22;
    private static final Map<String, ResourceLocation> IMAGE_CACHE = new HashMap<>();

    private final int count;
    private final List<StartingChoice.Option> options;
    private final Set<String> picked = new LinkedHashSet<>();
    private int focused = 0;
    private double scroll = 0;
    private Button confirm;

    private int listX, listY, listW, listH, detailX, detailW;

    public ChoiceScreen(int count, List<StartingChoice.Option> options) {
        super(Component.translatable("screen.modlock.choice.title"));
        this.count = count;
        this.options = new ArrayList<>(options);
        for (int i = 0; i < this.options.size(); i++) {
            if (this.options.get(i).takenBy().isEmpty()) {
                focused = i;
                break;
            }
        }
    }

    public static void open(int count, List<StartingChoice.Option> options) {
        Minecraft.getInstance().setScreen(new ChoiceScreen(count, options));
    }

    @Override
    protected void init() {
        int panelW = Math.min(width - 20, 420);
        int left = (width - panelW) / 2;
        listX = left;
        listY = 40;
        listW = Math.min(170, panelW / 2 - 5);
        listH = height - listY - 40;
        detailX = listX + listW + 10;
        detailW = left + panelW - detailX;
        confirm = addRenderableWidget(Button.builder(Component.translatable("screen.modlock.choice.confirm"), b -> submit())
                .bounds(width / 2 - 60, height - 30, 120, 20).build());
        updateConfirm();
    }

    private void updateConfirm() {
        if (confirm != null) confirm.active = picked.size() == count;
    }

    private void submit() {
        if (picked.size() != count) return;
        PacketDistributor.sendToServer(new ModLockNetwork.ChoiceAnswerPayload(new ArrayList<>(picked)));
        onClose();
    }

    // ---------------- input ----------------

    private int rowAt(double mx, double my) {
        if (mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) return -1;
        int i = (int) ((my - listY + scroll) / ROW);
        return i >= 0 && i < options.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = rowAt(mx, my);
        if (i >= 0) {
            focused = i;
            StartingChoice.Option o = options.get(i);
            if (o.takenBy().isEmpty()) {
                if (picked.contains(o.key())) {
                    picked.remove(o.key());
                } else if (count == 1) {
                    picked.clear();
                    picked.add(o.key());
                } else if (picked.size() < count) {
                    picked.add(o.key());
                }
                updateConfirm();
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        double max = Math.max(0, options.size() * ROW - listH);
        scroll = Math.max(0, Math.min(max, scroll - sy * ROW));
        return true;
    }

    // ---------------- rendering ----------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.modlock.choice.subtitle", picked.size(), count),
                width / 2, 24, 0xAAAAAA);

        // list
        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0xFF000000);
        g.fill(listX, listY, listX + listW, listY + listH, 0xC0101010);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int hover = rowAt(mx, my);
        for (int i = 0; i < options.size(); i++) {
            int y = listY + i * ROW - (int) scroll;
            if (y + ROW < listY || y > listY + listH) continue;
            StartingChoice.Option o = options.get(i);
            boolean taken = !o.takenBy().isEmpty();
            boolean sel = picked.contains(o.key());
            int bg = sel ? 0xFF2E5E2E : (i == focused ? 0xFF3A3A3A : (i == hover ? 0xFF2A2A2A : 0));
            if (bg != 0) g.fill(listX, y, listX + listW, y + ROW, bg);
            ItemStack icon = iconOf(o);
            int textX = listX + 4;
            if (!icon.isEmpty()) {
                g.renderItem(icon, listX + 3, y + 3);
                textX = listX + 23;
            }
            int color = taken ? 0x777777 : 0xFFFFFF;
            String prefix = o.key().startsWith("bundle:") ? "■ " : "";
            g.drawString(font, font.plainSubstrByWidth(prefix + o.name(), listW - (textX - listX) - 14), textX, y + 7, color);
            g.drawString(font, sel ? "✔" : (taken ? "✖" : ""), listX + listW - 10, y + 7, sel ? 0x55FF55 : 0xAA5555);
        }
        g.disableScissor();

        // details
        if (focused >= 0 && focused < options.size()) renderDetails(g, options.get(focused));
    }

    private void renderDetails(GuiGraphics g, StartingChoice.Option o) {
        int x = detailX, y = listY;
        g.fill(x - 1, y - 1, x + detailW + 1, listY + listH + 1, 0xFF000000);
        g.fill(x, y, x + detailW, listY + listH, 0xC0101010);
        x += 6;
        y += 6;
        int w = detailW - 12;

        ResourceLocation img = imageOf(o);
        if (img != null) {
            int size = Math.min(96, w);
            g.blit(img, x + (w - size) / 2, y, 0, 0, size, size, size, size);
            y += size + 6;
        } else {
            ItemStack icon = iconOf(o);
            if (!icon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(x + w / 2f - 16, y, 0);
                g.pose().scale(2, 2, 1);
                g.renderItem(icon, 0, 0);
                g.pose().popPose();
                y += 38;
            }
        }
        g.drawString(font, Component.literal(o.name()).withStyle(s -> s.withBold(true)), x, y, 0xFFFFFF);
        y += 12;
        Component kind = Component.translatable(o.key().startsWith("bundle:")
                ? "screen.modlock.choice.bundle" : "screen.modlock.choice.mod");
        g.drawString(font, kind, x, y, 0x88AAFF);
        y += 12;
        if (!o.takenBy().isEmpty()) {
            g.drawString(font, Component.translatable("screen.modlock.choice.taken", o.takenBy()), x, y, 0xFF6666);
            y += 12;
        }
        if (!o.description().isEmpty()) {
            g.drawWordWrap(font, Component.literal(o.description()), x, y, w, 0xCCCCCC);
            y += font.split(Component.literal(o.description()), w).size() * 9 + 4;
        }
        if (!o.members().isEmpty()) {
            g.drawString(font, Component.translatable("screen.modlock.choice.members", o.members().size()), x, y, 0xFFDD55);
            y += 11;
            Component list = Component.literal(String.join(", ", o.members()));
            int maxY = listY + listH - 6;
            for (var line : font.split(list, w)) {
                if (y + 9 > maxY) {
                    g.drawString(font, "...", x, y, 0xAAAAAA);
                    break;
                }
                g.drawString(font, line, x, y, 0xAAAAAA);
                y += 9;
            }
        }
    }

    private static ItemStack iconOf(StartingChoice.Option o) {
        if (o.icon().isEmpty()) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(o.icon().startsWith("item:") ? o.icon().substring(5) : o.icon());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    /** Picture sent by the server (PNG bytes) or a texture id from a resource pack. */
    private static ResourceLocation imageOf(StartingChoice.Option o) {
        if (o.imageBytes().length > 0) {
            String cacheKey = o.key() + "#" + java.util.Arrays.hashCode(o.imageBytes());
            return IMAGE_CACHE.computeIfAbsent(cacheKey, k -> {
                try {
                    // read(byte[]) copies into the 64 KiB LWJGL stack and crashes on bigger PNGs; the stream overload allocates off-heap
                    NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(o.imageBytes()));
                    ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(ModLock.MODID,
                            "choice_image/" + Integer.toHexString(k.hashCode()));
                    Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(image));
                    return rl;
                } catch (Exception | OutOfMemoryError e) {
                    return null;
                }
            });
        }
        if (!o.image().isEmpty()) return ResourceLocation.tryParse(o.image());
        return null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
