package club.s1ant.playerservers.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.UUID;

public class MojangAPI {

    public static final UUID getUUID(String name) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);;
            URLConnection con = url.openConnection();
            JsonObject object = JsonParser.parseReader(new InputStreamReader(con.getInputStream())).getAsJsonObject();
            return toOnlineUUID(object.get("id").getAsString());
        } catch (Exception e) {
//            e.printStackTrace();
            System.out.println("Mojang API either down or player non-existent.");
            return null;
        }
    }

    public static final String getUsername(UUID uuid) {
        try {
            URL url = new URL("https://api.mojang.com/user/profiles/" + uuid.toString().replaceAll("-", "") + "/names");
            URLConnection con = url.openConnection();
            JsonArray array = JsonParser.parseReader(new InputStreamReader(con.getInputStream())).getAsJsonArray();
            return array.get(0).getAsJsonObject().get("name").getAsString();
        } catch (Exception e) {
            System.out.println("Mojang API either down or player non-existent.");
            return null;
        }
    }

    private static UUID toOnlineUUID(String uuid) {
//        StringBuilder sb = new StringBuilder(uuid);
//        sb.insert(8, "-");
//        sb = new StringBuilder(sb.toString());
//        sb.insert(13, "-");
//        sb = new StringBuilder(sb.toString());
//        sb.insert(18, "-");
//        sb = new StringBuilder(sb.toString());
//        sb.insert(23, "-");
//
//        return UUID.fromString(sb.toString());

        return UUID.fromString(new StringBuilder(uuid).insert(20, '-')
                .insert(16, '-')
                .insert(12, '-')
                .insert(8, '-')
                .toString());
    }
}
