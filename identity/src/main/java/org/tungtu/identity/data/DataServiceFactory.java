package org.tungtu.identity.data;

import org.tungtu.identity.config.ConfigService;

public class DataServiceFactory {
    public static DataService getDataService(ConfigService configService) {
        return new MockDataService();
    }
}
