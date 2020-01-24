package me.kernelerror.skinapi;

import com.comphenix.packetwrapper.*;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.wrappers.*;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.mineskin.MineskinClient;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class SkinAPI {
    private final MineskinClient client = new MineskinClient();
    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;

    public SkinAPI(JavaPlugin plugin) {
        this.plugin = plugin;
        protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void uploadSkin(Player player, String url, SkinUploadCallback callback) throws MalformedURLException, IOException {
        final URL skinUrl = new URL(url);
        final File skinFile = new File(plugin.getDataFolder(), "skins/" + player.getUniqueId() + ".skin");

        FileUtils.copyURLToFile(skinUrl, skinFile);
        client.generateUpload(skinFile, skin -> {
            skinFile.delete();
            callback.done(skin.id);
        });
    }

    public void setSkin(final Player player, final int id, final SkinSetCallback callback) {
        setSkin(Bukkit.getOnlinePlayers(), player, id, callback);
    }

    public void setSkin(final Collection<? extends Player> observers, final Player player, final int id, SkinSetCallback callback) {
        // Update skin texture
        final WrappedGameProfile gameProfile = WrappedGameProfile.fromPlayer(player);
        gameProfile.getProperties().clear();
        gameProfile.getProperties().put("textures", new WrappedSignedProperty("textures", "", ""));

        // Prepare packets
        final ArrayList<PlayerInfoData> playerInfoData = new ArrayList<PlayerInfoData>() {
            {
                new PlayerInfoData(gameProfile, 0, EnumWrappers.NativeGameMode.fromBukkit(player.getGameMode()), WrappedChatComponent.fromText(player.getDisplayName()));
            }
        };

        final WrapperPlayServerPlayerInfo removePlayer = new WrapperPlayServerPlayerInfo();
        removePlayer.setAction(EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);
        removePlayer.setData(playerInfoData);

        final WrapperPlayServerPlayerInfo addPlayer = new WrapperPlayServerPlayerInfo();
        addPlayer.setAction(EnumWrappers.PlayerInfoAction.ADD_PLAYER);
        addPlayer.setData(playerInfoData);

        for (final Player observer : observers) {
            try {
                final Object observerHandle = Player.class.getMethod("getHandle").invoke(observer);
                final Class<?> observerHandleClass = observerHandle.getClass();

                if (observer.equals(player)) {
                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                        final Location location = observer.getLocation();

                        // Prepare packets
                        final WrapperPlayServerRespawn respawn = new WrapperPlayServerRespawn();
                        respawn.setDimension(-1); // change
                        respawn.setDifficulty(EnumWrappers.Difficulty.valueOf(player.getWorld().getDifficulty().name()));
                        respawn.setLevelType(player.getWorld().getWorldType());
                        respawn.setGamemode(EnumWrappers.NativeGameMode.fromBukkit(player.getGameMode()));

                        final WrapperPlayServerPosition position = new WrapperPlayServerPosition();
                        position.setX(location.getX());
                        position.setY(location.getY());
                        position.setZ(location.getZ());
                        position.setYaw(location.getYaw());
                        position.setPitch(location.getPitch());
                        position.setFlags(new HashSet<>());

                        final WrapperPlayServerHeldItemSlot heldItemSlot = new WrapperPlayServerHeldItemSlot();
                        heldItemSlot.setSlot(player.getInventory().getHeldItemSlot());

                        // Update skin for myself
                        try {
                            removePlayer.sendPacket(observer);
                            addPlayer.sendPacket(observer);
                            respawn.sendPacket(observer);
                            observerHandleClass.getMethod("updateAbilities").invoke(observerHandle);
                            position.sendPacket(observer);
                            heldItemSlot.sendPacket(observer);
                            player.getClass().getMethod("updateScaledHealth").invoke(player);
                            player.updateInventory();
                            observerHandleClass.getMethod("triggerHealthUpdate").invoke(observerHandle);

                            if (player.isOp()) {
                                player.setOp(false);
                                player.setOp(true);
                            }
                        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
                            exception.printStackTrace();
                        }
                    });
                } else {
                    final Location location = player.getLocation();

                    // Prepare packets
                    final WrapperPlayServerEntityDestroy entityDestroy = new WrapperPlayServerEntityDestroy();
                    entityDestroy.setEntityIds(new int[] { player.getEntityId() });

                    final WrapperPlayServerNamedEntitySpawn namedEntitySpawn = new WrapperPlayServerNamedEntitySpawn();
                    namedEntitySpawn.setEntityID(player.getEntityId());
                    namedEntitySpawn.setPlayerUUID(player.getUniqueId());
                    namedEntitySpawn.setMetadata(WrappedDataWatcher.getEntityWatcher(player));
                    namedEntitySpawn.setPosition(location.toVector());
                    namedEntitySpawn.setYaw(location.getYaw());
                    namedEntitySpawn.setPitch(location.getPitch());

                    // Update skin for other players
                    removePlayer.sendPacket(observer);
                    addPlayer.sendPacket(observer);
                    entityDestroy.sendPacket(observer);
                    namedEntitySpawn.sendPacket(observer);
                }
            } catch(NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
                exception.printStackTrace();
            }
        }

        callback.done();
    }
}