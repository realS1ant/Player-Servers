package club.s1ant.playerservers;

import com.github.dockerjava.api.model.Container;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerMap {

    private final PlayerServers plugin;
    private final Map<UUID, RegisteredServer> servers = new ConcurrentHashMap<>(); //no runtime exceptions...

    public ServerMap(PlayerServers plugin) {
        this.plugin = plugin;
    }

    public RegisteredServer getServer(@NotNull UUID uuid) {
        return servers.get(uuid);
    }

    public UUID getUUID(@NotNull RegisteredServer server) {
        return servers.entrySet().stream().filter(entry -> entry.getValue().equals(server)).findFirst().get().getKey();
    }

    public void register(@NotNull ServerInfo serverInfo, @NotNull UUID uuid) {
        //keep out of /server
        servers.put(uuid, plugin.proxy.createRawRegisteredServer(serverInfo));
    }

    public void unregister(@NotNull UUID uuid) {
        servers.remove(uuid);
    }

    public void reset() {
        servers.clear();
    }

    public void closeOffline() {
        boolean log = plugin.getConfig().getBoolean("servers.logstopped");
        if(log) plugin.logger.info("Stopping all private servers with 0 players.");
        servers.forEach((uuid, server) -> {
            if(server.getPlayersConnected().size() < 1) {
                UUID playerUUID = getUUID(server);
                if(log) plugin.logger.info("Stopping private server server " + server.getServerInfo().getName() + " (" + playerUUID + ").");
                Optional<Container> opt = plugin.docker.getContainer(uuid);
                if(opt.isEmpty()) {
                    plugin.logger.error("No container for private server with 0 people: "
                            + server.getServerInfo().getName() + " (" + playerUUID + ").");
                    return;
                } else {
                    plugin.docker.stopContainer(opt.get());
                    unregister(playerUUID);
                };
            }
        });
    }

    public boolean isServer(ServerConnection conn) {
        return servers.containsValue(conn.getServer());
    }

    public boolean hasOnlineServer(@NotNull UUID uuid) {
        if(servers.containsKey(uuid)) return true;
        Optional<Container> container = plugin.docker.getContainer(uuid);
        if(container.isPresent()) {
            if(container.get().getState().equalsIgnoreCase("running")) {
                plugin.serverManager.registerPlayerServer(uuid, plugin.docker.getContainerAddress(container.get()));
                return true;
            }
        }
        //Not in server map, and no container with that uuid so no online server.
        return false;
    }

    //Another one with container passed in just to save bandwith
    public boolean hasOnlineServer(@NotNull UUID uuid, @NotNull Container container) {
        if(servers.containsKey(uuid)) return true;
        if(container.getState().equalsIgnoreCase("running")) {
            plugin.serverManager.registerPlayerServer(uuid, plugin.docker.getContainerAddress(container));
            return true;
        }
        //Not in server map, and no container with that uuid so no online server.
        return false;
    }

    public List<RegisteredServer> getServers() {
        return servers.values().stream().toList();
    }
}
