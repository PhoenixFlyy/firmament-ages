package dev.firmages.core.compat.tfc;

import dev.firmages.core.sky.SkyHarvestData;
import dev.firmages.core.sky.StormForecast;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.climate.Climate;
import net.dries007.tfc.util.climate.ClimateModel;
import net.minecraft.server.level.ServerLevel;

/**
 * TerraFirmaCraft's weather series for {@code /firmages sky storm forecast} (SPEC §26.10): the overworld's climate
 * model, read from the current calendar tick on ({@code Calendars.SERVER.getCalendarTicks}, the clock TFC's weather
 * cycle reads [verified, javap TFC 4.2.11]). Loaded only with TFC present.
 */
public final class TfcWeather {
    private TfcWeather() {}

    public static StormForecast forecast(ServerLevel overworld, SkyHarvestData.Storm data, long ticks) {
        ClimateModel model = Climate.get(overworld);
        if (!model.supportsRain()) return new StormForecast(ticks, 0, 0, 0, 0);
        long now = Calendars.SERVER.getCalendarTicks();
        return StormForecast.replay(data, now, ticks, overworld.getRainLevel(1F), overworld.getThunderLevel(1F), model::getRain, model::getThunder);
    }
}
