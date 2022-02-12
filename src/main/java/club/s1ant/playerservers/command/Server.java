package club.s1ant.playerservers.command;

import club.s1ant.playerservers.util.MojangAPI;
import club.s1ant.playerservers.PlayerServers;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class Server implements SimpleCommand {

    private final PlayerServers plugin;

    public Server(PlayerServers plugin){
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if(invocation.arguments().length == 0) {
            usage(invocation);
            return;
        }

        String[] args = invocation.arguments();

        if(args[0].equalsIgnoreCase("list")) {
            invocation.source().sendMessage(Component.text("All private servers:").color(NamedTextColor.GREEN));
            plugin.docker.getAllServerContainers().forEach((name, tc) -> {
                invocation.source().sendMessage(Component.text("- ").color(NamedTextColor.GRAY).append(Component.text(""+name).color(tc)));
            });
            return;
        }

        if(args.length < 2) {
            if(!suggestCurrentServer(invocation)) invocation.source().sendMessage(plugin.getMessage("suggestserver", args[0]));
            return;
        }

        Optional<Player> player = plugin.proxy.getPlayer(args[1]);
        UUID uuid;
        if(player.isPresent()) {
            uuid = player.get().getUniqueId();
        } else {
            uuid = MojangAPI.getUUID(args[1]);
        }

        if(uuid == null) {
            invocation.source().sendMessage(plugin.getMessage("playernotfound"));
            return;
        }


        switch(args[0].toLowerCase()) {
            case "create":
                //tpsrxserver create <username> [template]
                plugin.serverManager.createPlayerServer(uuid, invocation.source(), args.length >= 3 ? args[2] : null);
                break;
            case "delete":
                plugin.serverManager.deletePlayerServer(uuid, invocation.source());
                break;
            case "start":
                plugin.serverManager.startPlayerServer(uuid, invocation.source());
                break;
            case "stop":
                plugin.serverManager.stopPlayerServer(uuid, invocation.source());
                break;
            case "deletefiles":
                if(args.length < 3) invocation.source().sendMessage(plugin.getMessage("deletefilesusage"));
                else plugin.serverManager.deletePlayerServerFiles(uuid, invocation.source(), args[2]);
                break;
            case "info":
                plugin.serverManager.playerServerInfo(uuid, invocation.source());
                break;
            default:
                usage(invocation);
        }

    }

    @Override
    public List<String> suggest(Invocation invocation) {
        final List<String> subCommands = Arrays.asList("create", "delete", "start", "stop", "deletefiles", "list", "info");

        if(invocation.arguments().length == 0) {
            return subCommands;
        }
        if(invocation.arguments().length == 1) {
            return subCommands.stream()
                    .filter(cmd -> cmd.regionMatches(true, 0, invocation.arguments()[0], 0,
                            invocation.arguments()[0].length()))
                    .collect(Collectors.toList());
        }
        //always player name
        if(invocation.arguments().length == 2) {
            return plugin.proxy.getAllPlayers().stream().map(Player::getUsername).collect(Collectors.toList());
        }

        if(invocation.arguments().length == 3 && (invocation.arguments()[0].equalsIgnoreCase("create") || invocation.arguments()[0].equalsIgnoreCase("deletefiles"))) {
            return plugin.getTemplateImages();
        }
        return null;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        if(plugin.getConfig().getString("managecommandpermission") == null || plugin.getConfig().getString("managecommandpermission").equalsIgnoreCase("")) return true;
        return invocation.source().hasPermission(plugin.getConfig().getString("managecommandpermission"));
    }

    private void usage(Invocation invocation) {
        invocation.source().sendMessage(Component.text(
    "/tpsrxserver list\n" +
            "/tpsrxserver create <player> <template>\n" +
            "/tpsrxserver delete <player>\n" +
            "/tpsrxserver start <player>\n" +
            "/tpsrxserver stop <player>\n" +
            "/tpsrxserver deletefiles <player> <template>\n"
        ).color(NamedTextColor.RED));
    }

    //method to suggest the current server in command
    private boolean suggestCurrentServer(Invocation in) {
        if(in.source() instanceof Player) {
            Player p = ((Player) in.source());
            Optional<ServerConnection> current = p.getCurrentServer();
            if(current.isPresent()) {
                if(plugin.serverMap.isServer(current.get())){
                    UUID uuid = plugin.serverMap.getUUID(current.get().getServer());
                    String command = "/tpsrxserver " + in.arguments()[0].toLowerCase() + " " + MojangAPI.getUsername(uuid);
                    p.sendMessage(
//                            Component.text("Did you mean the current server? " + command)
//                                    .color(NamedTextColor.DARK_BLUE)
                        plugin.getMessage("suggestcurrentserver", command)
                            .clickEvent(ClickEvent.suggestCommand(command))
                    );
                    return true;
                }
            }
        }
        return false;
    }
}
