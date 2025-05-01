package utils;

import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.nio.file.FileAlreadyExistsException;
import java.util.prefs.Preferences;

public class ConfigManager {

    private static final String CONFIG_SUBPATH = "OLTApp/config/settings.json";
    private JSONObject config;
    private PrintStream logger;
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

    public void setLogger(PrintStream logger) {
        this.logger = logger;
        if (this.logger != null) {
            this.logger.println("DEBUG (ConfigManager): Logger instance received.");
        }
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
            if (logger != null) {
                logger.println("DEBUG (ConfigManager): Configurações carregadas de " + configFile.toAbsolutePath());
            }
        } catch (NoSuchFileException e) {

            config = new JSONObject();
            config.put("theme", "style.css");
            config.put("lastUser", "");
            if (logger != null) {
                logger.println("DEBUG (ConfigManager): Arquivo de configuração não encontrado, criando padrão.");
            } else {
                System.err.println("DEBUG (ConfigManager): Arquivo de configuração não encontrado, criando padrão. Logger não setado ainda.");
            }
            saveConfig();
        }
        catch (Exception e) {
            config = new JSONObject();
            config.put("theme", "style.css");
            config.put("lastUser", "");
            if (logger != null) {
                logger.println("DEBUG (ConfigManager): Erro ao carregar configurações, criando padrão.");
                logger.println("DEBUG (ConfigManager): Erro ao carregar: " + e.getMessage());
                e.printStackTrace(logger);
            } else {
                System.err.println("DEBUG (ConfigManager): Erro ao carregar configurações, criando padrão. Logger não setado ainda.");
                e.printStackTrace();
            }
            saveConfig();
        }
    }

    private void saveConfig() {
        Path configFile = getConfigFileFullPath();
        Path configDir = configFile.getParent();

        if (logger == null) {
            System.err.println("ERRO: Logger não setado no ConfigManager. Logs de salvamento não detalhados.");
        }

        try {

            Files.createDirectories(configDir);
            if (logger != null) {
                logger.println("DEBUG (ConfigManager): Diretório de configuração verificado/criado: " + configDir.toAbsolutePath());
            }


            Files.write(configFile, config.toString(4).getBytes());
            if (logger != null) {
                logger.println("DEBUG (ConfigManager): Configurações salvas com sucesso em: " + configFile.toAbsolutePath());
            }

        } catch (FileAlreadyExistsException e) {

            if (logger != null) {
                logger.println("DEBUG (ConfigManager): Diretório de configuração já existe: " + configDir.toAbsolutePath());
            }

            try {
                Files.write(configFile, config.toString(4).getBytes());
                if (logger != null) {
                    logger.println("DEBUG (ConfigManager): Configurações salvas com sucesso (diretório já existia) em: " + configFile.toAbsolutePath());
                }
            } catch (Exception writeEx) {

                if (logger != null) {
                    logger.println("!!!!!!!! ERRO CRÍTICO (ConfigManager): Falha ao salvar o arquivo de configurações (diretório existente) !!!!!!!");
                    logger.println("Caminho tentado para salvar: " + configFile.toAbsolutePath());
                    logger.println("Detalhes do erro:");
                    writeEx.printStackTrace(logger);
                } else {
                    System.err.println("!!!!!!!! ERRO CRÍTICO (ConfigManager): Falha ao salvar o arquivo de configurações (diretório existente) !!!!!!!");
                    System.err.println("Caminho tentado para salvar: " + configFile.toAbsolutePath());
                    System.err.println("Detalhes do erro:");
                    writeEx.printStackTrace();
                }
            }
        }
        catch (Exception e) {

            if (logger != null) {
                logger.println("!!!!!!!! ERRO CRÍTICO (ConfigManager): Falha ao salvar o arquivo de configurações !!!!!!!");
                logger.println("Caminho tentado para salvar: " + configFile.toAbsolutePath());
                logger.println("Detalhes do erro:");
                e.printStackTrace(logger);
            } else {
                System.err.println("!!!!!!!! ERRO CRÍTICO (ConfigManager): Falha ao salvar o arquivo de configurações !!!!!!!");
                System.err.println("Caminho tentado para salvar: " + configFile.toAbsolutePath());
                System.err.println("Detalhes do erro:");
                e.printStackTrace();
            }
        }
    }

    public String getLastUser() {
        String lastUser = config.optString("lastUser", "");
        if (logger != null) {
            logger.println("DEBUG (ConfigManager): Obtendo último usuário: '" + lastUser + "'");
        }
        return lastUser;
    }

    public void setLastUser(String username) {
        if (logger != null) {
            logger.println("DEBUG (ConfigManager): Definindo último usuário para: '" + username + "'");
        }
        config.put("lastUser", username);
        saveConfig();
    }

    public String getTheme() {
        String theme = config.optString("theme", "style.css");
        if (logger != null) {
            logger.println("DEBUG (ConfigManager): Obtendo tema: '" + theme + "'");
        }
        return theme;
    }

    public void setTheme(String themeName) {
        if (logger != null) {
            logger.println("DEBUG (ConfigManager): Definindo tema para: '" + themeName + "'");
        }
        config.put("theme", themeName);
        saveConfig();
    }
}
