package org.tungtu.identity.config;

public class ConfigServiceFactory {
    public static ConfigService getConfigService() {
        return new LocalConfigService();
    }
}
