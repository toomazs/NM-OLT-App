package utils;

import org.json.JSONArray;
import org.json.JSONObject;
import models.OLT;
import models.Secrets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private static final String CONFIG_SUBPATH = "OLTApp/config/settings.json";
    private JSONObject config;
    private static ConfigManager instance;

    private ConfigManager() {
        loadConfig();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    private Path getConfigFileFullPath() {
        String userHome = System.getProperty("user.home");
        Path appDataDir = Paths.get(userHome, "AppData", "Roaming");
        return appDataDir.resolve(CONFIG_SUBPATH);
    }

    private void loadConfig() {
        Path configFile = getConfigFileFullPath();
        try {
            String content = new String(Files.readAllBytes(configFile));
            config = new JSONObject(content);
        } catch (IOException e) {
            config = new JSONObject();
            config.put("theme", "style.css");
            config.put("lastUser", "");
            config.put("dynamicOLTs", new JSONArray());
            saveConfig();
        }
    }

    private void saveConfig() {
        Path configFile = getConfigFileFullPath();
        Path configDir = configFile.getParent();
        try {
            Files.createDirectories(configDir);
            Files.write(configFile, config.toString(4).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getLastUser() {
        return config.optString("lastUser", "");
    }

    public void setLastUser(String username) {
        config.put("lastUser", username);
        saveConfig();
    }

    public String getTheme() {
        return config.optString("theme", "style.css");
    }

    public void setTheme(String themeName) {
        config.put("theme", themeName);
        saveConfig();
    }


    public List<OLT> getDynamicOLTs() {
        List<OLT> olts = new ArrayList<>();
        JSONArray oltArray = config.optJSONArray("dynamicOLTs");
        if (oltArray != null) {
            for (int i = 0; i < oltArray.length(); i++) {
                JSONObject oltJson = oltArray.getJSONObject(i);
                olts.add(new OLT(
                        oltJson.getString("name"),
                        oltJson.getString("ip"),
                        oltJson.optString("port", "22"),
                        oltJson.optString("user", null),
                        oltJson.optString("password", null)
                ));
            }
        }
        return olts;
    }

    public void saveDynamicOLTs(List<OLT> olts) {
        JSONArray oltArray = new JSONArray();
        for (OLT olt : olts) {
            JSONObject oltJson = new JSONObject();
            oltJson.put("name", olt.getName());
            oltJson.put("ip", olt.getIp());
            oltJson.put("port", olt.getPort());
            if (!olt.getUser().equals(Secrets.SSH_USER)) {
                oltJson.put("user", olt.getUser());
            }
            if (!olt.getPassword().equals(Secrets.SSH_PASS)) {
                oltJson.put("password", olt.getPassword());
            }
            oltArray.put(oltJson);
        }
        config.put("dynamicOLTs", oltArray);
        saveConfig();
    }
}