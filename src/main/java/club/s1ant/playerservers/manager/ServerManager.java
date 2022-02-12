package club.s1ant.playerservers.manager;

import club.s1ant.playerservers.PlayerServers;
import club.s1ant.playerservers.util.MojangAPI;
import com.github.dockerjava.api.model.Container;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ServerManager {

    private final PlayerServers plugin;

    public ServerManager(PlayerServers plugin) {
        this.plugin = plugin;

        loadServerMap();
    }

    private void loadServerMap() {
        plugin.serverMap.reset();
        Set<Map.Entry<UUID, InetSocketAddress>> servers = plugin.docker.getRunningServerContainers().entrySet();
        for(Map.Entry<UUID, InetSocketAddress> entry : servers) {
            registerPlayerServer(entry.getKey(), entry.getValue());
        }
        plugin.logger.info("Loaded {} running servers.", servers.size());
    }

    public void connect(Player p, RegisteredServer server) {
        try {
            p.createConnectionRequest(server).fireAndForget();
        } catch(Exception e) {
            p.sendMessage(plugin.getMessage("cannotconnect"));
        }
    }

    public void registerPlayerServer(UUID uuid, InetSocketAddress address) {
        plugin.serverMap.register(new ServerInfo(plugin.getConfig().getString("servers.velocitynameformat").replace("uuid", uuid.toString()), address),uuid);
    }

    public void unregisterPlayerServer(UUID uuid) {
        plugin.serverMap.unregister(uuid);
    }

    public void createPlayerServer(UUID uuid, CommandSource source, String templateName) {
        if (plugin.docker.isCreated(uuid)) {
            source.sendMessage(plugin.getMessage("alreadycreated"));
            return;
        }

        Optional<String> templateO = verifyTemplate(templateName);
        if(templateO.isEmpty()) {
            source.sendMessage(plugin.getMessage("templatenotfound"));
            return;
        }

        String template = templateO.get();
        source.sendMessage(plugin.getMessage("usingtemplate", template));

        if(plugin.docker.hasVolume(uuid, template)) source.sendMessage(plugin.getMessage("creatingwithexistingdata"));
        else plugin.docker.createVolume(uuid, template);

        plugin.docker.createContainer(uuid, template);
        source.sendMessage(plugin.getMessage("servercreated"));

        if(plugin.getConfig().getBoolean("servers.startoncreate")) {
            Optional<Container> optional = plugin.docker.getContainer(uuid);
            optional.ifPresentOrElse(container -> {
                plugin.docker.startContainer(container);
                source.sendMessage(plugin.getMessage("serverstarting").append(plugin.getMessage("clicktojoin").clickEvent(ClickEvent.runCommand("/myserver"))));
            }, () -> System.out.println("No container present."));
        } else {
            source.sendMessage(plugin.getMessage("clicktostart").clickEvent(ClickEvent.runCommand("/tpsrx start " + MojangAPI.getUsername(uuid))));
        }
    }

    public void startPlayerServer(UUID uuid, CommandSource source) {
        Optional<Container> optional = plugin.docker.getContainer(uuid);

        if(optional.isEmpty()) {
            source.sendMessage(plugin.getMessage("usernoserver"));
            return;
        }

        Container container = optional.get();

        if(plugin.serverMap.hasOnlineServer(uuid) || container.getState().equalsIgnoreCase("running")) {
            source.sendMessage(plugin.getMessage("alreadyonline"));
            return;
        }

        plugin.docker.startContainer(container);
        container = plugin.docker.getContainer(uuid).get();

        InetSocketAddress ip = plugin.docker.getContainerAddress(container);
        registerPlayerServer(uuid, ip);

        Component msg = source instanceof Player ? plugin.getMessage("serverstarting").append(plugin.getMessage("clicktojoin")).clickEvent(ClickEvent.runCommand("/visit " + MojangAPI.getUsername(uuid))) : Component.text("Server starting.").color(NamedTextColor.GREEN);
        source.sendMessage(msg);
    }

    public void stopPlayerServer(UUID uuid, CommandSource source) {
        Optional<Container> optional = plugin.docker.getContainer(uuid);

        if(optional.isEmpty()) {
            source.sendMessage(plugin.getMessage("usernoserver"));
            return;
        }

        Container container = optional.get();

        if(!plugin.serverMap.hasOnlineServer(uuid) || !container.getState().equalsIgnoreCase("running")) {
            source.sendMessage(plugin.getMessage("alreadyoffline"));
            return;
        }

        kickAll(uuid, source);

        plugin.docker.stopContainer(container);
        plugin.serverManager.unregisterPlayerServer(uuid);

        source.sendMessage(plugin.getMessage("serverstopped"));
    }

    public void deletePlayerServer(UUID uuid, CommandSource source) {
        Optional<Container> optional = plugin.docker.getContainer(uuid);

        if(optional.isEmpty()) {
            source.sendMessage(plugin.getMessage("usernoserver"));
            return;
        }

        Container container = optional.get();
        source.sendMessage(plugin.getMessage("deletingserver"));
        //If server is online, stop it first.
        if(plugin.serverMap.hasOnlineServer(uuid, container)) {
            kickAll(uuid, source);
            plugin.serverManager.unregisterPlayerServer(uuid);
            plugin.docker.stopContainer(container);
        }

        plugin.docker.deleteContainer(container);

        source.sendMessage(plugin.getMessage("deletedwithoutfiles"));
    }

    public void deletePlayerServerFiles(UUID uuid, CommandSource source, String templateName) {
        if(plugin.docker.isCreated(uuid)) {
            source.sendMessage(plugin.getMessage("activeservernodelete"));
            return;
        }

        Optional<String> templateO = verifyTemplate(templateName);
        if(templateO.isEmpty()) {
            source.sendMessage(plugin.getMessage("templatenotfound"));
            return;
        }
        String template = templateO.get();

        if(!plugin.docker.hasVolume(uuid, template)) {
            source.sendMessage(plugin.getMessage("notemplate"));
            return;
        }

        plugin.docker.deleteVolume(uuid, template);

        source.sendMessage(plugin.getMessage("deletedwithfiles", template));
    }

    public void playerServerInfo(UUID uuid, CommandSource source) {
        Optional<Container> opt = plugin.docker.getContainer(uuid);
        if(opt.isEmpty()) {
            source.sendMessage(plugin.getMessage("usernoserver"));
            Component msg = Component.text("Data Volumes:").color(NamedTextColor.DARK_GRAY);
            for(String template : templateDataVolumes(uuid)) {
                msg = msg.append(Component.text("\n - ").color(NamedTextColor.GRAY).append(Component.text(template).color(NamedTextColor.GREEN)));
            }
            source.sendMessage(msg);
            return;
        }
        Container container = opt.get();
        source.sendMessage(Component.text(MojangAPI.getUsername(uuid)).color(NamedTextColor.BLUE).append(Component.text(" - ").color(NamedTextColor.DARK_GRAY)).append(Component.text(container.getState()).color(container.getState().equals("running") ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
        if(plugin.serverMap.hasOnlineServer(uuid)) {
            RegisteredServer server = plugin.serverMap.getServer(uuid);
            source.sendMessage(Component.text("Players Online: ").color(NamedTextColor.DARK_GRAY).append(Component.text(server.getPlayersConnected().size()).color(NamedTextColor.GREEN)));
            source.sendMessage(Component.text("IP Address: ").color(NamedTextColor.DARK_GRAY).append(Component.text(server.getServerInfo().getAddress().getHostString()).color(NamedTextColor.GREEN)));
        }
        source.sendMessage(Component.text("Status: ").color(NamedTextColor.DARK_GRAY).append(Component.text(container.getStatus()).color(NamedTextColor.GREEN)));
        source.sendMessage(Component.text("Template: ").color(NamedTextColor.DARK_GRAY).append(Component.text(container.getImage()).color(NamedTextColor.GREEN)));
        if(container.getState().equalsIgnoreCase("running")) {
            InetSocketAddress addr = plugin.docker.getContainerAddress(container);
            source.sendMessage(Component.text("Connection: ").color(NamedTextColor.DARK_GRAY).append(Component.text(addr.getHostString() + ":" + addr.getPort()).color(NamedTextColor.GREEN)));
        }
        source.sendMessage(Component.text("Created: ").color(NamedTextColor.DARK_GRAY).append(Component.text(DateTimeFormatter.ofPattern("EEEE LLLL d, u h:mm a zzz").withLocale(Locale.ENGLISH).withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(container.getCreated()))).color(NamedTextColor.GREEN)));
        source.sendMessage(Component.text("UUID: ").color(NamedTextColor.DARK_GRAY).append(Component.text(uuid.toString()).color(NamedTextColor.GREEN)));
        source.sendMessage(Component.text("Container Id: ").color(NamedTextColor.DARK_GRAY).append(Component.text(container.getId()).color(NamedTextColor.GREEN)));

        Component msg = Component.text("Data Volumes:").color(NamedTextColor.DARK_GRAY);
        for(String template : templateDataVolumes(uuid)) {
            msg = msg.append(Component.text("\n - ").color(NamedTextColor.GRAY).append(Component.text(template).color(NamedTextColor.GREEN)));
        }
        source.sendMessage(msg);
    }

    private void kickAll(UUID uuid, CommandSource source) {
        //send all players to random fallback server specified in config.
        List<String> serverNames = plugin.getConfig().getList("servers.fallbackservers");

        List<RegisteredServer> servers = serverNames.stream().map(serverName -> {
            Optional<RegisteredServer> fallbackServer = plugin.proxy.getServer(serverName);
            if (fallbackServer.isEmpty()) {
                plugin.logger.warn("Fallback server, " + serverName + ", does not seem to be online.");
                return null;
            }
            else return fallbackServer.get();
        }).filter(Objects::nonNull).toList();

        if(servers.isEmpty()) {
            source.sendMessage(plugin.getMessage("nofallbackservers").decorate(TextDecoration.BOLD));
            return;
        }

        source.sendMessage(plugin.getMessage("sendingtofallback", String.valueOf(servers.size())));
        RegisteredServer server = plugin.serverMap.getServer(uuid);

        //Have to do it like this to use the index to evenly distribute players among all fallback servers.
        List<Player> players = server.getPlayersConnected().stream().toList();
        for(int i=0;i<players.size();i++) {
            Player player = players.get(i);
            player.sendMessage(plugin.getMessage("serverclosed"));
            player.createConnectionRequest(servers.get(i % servers.size())).fireAndForget();
        }
    }

    private List<String> templateDataVolumes(UUID uuid) {
        return plugin.docker.listVolumes(uuid).stream().map(vol -> vol.replace("vol-" + uuid + "-", "")).toList();
    }

    private Optional<String> verifyTemplate(String input) {
        List<String> templates = plugin.getTemplateImages();

        if (input == null) return Optional.of(plugin.getConfig().getString("docker.defaultimage")); //If input is blank then they want the default.
        else if(templates.contains(input)) {
            return Optional.of(input);
        } else if(templates.stream().map(String::toLowerCase).toList().contains(input.toLowerCase())) {
            Optional<String> os = templates.stream().filter(s -> s.equalsIgnoreCase(input)).findFirst();
            if(os.isEmpty()) return Optional.empty();
            else return os;
        }
        return Optional.empty();
    }

    public boolean isTemplate(String templateName, boolean caseSensitive) {
        if(caseSensitive) return plugin.getTemplateImages().contains(templateName);
        else return plugin.getTemplateImages().stream().map(String::toLowerCase).toList().contains(templateName.toLowerCase());
    }
}
