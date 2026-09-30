package dev.firmages.core.compat.kubejs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/** KubeJS plugin, listed in {@code kubejs.plugins.txt}; KubeJS loads it only when KubeJS is present. */
public final class FirmagesKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("FirmAges", FirmAgesJS.class);
    }
}
