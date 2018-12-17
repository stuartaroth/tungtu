package org.tungtu.socket;

import org.tungtu.socket.config.ConfigService;
import org.tungtu.socket.config.ConfigServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
    private static Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        logger.info("starting socket...");
        ConfigService configService = ConfigServiceFactory.getConfigService();
        SocketServer socketServer = new SocketServer(configService);
        socketServer.start();
    }
}
