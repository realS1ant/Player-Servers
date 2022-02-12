package club.s1ant.playerservers;

import club.s1ant.playerservers.command.MyServer;
import club.s1ant.playerservers.command.Server;
import club.s1ant.playerservers.command.Visit;
import club.s1ant.playerservers.manager.ServerManager;
import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Plugin(id = "playerservers", authors = {"S1ant"}, name = "PlayerServers", description = "Create private servers for your network with Docker.", version = "1.0")
public class PlayerServers {

    public final ProxyServer proxy;
    public final Logger logger;
    public Docker docker;
    public ServerMap serverMap;
    public ServerManager serverManager;
    private final Path dataPath;
    private Toml config;
    private Toml messages;
    private Toml defaultMessages;
    private List<String> templateImages;

    @Inject
    public PlayerServers(ProxyServer proxy, Logger logger, @DataDirectory Path dataPath) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataPath = dataPath;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent e) {
        loadConfigurations();

        docker = new Docker(this);
        docker.createDockerConnection();

        templateImages = setupImageList();
        serverMap = new ServerMap(this);
        serverManager = new ServerManager(this);

        proxy.getCommandManager().register("tpsrxserver", new Server(this));
        proxy.getCommandManager().register("visit", new Visit(this));
        proxy.getCommandManager().register("myserver", new MyServer(this));

        this.proxy.getScheduler().buildTask(this, serverMap::closeOffline)
                .repeat(getConfig().getLong("servers.timeoutinterval"), TimeUnit.MINUTES).schedule();
    }

    @Subscribe
    public void onProxyDie(ProxyShutdownEvent e) {
        docker.closeConnection();
    }

    private void loadConfigurations() {
        File dir = dataPath.toFile();
        dir.mkdirs();
        File configFile = new File(dir, "config.toml");
        if(!configFile.exists()) {
            try {
                InputStream stream = getClass().getClassLoader().getResourceAsStream("config.toml");
                if (stream != null) Files.copy(stream, configFile.toPath());
                else configFile.createNewFile();
            } catch(IOException e) {
                e.printStackTrace();
                logger.error("Error while copying default configuration file.");
            }
        }
        config = new Toml().read(configFile);

        File messagesFile = new File(dir, "messages.toml");
        if(!messagesFile.exists()) {
            try {
                InputStream stream = getClass().getClassLoader().getResourceAsStream("messages.toml");
                if (stream != null) Files.copy(stream, messagesFile.toPath());
                else messagesFile.createNewFile();
            } catch(IOException e) {
                e.printStackTrace();
                logger.error("Error while copying default configuration file.");
            }
        }
        messages = new Toml().read(messagesFile);
        defaultMessages = new Toml().read(getClass().getClassLoader().getResourceAsStream("messages.toml"));
    }

    public Toml getConfig() {
        return config;
    }

    public Component getMessage(String key, String... variables) {
       Component message;
       String text = messages.getString(key);
       String defaultText = defaultMessages.getString(key);
       if(text == null) text = defaultText;
       if(defaultText == null) {
           text = messages.getString("nomessage");
           variables = new String[] {key};
           key = "nomessage";
       }

       for(String var : variables) {
           if(text.contains("{}")) text = text.replace("{}", var);
       }

       message = Component.text(text);
       if(messages.getString(key + "-color") != null || defaultMessages.getString(key + "-color") != null) message = message.color(getTextColor(key));
       return message;
    }

    private TextColor getTextColor(String key) {
        String color = messages.getString(key + "-color");
        String defaultColor = defaultMessages.getString(key + "-color");

        if(color == null) color = defaultColor;
        if(defaultColor == null) return NamedTextColor.WHITE; //this will never happen... line 125 checks.

        if(color.startsWith("#")) return TextColor.fromHexString(color);
        else {
            return NamedTextColor.NAMES.value(color.toLowerCase());
        }

    }


    //must be in here because used in a method run by servermanager constructor so can't be in there.
    private List<String> setupImageList() {
        List<String> dockerImages = this.docker.getAllImageNames();
        List<String> images = this.getConfig().getList("docker.images");
        if(!images.contains(this.getConfig().getString("docker.defaultimage"))) images.add(this.getConfig().getString("docker.defaultimage")); //Must insure we test the default image too!!
        images.forEach(image -> {
            if(!dockerImages.contains(image + ":latest")) this.logger.error("Image for template: " + image + " not found. This is case-sensitive and needs to be correct. Fix immediately, this could cause major errors.");
        });
        return images;
    }

    public List<String> getTemplateImages() {
        return templateImages;
    }
}
