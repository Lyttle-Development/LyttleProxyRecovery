package com.lyttledev.lyttleproxyrecovery.utils;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ServerUtils {

    private ServerUtils() {}

    public static boolean isLikelyServerCrash(String reason) {
        if (reason == null) return false;
        String lower = reason.toLowerCase();
        return lower.contains("server closed")
                || lower.contains("closed")
                || lower.contains("disconnected")
                || lower.contains("timed out")
                || lower.contains("connection reset")
                || lower.contains("connection refused")
                || lower.contains("no route to host")
                || lower.contains("unable to connect");
    }

    public static boolean isServerOnline(ProxyServer server, String serverName, Logger logger) {
        Optional<RegisteredServer> optServer = server.getServer(serverName);
        if (optServer.isPresent()) {
            try {
                boolean online = optServer.get().ping().get(1, TimeUnit.SECONDS) != null;
                logger.info("isServerOnline('{}') = {}", serverName, online);
                return online;
            } catch (Exception e) {
                logger.error("Error in isServerOnline('{}'): {}", serverName, e.getMessage());
                return false;
            }
        }
        logger.warn("isServerOnline('{}'): No such server registered!", serverName);
        return false;
    }
}