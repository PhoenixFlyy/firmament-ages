package dev.firmages.core.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skips the mod-specific mixins (SPEC §1.3) when their mod is not installed: {@code mixin.<pkg>.*} needs the mod
 * id mapped here. Mixins directly in {@code dev.firmages.core.mixin} (vanilla targets) always apply.
 */
public final class FirmagesMixinPlugin implements IMixinConfigPlugin {
    private static final String BASE = "dev.firmages.core.mixin.";
    private static final Map<String, String> MOD_OF_PACKAGE = Map.of(
        "ie", "immersiveengineering",
        "occultism", "occultism",
        "ars", "ars_nouveau");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(BASE)) return true;
        String rest = mixinClassName.substring(BASE.length());
        int dot = rest.indexOf('.');
        if (dot < 0) return true;
        String modId = MOD_OF_PACKAGE.get(rest.substring(0, dot));
        return modId == null || isLoaded(modId);
    }

    static boolean isLoaded(String modId) {
        LoadingModList list = LoadingModList.get();
        return list != null && list.getModFileById(modId) != null;
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
