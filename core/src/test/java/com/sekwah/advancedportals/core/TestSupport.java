package com.sekwah.advancedportals.core;

import com.sekwah.advancedportals.core.connector.containers.CommandSenderContainer;
import com.sekwah.advancedportals.core.connector.containers.GameMode;
import com.sekwah.advancedportals.core.connector.containers.PlayerContainer;
import com.sekwah.advancedportals.core.connector.containers.WorldContainer;
import com.sekwah.advancedportals.core.portal.AdvancedPortal;
import com.sekwah.advancedportals.core.registry.TagRegistry;
import com.sekwah.advancedportals.core.repository.ConfigRepository;
import com.sekwah.advancedportals.core.repository.IPlayerDataRepository;
import com.sekwah.advancedportals.core.repository.IPortalRepository;
import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import com.sekwah.advancedportals.core.serializeddata.PlayerLocation;
import com.sekwah.advancedportals.core.serializeddata.Vector;
import com.sekwah.advancedportals.core.services.PlayerDataServices;
import com.sekwah.advancedportals.core.services.PortalServices;
import com.sekwah.advancedportals.core.warphandler.Tag;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * Tiny hand written fakes so the core logic can be tested without a server.
 * Interfaces are stubbed with dynamic proxies that return "empty" values.
 */
public final class TestSupport {
    private TestSupport() {}

    public static void set(Object target, String field, Object value) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field f = clazz.getDeclaredField(field);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new NoSuchFieldException(field);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object target, String field) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field f = clazz.getDeclaredField(field);
                    f.setAccessible(true);
                    return (T) f.get(target);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new NoSuchFieldException(field);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == PlayerContainer[].class) return new PlayerContainer[0];
        if (type == List.class) return new ArrayList<>();
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == String.class) return "";
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> type,
                              Map<String, java.util.function.Function<Object[], Object>> answers) {
        InvocationHandler handler = (proxy, method, args) -> {
            var answer = answers.get(method.getName());
            if (answer != null) {
                return answer.apply(args);
            }
            if (method.getName().equals("toString")) return type.getSimpleName();
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            return defaultValue(method.getReturnType());
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    public static ConfigRepository config(Map<String, Object> values) {
        Map<String, java.util.function.Function<Object[], Object>> answers = new HashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            answers.put(entry.getKey(), a -> entry.getValue());
        }
        return proxy(ConfigRepository.class, answers);
    }

    /** In memory portal repository. */
    public static class MemoryPortalRepository {
        public final Map<String, AdvancedPortal> stored = new LinkedHashMap<>();

        public IPortalRepository asRepository() {
            Map<String, java.util.function.Function<Object[], Object>> a = new HashMap<>();
            a.put("getAllNames", args -> new ArrayList<>(stored.keySet()));
            a.put("get", args -> stored.get((String) args[0]));
            a.put("containsKey", args -> stored.containsKey((String) args[0]));
            a.put("save", args -> {
                stored.put((String) args[0], (AdvancedPortal) args[1]);
                return true;
            });
            a.put("delete", args -> stored.remove((String) args[0]) != null);
            return proxy(IPortalRepository.class, a);
        }
    }

    public static PlayerDataServices playerDataServices(ConfigRepository config) {
        PlayerDataServices services = new PlayerDataServices();
        set(services, "configRepository", config);
        set(services, "tempDataRepository",
            proxy(IPlayerDataRepository.class, new HashMap<>()));
        return services;
    }

    public static TagRegistry tagRegistry() {
        return new TagRegistry();
    }

    public static void registerActivation(TagRegistry registry, Tag tag) {
        Map<String, Tag> tagMap = get(registry, "tagMap");
        tagMap.put(tag.getName(), tag);
        List<Tag> tags = get(registry, "tags");
        tags.add(tag);
        if (tag instanceof Tag.Activation) {
            Map<String, Tag.Activation> map = get(registry, "activationTags");
            map.put(tag.getName(), (Tag.Activation) tag);
        }
        if (tag instanceof Tag.Creation) {
            Map<String, Tag.Creation> map = get(registry, "creationTags");
            map.put(tag.getName(), (Tag.Creation) tag);
        }
    }

    public static AdvancedPortal portal(String name, String world, int x1, int y1,
                                        int z1, int x2, int y2, int z2,
                                        TagRegistry registry,
                                        PlayerDataServices playerData,
                                        ConfigRepository config,
                                        String... triggerBlocks) {
        AdvancedPortal portal = new AdvancedPortal(
            new BlockLocation(world, x1, y1, z1),
            new BlockLocation(world, x2, y2, z2), registry, playerData);
        set(portal, "configRepository", config);
        portal.setArgValues("name", new String[] {name});
        portal.setArgValues("triggerblock", triggerBlocks);
        return portal;
    }

    public static PortalServices portalServices(MemoryPortalRepository repo,
                                                ConfigRepository config,
                                                PlayerDataServices playerData,
                                                TagRegistry registry) {
        PortalServices services = new PortalServices();
        set(services, "portalRepository", repo.asRepository());
        set(services, "configRepository", config);
        set(services, "playerDataServices", playerData);
        set(services, "tagRegistry", registry);
        return services;
    }

    /** What the fake player has been told / done. */
    public static class FakePlayer {
        public final UUID uuid = UUID.randomUUID();
        public final List<String> messages = new ArrayList<>();
        public Vector velocity;
        public double height = 1.8;
        public double width = 0.6;
        /** Set to make showEditor succeed and record the form */
        public boolean supportsEditor = false;
        public com.sekwah.advancedportals.core.connector.containers.EditorForm form;
        public final List<BlockLocation[]> highlighted = new ArrayList<>();
        public boolean supportsHighlight = false;
        public GameMode gameMode = GameMode.SURVIVAL;
        public String name = "Tester";
        public boolean op = true;
        public java.util.function.Predicate<String> permissions = p -> true;
        public PlayerLocation location =
            new PlayerLocation("world", 0, 64, 0, 0, 0);
        public final Map<BlockLocation, String> blocks = new HashMap<>();
        public String blockAt(BlockLocation loc) {
            for (Map.Entry<BlockLocation, String> e : blocks.entrySet()) {
                if (e.getKey().equals(loc)) return e.getValue();
            }
            return "AIR";
        }

        public PlayerContainer container() {
            Map<String, java.util.function.Function<Object[], Object>> a = new HashMap<>();
            a.put("getUUID", args -> uuid);
            a.put("getHeight", args -> height);
            a.put("getWidth", args -> width);
            a.put("getWorldName", args -> location.getWorldName());
            a.put("showEditor", args -> {
                form = (com.sekwah.advancedportals.core.connector.containers.EditorForm) args[0];
                return supportsEditor;
            });
            a.put("highlightRegion", args -> {
                highlighted.add(new BlockLocation[] {(BlockLocation) args[0], (BlockLocation) args[1]});
                return supportsHighlight;
            });
            a.put("getGameMode", args -> gameMode);
            a.put("getLoc", args -> location);
            a.put("getName", args -> name);
            a.put("isOp", args -> op);
            a.put("hasPermission", args -> permissions.test((String) args[0]));
            a.put("sendMessage", args -> {
                messages.add((String) args[0]);
                return null;
            });
            a.put("setVelocity", args -> {
                velocity = (Vector) args[0];
                return null;
            });
            a.put("getWorld", args -> proxy(WorldContainer.class, Map.of(
                "getBlock", w -> blockAt((BlockLocation) w[0]))));
            return proxy(PlayerContainer.class, a);
        }
    }

    public static class FakeSender {
        public List<String> messages = new ArrayList<>();
        public FakePlayer player;
        public PlayerContainer playerContainer;

        public FakeSender() {}

        public FakeSender(FakePlayer player) {
            this.player = player;
            this.playerContainer = player.container();
            // Everything the plugin tells the player (directly or through the
            // command sender) ends up in the same list
            this.messages = player.messages;
        }

        public String all() {
            return String.join("\n", messages);
        }

        public CommandSenderContainer container() {
            Map<String, java.util.function.Function<Object[], Object>> a = new HashMap<>();
            a.put("sendMessage", args -> {
                messages.add((String) args[0]);
                return null;
            });
            a.put("getPlayerContainer", args -> playerContainer);
            a.put("isOp", args -> player == null || player.op);
            a.put("hasPermission", args -> player == null || player.permissions.test((String) args[0]));
            return proxy(CommandSenderContainer.class, a);
        }
    }
}
