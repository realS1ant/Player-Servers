package club.s1ant.playerservers.command;

import club.s1ant.playerservers.PlayerServers;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;

public class MyServer implements SimpleCommand {

    private final PlayerServers plugin;

    public MyServer(PlayerServers plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if(!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Players only.").color(NamedTextColor.RED));
            return;
        }

        if(plugin.serverMap.hasOnlineServer(player.getUniqueId())) {
            Optional<ServerConnection> optionalConnection = player.getCurrentServer();
            if(player.getCurrentServer().isPresent() && optionalConnection.get().getServerInfo().getName().equals(plugin.serverMap.getServer(player.getUniqueId()).getServerInfo().getName())) {
                player.sendMessage(plugin.getMessage("myserveralreadyconnected"));
                return;
            }
            player.sendMessage(plugin.getMessage("connecting"));
            plugin.serverManager.connect(player, plugin.serverMap.getServer(player.getUniqueId()));
        } else {
            if(!plugin.docker.isCreated(player.getUniqueId())) {
                if(!plugin.getConfig().getBoolean("servers.createbymyserver")) {
                    player.sendMessage(plugin.getMessage("myservernoserver"));
                    return;
                }

                StringBuilder templatesStringSb = new StringBuilder();
                plugin.getTemplateImages().forEach((template) -> templatesStringSb.append(template).append(", "));
                String templatesString = templatesStringSb.substring(0, templatesStringSb.lastIndexOf(","));
                if(invocation.arguments().length < 1) {
                    player.sendMessage(plugin.getMessage("myservernoserver", templatesString));
                } else {
                    String arg = invocation.arguments()[0];
                    if(plugin.serverManager.isTemplate(arg, false)) {
                        player.sendMessage(plugin.getMessage("myservercreatingserver", arg));
                        plugin.serverManager.createPlayerServer(player.getUniqueId(), player, arg);
                    } else {
                        player.sendMessage(plugin.getMessage("myserverinvalidtemplate", arg, templatesString));
                    }
                }
            } else {
                player.sendMessage(plugin.getMessage("myserverstarting"));
                plugin.serverManager.startPlayerServer(player.getUniqueId(), player);
            }
        }
    }
}
