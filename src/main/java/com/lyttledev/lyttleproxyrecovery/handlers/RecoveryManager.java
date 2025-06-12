package com.lyttledev.lyttleproxyrecovery.handlers;

import com.lyttledev.lyttleproxyrecovery.LyttleProxyRecovery;
import com.lyttledev.lyttleproxyrecovery.utils.Constants;
import com.lyttledev.lyttleproxyrecovery.utils.ServerUtils;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class RecoveryManager {

    private final LyttleProxyRecovery plugin;
    private final ProxyServer server;
    private final Logger logger;
    private final Map<UUID, String> lastServer = new HashMap<>();
    private final Map<UUID, String> pendingRedirect = new HashMap<>();
    private final Set<String> downServers = new HashSet<>();

    public RecoveryManager(LyttleProxyRecovery plugin, ProxyServer server, Logger logger) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
    }

    public void startPeriodicServerCheck() {
        logger.info("Plugin enabled and initializing periodic server check...");
        server.getScheduler().buildTask(plugin, this::runPeriodicServerCheck)
            .repeat(Constants.PERIODIC_CHECK_INTERVAL_SEC, TimeUnit.SECONDS)
            .schedule();
    }

    private void runPeriodicServerCheck() {
        // logger.info("Periodic server check running...");
        // logger.info("Current pendingRedirect queue: {}", pendingRedirect);
        for (String srv : new HashSet<>(downServers)) {
            Optional<RegisteredServer> optServer = server.getServer(srv);
            optServer.ifPresent(regServer -> {
                logger.info("Pinging server '{}'", srv);
                regServer.ping().thenAccept(ping -> {
                    if (ping != null) {
                        logger.info("Server '{}' is UP. Attempting to redirect players...", srv);
                        List<UUID> toRemove = new ArrayList<>();
                        for (Map.Entry<UUID, String> entry : new HashMap<>(pendingRedirect).entrySet()) {
                            logger.info("Checking pending redirect for UUID '{}', server '{}'", entry.getKey(), entry.getValue());
                            if (entry.getValue().equals(srv)) {
                                Optional<Player> optPlayer = server.getPlayer(entry.getKey());
                                logger.info("Is player online? {}", optPlayer.isPresent());
                                optPlayer.ifPresent(player -> {
                                    logger.info("Sending player '{}' ({}) back to server '{}'", player.getUsername(), player.getUniqueId(), srv);
                                    player.createConnectionRequest(regServer).connect();
                                });
                                if (!optPlayer.isPresent()) {
                                    logger.warn("Player with UUID '{}' not found online, will not redirect.", entry.getKey());
                                }
                                toRemove.add(entry.getKey());
                            }
                        }
                        for (UUID uuid : toRemove) {
                            pendingRedirect.remove(uuid);
                        }
                        downServers.remove(srv);
                    } else {
                        logger.info("Server '{}' still appears to be DOWN.", srv);
                    }
                }).exceptionally(e -> {
                    logger.error("Error pinging server '{}': {}", srv, e.getMessage());
                    return null;
                });
            });
        }
    }

    public void handleServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer prevServer = event.getPreviousServer();
        if (prevServer != null) {
            lastServer.put(player.getUniqueId(), prevServer.getServerInfo().getName());
            logger.info("Player '{}' ({}) switched from server '{}'", player.getUsername(), player.getUniqueId(), prevServer.getServerInfo().getName());
        }
    }

    public void handleKickedFromServer(KickedFromServerEvent event) {
        Player player = event.getPlayer();
        RegisteredServer kickedFrom = event.getServer();
        String serverName = kickedFrom.getServerInfo().getName();
        String reason = event.getServerKickReason().toString();
        logger.warn("Player '{}' ({}) was kicked from '{}': {}", player.getUsername(), player.getUniqueId(), serverName, reason);

        if (ServerUtils.isLikelyServerCrash(reason)) {
            logger.warn("Kick appears to be due to server shutdown/crash. Adding player to pendingRedirect and server to downServers.");
            downServers.add(serverName);
            pendingRedirect.put(player.getUniqueId(), serverName);
        }
    }

    public void handleDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        String lastSrv = lastServer.get(player.getUniqueId());
        if (lastSrv != null && !ServerUtils.isServerOnline(server, lastSrv, logger)) {
            logger.warn("Player '{}' ({}) disconnected. Last server '{}' appears DOWN. Adding to recovery queue.", player.getUsername(), player.getUniqueId(), lastSrv);
            downServers.add(lastSrv);
            pendingRedirect.put(player.getUniqueId(), lastSrv);
        } else if (lastSrv != null) {
            logger.info("Player '{}' ({}) disconnected. Last server '{}' is UP.", player.getUsername(), player.getUniqueId(), lastSrv);
        } else {
            logger.info("Player '{}' ({}) disconnected. No previous server recorded.", player.getUsername(), player.getUniqueId());
        }
        lastServer.remove(player.getUniqueId());
    }
}