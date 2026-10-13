package dev.firmages.core.client.immersion;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.immersion.ChatBlocklist;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Applies {@code chat.blocklist} for {@code mixin.client.ChatComponentMixin} (SPEC §18.1). */
public final class ChatSilencer {
    private static volatile ChatBlocklist compiled = ChatBlocklist.EMPTY;

    private ChatSilencer() {}

    /** True if the unsigned chat line must be dropped; logs it. */
    public static boolean drop(Component message) {
        List<? extends String> configured = ClientConfig.get(ClientConfig.CHAT_BLOCKLIST, ChatBlocklist.DEFAULT_PATTERNS);
        ChatBlocklist list = compiled;
        if (!list.source().equals(configured)) {
            list = ChatBlocklist.compile(configured);
            for (String error : list.errors()) FirmagesCore.LOGGER.warn("firmages-client.toml chat.blocklist: invalid pattern {}", error);
            compiled = list;
        }
        String text = message.getString();
        if (!list.blocks(text)) return false;
        FirmagesCore.LOGGER.info("Chat line dropped by chat.blocklist: {}", text.replace("\n", "\\n"));
        return true;
    }
}
