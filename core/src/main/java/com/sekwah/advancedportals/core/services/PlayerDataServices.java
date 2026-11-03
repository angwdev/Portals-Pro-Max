package com.sekwah.advancedportals.core.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.repository.ConfigRepository;
import com.sekwah.advancedportals.core.repository.IPlayerDataRepository;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.PlayerData;
import com.sekwah.advancedportals.core.util.Lang;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

@Singleton
public final class PlayerDataServices {
    /**
     * Possibly change to the cache map Aztec was talking about
     */
    private final Map<UUID, PlayerData> tempDataMap =
        new ConcurrentHashMap<>();

    @Inject
    private IPlayerDataRepository tempDataRepository;

    @Inject
    private ConfigRepository configRepository;

    /**
     * Called for every movement packet, so it must be cheap and must never
     * block other (region) threads: a plain concurrent map lookup, only
     * hitting the repository the first time a player is seen.
     */
    public PlayerData getPlayerData(PlayerContainer player) {
        UUID uuid = player.getUUID();
        PlayerData playerData = tempDataMap.get(uuid);
        if (playerData != null) {
            return playerData;
        }
        return tempDataMap.computeIfAbsent(uuid, id -> {
            PlayerData tempData = tempDataRepository.get(id.toString());

            if (tempData == null) {
                tempData = new PlayerData();
            }
            return tempData;
        });
    }

    public void setJoinCooldown(PlayerContainer player) {
        PlayerData tempData = getPlayerData(player);
        tempData.setJoinCooldown(configRepository.getPortalCooldown() * 1000);
    }

    public void playerLeave(PlayerContainer player) {
        if(!configRepository.disablePlayerDataSaving()) {
            tempDataRepository.save(player.getUUID().toString(),
                    getPlayerData(player));
        }
        tempDataMap.remove(player.getUUID());
    }

    public void playerSelectorActivate(PlayerContainer player,
                                       BlockLocation blockLoc,
                                       boolean leftClick) {
        PlayerData tempData = getPlayerData(player);
        if (leftClick) {
            tempData.setPos1(blockLoc);
        } else {
            tempData.setPos2(blockLoc);
        }
        player.sendMessage(Lang.translateInsertVariables(
            "portal.selector.poschange", leftClick ? "1" : "2",
            blockLoc.getPosX(), blockLoc.getPosY(), blockLoc.getPosZ()));
    }
}
