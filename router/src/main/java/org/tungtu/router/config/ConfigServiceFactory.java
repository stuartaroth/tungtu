package org.tungtu.router.config;

public class ConfigServiceFactory {
    public static ConfigService getConfigService() {
        return new LocalConfigService();
    }
}
