package dev.firmages.core;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(FirmagesCore.MOD_ID)
public final class FirmagesCore {
    public static final String MOD_ID = "firmages";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FirmagesCore(IEventBus modEventBus) {
        LOGGER.info("firmages-core loaded");
    }
}
