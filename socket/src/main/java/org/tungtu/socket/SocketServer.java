package org.tungtu.socket;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.tungtu.socket.config.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.tungtu.socket.ConstantsService.*;

public class SocketServer extends WebSocketServer {
    private static Logger logger = LoggerFactory.getLogger(SocketServer.class);

    private ConfigService configService;

    private Schema messageForSocketSchema;
    private Schema messageFromSocketSchema;

    private ConcurrentHashMap<String, WebSocket> connectionsById;
    private ConcurrentHashMap<WebSocket, String> connectionsByConnection;
    private ConcurrentHashMap<String, ConcurrentHashMap<String, String>> propertiesById;

    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Channel channel;

    public SocketServer(ConfigService configService) throws Exception {
        super(configService.getSocketAddress());
        this.configService = configService;
        setupJsonSchemas();
        setupConnectionMaps();
        setupRabbit();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        createEntry(conn);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        removeEntry(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String id = connectionsByConnection.get(conn);
        if (id != null) {
            try {
                JSONObject jsonObject = new JSONObject(message);
                jsonObject.put(SOCKET_ID, id);
                ConcurrentHashMap<String, String> socketIdProperties = propertiesById.getOrDefault(SOCKET_ID, new ConcurrentHashMap<>());
                jsonObject.put(SOCKET_ID_PROPERTIES, new JSONObject(socketIdProperties));
                logger.info("onMessage object: " + jsonObject.toString());
                messageFromSocketSchema.validate(jsonObject);
                channel.basicPublish(DEFAULT_EXCHANGE, MESSAGES_FROM_SOCKET_QUEUE, null, jsonObject.toString().getBytes(StandardCharsets.UTF_8));
            } catch (org.json.JSONException e) {
                conn.send(errorMessage("Message was invalid JSON", message, e).toString());
            } catch (org.everit.json.schema.ValidationException e) {
                conn.send(errorMessage("JSON did not pass validation", message, e).toString());
            } catch (Exception e) {
                conn.send(errorMessage("Exception", message, e).toString());
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error("onError", ex);
    }

    @Override
    public void onStart() {

    }

    private void setupJsonSchemas() throws Exception {
        try (InputStream inputStream = this.getClass().getResourceAsStream("/message_for_socket.json")) {
            JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
            messageForSocketSchema = SchemaLoader.load(rawSchema);
        }

        try (InputStream inputStream = this.getClass().getResourceAsStream("/message_from_socket.json")) {
            JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
            messageFromSocketSchema = SchemaLoader.load(rawSchema);
        }
    }

    private void setupConnectionMaps() {
        connectionsById = new ConcurrentHashMap<>();
        connectionsByConnection = new ConcurrentHashMap<>();
        propertiesById = new ConcurrentHashMap<>();
    }

    private void createEntry(WebSocket conn) {
        String id = IdService.generateId();

        connectionsById.put(id, conn);
        connectionsByConnection.put(conn, id);
        propertiesById.put(id, new ConcurrentHashMap<>());
    }

    private void removeEntry(WebSocket conn) {
        String id = connectionsByConnection.get(conn);
        if (id != null) {
            connectionsById.remove(id);
            connectionsByConnection.remove(conn);
        }
    }

    private void setupRabbit() throws Exception {
        connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(configService.getHost());
        connectionFactory.setUsername(configService.getUsername());
        connectionFactory.setPassword(configService.getPassword());

        connection = connectionFactory.newConnection();
        channel = connection.createChannel();

        // messages-for-socket
        channel.exchangeDeclare(MESSAGES_FOR_SOCKET_EXCHANGE, FANOUT);
        String messagesForSocketQueue = channel.queueDeclare().getQueue();
        channel.queueBind(messagesForSocketQueue, MESSAGES_FOR_SOCKET_EXCHANGE, EMPTY_ROUTING_KEY);
        channel.basicConsume(messagesForSocketQueue, true, (consumerTag, delivery) -> {
            try {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                JSONObject jsonObject = new JSONObject(message);
                messageForSocketSchema.validate(jsonObject);

                String id = jsonObject.getString(SOCKET_ID);
                String payload = jsonObject.getString(PAYLOAD);
                JSONObject socketIdProperties = jsonObject.getJSONObject(SOCKET_ID_PROPERTIES);

                Map<String, String> mappedProperties = new HashMap<>();
                socketIdProperties.toMap().forEach((key, value) -> {
                    mappedProperties.put(key, value.toString());
                });

                ConcurrentHashMap<String, String> properties = propertiesById.getOrDefault(id, new ConcurrentHashMap<>());
                properties.putAll(mappedProperties);
                propertiesById.put(id, properties);

                WebSocket conn = connectionsById.get(id);
                conn.send(payload);
            } catch (Exception e) {
                logger.error("error on consume", e);
            }
        }, (consumerTag, sig) -> {

        });

        // messages-from-socket
        channel.queueDeclare(MESSAGES_FROM_SOCKET_QUEUE, true, false, false, null);
    }

    private JSONObject errorMessage(String error, String originalMessage, Exception e) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(MESSAGE_TYPE, ERROR);
        jsonObject.put(ERROR_MESSAGE, error);
        jsonObject.put(ORIGINAL_MESSAGE, originalMessage);
        jsonObject.put(EXCEPTION_MESSAGE, e.getMessage());
        return jsonObject;
    }
}
