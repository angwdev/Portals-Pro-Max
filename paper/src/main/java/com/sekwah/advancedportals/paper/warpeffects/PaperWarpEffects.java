package com.sekwah.advancedportals.paper.warpeffects;

import com.sekwah.advancedportals.core.registry.WarpEffectRegistry;
import com.sekwah.advancedportals.shadowed.inject.Inject;

public class PaperWarpEffects {
    @Inject
    private WarpEffectRegistry warpEffectRegistry;

    public void registerEffects() {
        warpEffectRegistry.registerEffect("ender", new EnderWarpEffect());
    }
}
