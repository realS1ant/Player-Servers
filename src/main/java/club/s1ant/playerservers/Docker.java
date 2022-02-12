package club.s1ant.playerservers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Collectors;

public class Docker {

    private DockerClient client;
    private final PlayerServers plugin;

    public Docker(PlayerServers plugin) {
        this.plugin = plugin;
    }

    public void createDockerConnection() {
        String dockerHost = plugin.getConfig().getString("docker.host");
        if(dockerHost == null) {
            plugin.logger.error("Invalid 'dockerhost' in config. Please follow format: \"tcp://<url>:<port>\"");
            return;
        }
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(dockerHost)
            .withDockerTlsVerify(plugin.getConfig().getBoolean("docker.tlsverify"))
            .withDockerCertPath(plugin.getConfig().getString("docker.certificatepath"))
            .build();

        DockerHttpClient httpClient =  new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig())
            .build();

        try {
            client = DockerClientBuilder.getInstance(config).withDockerHttpClient(httpClient).build();
        } catch(Exception e) {
            e.printStackTrace();
            plugin.logger.error("Error connecting to docker at host: " + dockerHost);
            return;
        }
        plugin.logger.info("Connected to docker at host: " + dockerHost);
    }

    public void closeConnection() {
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isCreated(UUID uuid) {
        return client.listContainersCmd().withShowAll(true).withNameFilter(List.of(uuid.toString())).exec().size() > 0;
    }

    //Volume name format: vol-UUID-template

    public void createContainer(UUID uuid, String image) {
        //image name is always the same as the template.
        //supports multiple images (image name already verified in ServerManager.java)
        client.createContainerCmd(image)
                .withName(uuid.toString())
                .withHostConfig(
                        new HostConfig()
                                .withBinds(new Binds(new Bind("vol-" + uuid + "-" + image, new Volume(plugin.getConfig().getString("docker.containerserverdir")))))
                                .withPublishAllPorts(true)
                )
                .exec();
    }

    public void startContainer(Container container) {
        client.startContainerCmd(container.getId()).exec();
    }

    public void stopContainer(Container container) {
        client.stopContainerCmd(container.getId()).exec();
    }

    public void deleteContainer(Container container) {
        client.removeContainerCmd(container.getId()).exec();
    }

    public boolean hasVolume(UUID uuid, String template) {
        return client.listVolumesCmd().withFilter("name", List.of("vol-" + uuid + "-" + template)).exec().getVolumes().size() > 0;
    }

    public void deleteVolume(UUID uuid, String template) {
        client.removeVolumeCmd("vol-" + uuid + "-" + template).exec();
    }

    public void createVolume(UUID uuid, String template) {
        client.createVolumeCmd().withName("vol-" + uuid + "-" + template).exec();
    }

    public Optional<Container> getContainer(UUID uuid) {
        List<Container> containers = client.listContainersCmd().withNameFilter(List.of(uuid.toString())).withShowAll(true).exec();
        if(containers.size() > 0) return Optional.of(containers.get(0));
        else return Optional.empty();
    }

    public InetSocketAddress getContainerAddress(Container container) {
        Optional<ContainerPort> cpo = Arrays.stream(container.getPorts()).filter(port -> port.getPrivatePort() != null && port.getPrivatePort() == 25565).findFirst();
        if(cpo.isPresent()) {
            ContainerPort cp = cpo.get();
            if(cp.getPublicPort() == null) {
                plugin.logger.error("No public port for 25565 for container: " + container.getNames()[0]);
                return null;
            }
            return new InetSocketAddress(plugin.getConfig().getString("docker.publicip"), cp.getPublicPort());
        } else {
            plugin.logger.error("No public port for 25565 for container: " + container.getNames()[0]);
            return null;
        }
    }

    public Map<UUID, InetSocketAddress> getRunningServerContainers() {
        return client.listContainersCmd().exec().stream()
            .filter(container -> plugin.getTemplateImages().contains(container.getImage().split(":")[0])) // full tag name including :latest or whatever the version is.
            .map(container -> Map.entry(UUID.fromString(container.getNames()[0].replace("/", "")), getContainerAddress(container))) //all names start with / with this lib very annoying
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<UUID, NamedTextColor> getAllServerContainers() {
        return client.listContainersCmd().withShowAll(true).exec().stream()
            .filter(container -> plugin.getTemplateImages().contains(container.getImage().split(":")[0])) //no .withImageFilter and we can pretty much guarantee this'll work.
            .map(container -> Map.entry(UUID.fromString(container.getNames()[0].replace("/", "")), container.getState().equalsIgnoreCase("running") ? NamedTextColor.GREEN : NamedTextColor.GRAY))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<String> getAllImageNames() {
        return client.listImagesCmd().withShowAll(true).exec().stream().map(image -> image.getRepoTags()[0]).collect(Collectors.toList());
    }

    public List<String> listVolumes(UUID uuid) {
        return client.listVolumesCmd().exec().getVolumes().stream().map(InspectVolumeResponse::getName).filter(name -> name.startsWith("vol-" + uuid)).toList();
    }
}
