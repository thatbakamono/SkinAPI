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
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class SkinAPI {
    private final MineskinClient client = new MineskinClient();
    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;

    private HashMap<UUID, ReflectionCache> cache = new HashMap<>();

    private Method getHandleMethod;
    private Method updateScaledHealthMethod;

    public SkinAPI(JavaPlugin plugin) {
        this.plugin = plugin;
        protocolManager = ProtocolLibrary.getProtocolManager();

        try {
            getHandleMethod = Player.class.getMethod("getHandle");
            updateScaledHealthMethod = Player.class.getMethod("updateScaledHealth");
        } catch (NoSuchMethodException exception) {
            throw new RuntimeException("This should not happen, maybe you are using an unsupported version?");
        }
    }

    public void uploadSkin(Player player, String url, SkinUploadCallback callback) throws MalformedURLException, IOException {
        URL skinUrl = new URL(url);
        File skinFile = new File(plugin.getDataFolder(), "skins/" + player.getUniqueId() + ".skin");

        FileUtils.copyURLToFile(skinUrl, skinFile);
        client.generateUpload(skinFile, skin -> {
            skinFile.delete();
            callback.done(skin.id);
        });
    }

    public void getSkin(Player player, SkinUploadCallback callback) {
        getSkin(player.getUniqueId(), callback);
    }

    public void getSkin(UUID uniqueId, SkinUploadCallback callback) {
        client.generateUser(uniqueId, skin -> {
            callback.done(skin.id);
        });
    }

    public void setSkin(Player player, int id, SkinSetCallback callback) {
        setSkin(Bukkit.getOnlinePlayers(), player, id, callback);
    }

    public void setSkin(Collection<? extends Player> observers, Player player, int id, SkinSetCallback callback) {
        client.getSkin(id, skin -> {
            // Update skin texture
            WrappedGameProfile gameProfile = WrappedGameProfile.fromPlayer(player);
            gameProfile.getProperties().clear();
            gameProfile.getProperties().put("textures", new WrappedSignedProperty("textures", skin.data.texture.value, skin.data.texture.signature));

            // Prepare packets
            final ArrayList<PlayerInfoData> playerInfoData = new ArrayList<PlayerInfoData>() {
                {
                    new PlayerInfoData(gameProfile, 0, EnumWrappers.NativeGameMode.fromBukkit(player.getGameMode()), WrappedChatComponent.fromText(player.getDisplayName()));
                }
            };

            WrapperPlayServerPlayerInfo removePlayer = createPlayServerPlayerInfoPacket(EnumWrappers.PlayerInfoAction.REMOVE_PLAYER, playerInfoData);
            WrapperPlayServerPlayerInfo addPlayer = createPlayServerPlayerInfoPacket(EnumWrappers.PlayerInfoAction.ADD_PLAYER, playerInfoData);

            for (Player observer : observers) {
                try {
                    ReflectionCache observerCache;

                    if (!cache.containsKey(observer.getUniqueId())) {
                        observerCache = new ReflectionCache();
                        observerCache.setHandle(getHandleMethod.invoke(observer));
                        observerCache.setHandleClass(observerCache.getHandle().getClass());
                        observerCache.setUpdateAbilitiesMethod(observerCache.getHandleClass().getMethod("updateAbilities"));

                        cache.put(observer.getUniqueId(), observerCache);
                    } else {
                        observerCache = cache.get(observer.getUniqueId());
                    }

                    if (observer.equals(player)) {
                        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                            Location location = observer.getLocation();

                            // Prepare packets
                            WrapperPlayServerRespawn respawn = createPlayServerRespawnPacket(player);
                            WrapperPlayServerPosition position = createPlayServerPositionPacket(location);
                            WrapperPlayServerHeldItemSlot heldItemSlot = new WrapperPlayServerHeldItemSlot();
                            heldItemSlot.setSlot(player.getInventory().getHeldItemSlot());

                            // Update skin for myself
                            try {
                                removePlayer.sendPacket(observer);
                                addPlayer.sendPacket(observer);
                                respawn.sendPacket(observer);
                                observerCache.getUpdateAbilitiesMethod().invoke(observerCache.getHandle());
                                position.sendPacket(observer);
                                heldItemSlot.sendPacket(observer);
                                updateScaledHealthMethod.invoke(player);
                                player.updateInventory();
                                observerCache.getTriggerHealthUpdateMethod().invoke(observerCache.getHandle());

                                if (player.isOp()) {
                                    player.setOp(false);
                                    player.setOp(true);
                                }
                            } catch (IllegalAccessException | InvocationTargetException exception) {
                                throw new RuntimeException("This should not happen, maybe you are using an unsupported version?");
                            }
                        });
                    } else {
                        Location location = player.getLocation();

                        // Prepare packets
                        WrapperPlayServerEntityDestroy entityDestroy = new WrapperPlayServerEntityDestroy();
                        entityDestroy.setEntityIds(new int[] { player.getEntityId() });
                        WrapperPlayServerNamedEntitySpawn namedEntitySpawn = createPlayServerNamedEntitySpawnPacket(player);

                        // Update skin for other players
                        removePlayer.sendPacket(observer);
                        addPlayer.sendPacket(observer);
                        entityDestroy.sendPacket(observer);
                        namedEntitySpawn.sendPacket(observer);
                    }
                } catch(NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
                    throw new RuntimeException("This should not happen, maybe you are using an unsupported version?");
                }
            }

            callback.done();
        });
    }

    private WrapperPlayServerPlayerInfo createPlayServerPlayerInfoPacket(EnumWrappers.PlayerInfoAction playerInfoAction, List<PlayerInfoData> playerInfoData) {
        WrapperPlayServerPlayerInfo playerInfo = new WrapperPlayServerPlayerInfo();

        playerInfo.setAction(playerInfoAction);
        playerInfo.setData(playerInfoData);

        return playerInfo;
    }

    private WrapperPlayServerRespawn createPlayServerRespawnPacket(Player player) {
        WrapperPlayServerRespawn respawn = new WrapperPlayServerRespawn();

        respawn.setDimension(player.getWorld().getEnvironment().getId());
        respawn.setDifficulty(EnumWrappers.Difficulty.valueOf(player.getWorld().getDifficulty().name()));
        respawn.setLevelType(player.getWorld().getWorldType());
        respawn.setGamemode(EnumWrappers.NativeGameMode.fromBukkit(player.getGameMode()));

        return respawn;
    }

    private WrapperPlayServerPosition createPlayServerPositionPacket(Location location) {
        WrapperPlayServerPosition position = new WrapperPlayServerPosition();

        position.setX(location.getX());
        position.setY(location.getY());
        position.setZ(location.getZ());
        position.setYaw(location.getYaw());
        position.setPitch(location.getPitch());
        position.setFlags(new HashSet<>());

        return position;
    }

    private WrapperPlayServerNamedEntitySpawn createPlayServerNamedEntitySpawnPacket(Player player) {
        Location location = player.getLocation();
        WrapperPlayServerNamedEntitySpawn namedEntitySpawn = new WrapperPlayServerNamedEntitySpawn();

        namedEntitySpawn.setEntityID(player.getEntityId());
        namedEntitySpawn.setPlayerUUID(player.getUniqueId());
        namedEntitySpawn.setMetadata(WrappedDataWatcher.getEntityWatcher(player));
        namedEntitySpawn.setPosition(location.toVector());
        namedEntitySpawn.setYaw(location.getYaw());
        namedEntitySpawn.setPitch(location.getPitch());

        return namedEntitySpawn;
    }
}