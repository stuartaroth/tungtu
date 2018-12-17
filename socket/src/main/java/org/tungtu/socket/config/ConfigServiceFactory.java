package org.tungtu.socket.config;

public class ConfigServiceFactory {
    public static ConfigService getConfigService() {
        return new LocalConfigService();
    }
}
