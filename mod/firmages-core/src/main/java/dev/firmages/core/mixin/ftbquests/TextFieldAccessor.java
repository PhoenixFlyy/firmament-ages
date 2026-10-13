package dev.firmages.core.mixin.ftbquests;

import dev.ftb.mods.ftblibrary.ui.TextField;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The unwrapped text of an FTB Library text field (private {@code rawText}, FTB Library 2101.1.37 [verified, javap]). */
@Mixin(value = TextField.class, remap = false)
public interface TextFieldAccessor {
    @Accessor("rawText")
    Component firmages$rawText();
}
