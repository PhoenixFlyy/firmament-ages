package dev.firmages.core.mixin.betterf3;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.firmages.core.client.immersion.F3VeilClient;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * The F3 veil in BetterF3 (SPEC §18.11). BetterF3 11.0.3 [verified, javap]: {@code DebugRenderer.newText} asks every
 * enabled module of a column for {@code BaseModule.linesFormatted(boolean)} (a {@code List<Component>}, one per shown
 * {@code DebugLine}: name and value through {@code DebugLine.toText}, list lines through {@code DebugLineList.toTexts})
 * and draws them; the module's colours are its public fields {@code nameColor} and {@code valueColor}. The returned
 * lines go through {@code F3VeilClient.veilModule} (the colours read by name, so a BetterF3 without them only loses
 * the colours). Client only, applied only when BetterF3 is loaded.
 */
@Pseudo
@Mixin(targets = "me.cominixo.betterf3.modules.BaseModule", remap = false)
public abstract class BaseModuleMixin {
    @ModifyReturnValue(method = "linesFormatted(Z)Ljava/util/List;", at = @At("RETURN"), require = 0)
    private List<Component> firmages$veil(List<Component> lines) {
        return F3VeilClient.veilModule(this, lines);
    }
}
