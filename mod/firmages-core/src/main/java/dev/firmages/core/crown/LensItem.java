package dev.firmages.core.crown;

import dev.firmages.core.shrine.ShrineDataLoader;
import dev.firmages.core.shrine.ShrineTier;
import net.minecraft.world.item.Item;

/**
 * One of the nine lenses (SPEC §27.4). Lens N belongs to shrine tier N and carries that tier's beam colour, read from
 * the loaded tier data ({@code tier/ring_N.json}) on the server and from {@code firmages:shrine_sync} on the client.
 */
public final class LensItem extends Item {
    private final int index;

    public LensItem(int index, Properties properties) {
        super(properties);
        this.index = index;
    }

    /** 0..8 = Stone .. Quantum. */
    public int index() {
        return index;
    }

    /** Server side: the tier's beam colour as RGB, or white when the tier data does not name one. */
    public static int serverColor(int index) {
        return ShrineDataLoader.current().tier(index).map(ShrineTier::response).map(r -> r.beamColor() & 0xFFFFFF).orElse(0xFFFFFF);
    }
}
