package dev.firmages.core.client;

import com.klikli_dev.modonomicon.api.multiblock.Multiblock;
import com.klikli_dev.modonomicon.client.render.MultiblockPreviewRenderer;
import com.klikli_dev.modonomicon.data.MultiblockDataManager;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.modonomicon.ShrineMultiblocks;
import dev.firmages.core.net.ShrinePreviewPayload;
import net.minecraft.world.level.block.Rotation;

/**
 * Modonomicon ghost preview of a ring (SPEC §11 {@code shrine_preview}): the client copy of the multiblock
 * (Modonomicon syncs its multiblocks to clients) anchored so that its centre sits on the heart.
 */
final class PreviewBridge {
    private PreviewBridge() {}

    static void show(ShrinePreviewPayload p) {
        Multiblock mb = MultiblockDataManager.get().getMultiblock(p.multiblock());
        if (mb == null) {
            FirmagesCore.LOGGER.warn("Shrine preview: multiblock {} is unknown on this client", p.multiblock());
            return;
        }
        Rotation[] rs = Rotation.values();
        Rotation r = p.rotation() >= 0 && p.rotation() < rs.length ? rs[p.rotation()] : Rotation.NONE;
        MultiblockPreviewRenderer.setMultiblock(mb, ShrineMultiblocks.name(p.multiblock()), false);
        // The preview draws the structure one block above its anchor (anchor = the block clicked on), so the
        // anchor is the block below the heart. [PoC] check the alignment in the client.
        MultiblockPreviewRenderer.anchorTo(p.heart().below(), r);
    }

    static void clear() {
        if (MultiblockPreviewRenderer.hasMultiblock) MultiblockPreviewRenderer.setMultiblock(null, net.minecraft.network.chat.Component.empty(), false);
    }
}
