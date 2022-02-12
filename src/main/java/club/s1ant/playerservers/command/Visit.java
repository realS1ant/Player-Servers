package club.s1ant.playerservers.command;

import club.s1ant.playerservers.PlayerServers;
import club.s1ant.playerservers.util.MojangAPI;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class Visit implements SimpleCommand {

    private final PlayerServers plugin;

    public Visit(PlayerServers plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if(!(invocation.source() instanceof Player)) {
            invocation.source().sendMessage(Component.text("Players only.").color(NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();

        if(invocation.arguments().length == 0) {
            invocation.source().sendMessage(plugin.getMessage("visitusage"));
            return;
        }

        Optional<Player> player = plugin.proxy.getPlayer(args[0]);
        UUID uuid;
        if(player.isPresent()) {
            uuid = player.get().getUniqueId();
        } else {
            uuid = MojangAPI.getUUID(args[0]);
        }

        if(!plugin.docker.isCreated(uuid)) {
            invocation.source().sendMessage(plugin.getMessage("usernoserver", args[0]));
            return;
        }

        if(!plugin.serverMap.hasOnlineServer(uuid)) {
            invocation.source().sendMessage(plugin.getMessage("visitnotonline"));
            return;
        }

        //server is created and online
        plugin.serverManager.connect((Player) invocation.source(), plugin.serverMap.getServer(uuid));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return plugin.proxy.getAllPlayers().stream().map(Player::getUsername).collect(Collectors.toList());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return SimpleCommand.super.hasPermission(invocation);
    }
}
