package dev.modlock.attach;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import dev.modlock.ModLock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModLockAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ModLock.MODID);

    /**
     * UUID (as string) of the player who last put the input item of a furnace by hand.
     * Empty = inserted by automation, never blocked.
     * Attachment serialization is MapCodec based since 1.21.6, so the value is stored as {inserter: "..."}.
     */
    public static final Supplier<AttachmentType<String>> FURNACE_INSERTER = ATTACHMENTS.register(
            "furnace_inserter",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING.fieldOf("inserter"), s -> !s.isEmpty()).build());

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    private ModLockAttachments() {}
}
