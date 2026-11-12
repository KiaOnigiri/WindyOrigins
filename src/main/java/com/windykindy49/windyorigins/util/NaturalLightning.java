package com.windykindy49.windyorigins.util;

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
