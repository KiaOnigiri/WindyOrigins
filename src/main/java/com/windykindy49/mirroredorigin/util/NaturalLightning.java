package com.windykindy49.mirroredorigin.util;

/**
 * True while ServerWorld#tickChunk is running, which is the one and only place vanilla makes
 * a thunderstorm strike - lightning rod hits included, since the rod only steers a bolt the
 * weather had already decided to send.
 *
 * Everything else that creates lightning does so from somewhere else entirely: a channelling
 * trident during the trident's own tick, a command during its handler. Those keep the phase
 * of whoever caused them; only what is born in here belongs to both worlds.
 */
public final class NaturalLightning {
    private static final ThreadLocal<Boolean> WEATHER_TICK = new ThreadLocal<>();

    private NaturalLightning() {
    }

    public static void begin() {
        WEATHER_TICK.set(Boolean.TRUE);
    }

    public static void end() {
        WEATHER_TICK.remove();
    }

    public static boolean isWeatherStrike() {
        return Boolean.TRUE.equals(WEATHER_TICK.get());
    }
}
