package dev.firmages.core.compat.ftbquests;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import net.neoforged.fml.ModList;

/**
 * Opens the FTB Quests screen, the pack's only progress screen (SPEC §18.2). FTB Quests 2101.1.36 [verified, javap]:
 * {@code ClientQuestFile.openGui()} opens the last quest view (it prints FTB's "missing data" lines when the quest
 * file has not synced yet). Client only; FTB Quests classes load only through {@link #open()} when the mod is present.
 */
public final class FtbQuestsScreens {
    private FtbQuestsScreens() {}

    public static boolean available() {
        return ModList.get().isLoaded("ftbquests");
    }

    /** @return true if FTB Quests is installed and its quest file was synced, so the screen opened */
    public static boolean open() {
        if (!available()) return false;
        return Impl.open();
    }

    private static final class Impl {
        static boolean open() {
            if (!ClientQuestFile.exists()) return false;
            return ClientQuestFile.openGui() != null;
        }
    }
}
