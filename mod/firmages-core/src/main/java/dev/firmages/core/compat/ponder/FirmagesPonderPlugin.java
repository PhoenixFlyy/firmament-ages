package dev.firmages.core.compat.ponder;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.client.immersion.ClientAgeView;
import net.createmod.ponder.api.registration.IndexExclusionHelper;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * Ponder by Age (SPEC §21.6). Ponder 1.0.82 (in Create 6.0.10) collects the index exclusions of every plugin each
 * time the index screen is built ({@code PonderIndexScreen}, [verified, javap]), so a predicate that follows the
 * player's Ages hides the scenes of locked items from the index (opened from Create's config menu). The tag screens
 * ignore exclusions; {@code mixin.ponder.PonderTagScreenMixin} filters those. The pack's own scenes (SPEC §22.5):
 * "Silo Hatch" on {@code ad_astra:launch_pad} and "Portcullis" on {@code create:rope_pulley}, built with Create's scene
 * builder. A quest picture opens a scene through {@link QuestPonderLink}. Client only, registered only with Ponder.
 */
public final class FirmagesPonderPlugin implements PonderPlugin {
    static final ResourceLocation LAUNCH_PAD = ResourceLocation.fromNamespaceAndPath("ad_astra", "launch_pad");
    static final ResourceLocation ROPE_PULLEY = ResourceLocation.fromNamespaceAndPath("create", "rope_pulley");

    @Override
    public String getModId() {
        return FirmagesCore.MOD_ID;
    }

    @Override
    public void indexExclusions(IndexExclusionHelper helper) {
        helper.exclude(il -> ClientAgeView.itemLocked(il.asItem()));
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        if (!ModList.get().isLoaded("create")) return; // the storyboards use Create's scene builder
        if (ModList.get().isLoaded("ad_astra")) helper.forComponents(LAUNCH_PAD).addStoryBoard("silo_hatch", FirmagesScenes::siloHatch);
        helper.forComponents(ROPE_PULLEY).addStoryBoard("portcullis", FirmagesScenes::portcullis);
    }

    /** Client setup, when Ponder is loaded. */
    public static void register() {
        PonderIndex.addPlugin(new FirmagesPonderPlugin());
        if (ModList.get().isLoaded("ftbquests") && ModList.get().isLoaded("ftblibrary")) QuestPonderLink.register();
    }
}
