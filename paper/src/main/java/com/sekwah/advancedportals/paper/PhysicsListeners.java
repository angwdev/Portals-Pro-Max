package com.sekwah.advancedportals.paper;

import com.sekwah.advancedportals.core.CoreListeners;
import com.sekwah.advancedportals.shadowed.inject.Inject;
import com.sekwah.advancedportals.paper.utils.ContainerHelpers;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

public class PhysicsListeners implements Listener {
    @Inject
    private CoreListeners coreListeners;

    @EventHandler(priority = EventPriority.HIGH)
    public void onPhysicsEvent(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (!coreListeners.physicsEvent(block.getWorld().getName(),
                                        block.getX(), block.getY(),
                                        block.getZ())) {
            event.setCancelled(true);
        }
    }
}
