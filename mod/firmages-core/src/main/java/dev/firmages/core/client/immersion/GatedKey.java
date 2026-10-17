package dev.firmages.core.client.immersion;

/**
 * A {@code KeyMapping} the key gate can switch off (SPEC §18.10); implemented on every key mapping by
 * {@code mixin/client/KeyMappingMixin}. While gated, the mapping reads as never pressed: {@code isDown},
 * {@code consumeClick}, {@code matches} and {@code matchesMouse} return false and pending clicks are dropped.
 */
public interface GatedKey {
    boolean firmages$gated();

    void firmages$setGated(boolean gated);
}
