package dev.firmages.core.mixin.client;

import dev.firmages.core.client.immersion.ChatSilencer;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client chat safety net (SPEC §18.1). Every chat line ends in this overload (the one-argument
 * {@code addMessage(Component)} delegates to it; server system chat and {@code LocalPlayer.sendSystemMessage} both
 * reach it) [verified, NeoForge 21.1.252 sources]. Unsigned lines whose text matches {@code chat.blocklist} are
 * dropped; signed player chat is never touched.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"), cancellable = true)
    private void firmages$blocklist(Component message, @Nullable MessageSignature signature, @Nullable GuiMessageTag tag, CallbackInfo ci) {
        if (signature == null && ChatSilencer.drop(message)) ci.cancel();
    }
}
