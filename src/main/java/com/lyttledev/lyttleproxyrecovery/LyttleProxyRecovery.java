package com.lyttledev.lyttleproxyrecovery;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.plugin.Plugin;
import org.slf4j.Logger;

import com.google.inject.Inject;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Plugin(id = "lyttleproxyrecovery", name = "LyttleProxyRecovery", version = "1.0", authors = {"LyttleDevelopment", "Stualyttle"})
public class LyttleProxyRecovery {

    private final ProxyServer server;
    private final Logger logger;
    private final Map<UUID, String> lastServer = new HashMap<>();
    private final Map<UUID, String> pendingRedirect = new HashMap<>();
    private final Set<String> downServers = new HashSet<>();

    @Inject
    public LyttleProxyRecovery(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("[LyttleProxyRecovery] Plugin enabled and initializing periodic server check...");
        server.getScheduler().buildTask(this, () -> {
            logger.info("[LyttleProxyRecovery] Periodic server check running...");
            logger.info("[LyttleProxyRecovery] Current pendingRedirect queue: {}", pendingRedirect);
            for (String srv : new HashSet<>(downServers)) {
                Optional<RegisteredServer> optServer = server.getServer(srv);
                optServer.ifPresent(regServer -> {
                    logger.info("[LyttleProxyRecovery] Pinging server '{}'", srv);
                    regServer.ping().thenAccept(ping -> {
                        if (ping != null) {
                            logger.info("[LyttleProxyRecovery] Server '{}' is UP. Attempting to redirect players...", srv);
                            List<UUID> toRemove = new ArrayList<>();
                            for (Map.Entry<UUID, String> entry : new HashMap<>(pendingRedirect).entrySet()) {
                                logger.info("[LyttleProxyRecovery] Checking pending redirect for UUID '{}', server '{}'", entry.getKey(), entry.getValue());
                                if (entry.getValue().equals(srv)) {
                                    Optional<Player> optPlayer = server.getPlayer(entry.getKey());
                                    logger.info("[LyttleProxyRecovery] Is player online? {}", optPlayer.isPresent());
                                    optPlayer.ifPresent(player -> {
                                        logger.info("[LyttleProxyRecovery] Sending player '{}' ({}) back to server '{}'", player.getUsername(), player.getUniqueId(), srv);
                                        player.createConnectionRequest(regServer).connect();
                                    });
                                    if (!optPlayer.isPresent()) {
                                        logger.warn("[LyttleProxyRecovery] Player with UUID '{}' not found online, will not redirect.", entry.getKey());
                                    }
                                    toRemove.add(entry.getKey());
                                }
                            }
                            for (UUID uuid : toRemove) {
                                pendingRedirect.remove(uuid);
                            }
                            downServers.remove(srv);
                        } else {
                            logger.info("[LyttleProxyRecovery] Server '{}' still appears to be DOWN.", srv);
                        }
                    }).exceptionally(e -> {
                        logger.error("[LyttleProxyRecovery] Error pinging server '{}': {}", srv, e.getMessage());
                        return null;
                    });
                });
            }
        }).repeat(5, TimeUnit.SECONDS).schedule();
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer prevServer = event.getPreviousServer();
        if (prevServer != null) {
            lastServer.put(player.getUniqueId(), prevServer.getServerInfo().getName());
            logger.info("[LyttleProxyRecovery] Player '{}' ({}) switched from server '{}'", player.getUsername(), player.getUniqueId(), prevServer.getServerInfo().getName());
        }
    }

    // Replace onDisconnect with onKickedFromServerEvent!
    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        Player player = event.getPlayer();
        RegisteredServer kickedFrom = event.getServer();
        String serverName = kickedFrom.getServerInfo().getName();
        String reason = event.getServerKickReason().toString();
        logger.warn("[LyttleProxyRecovery] Player '{}' ({}) was kicked from '{}': {}", player.getUsername(), player.getUniqueId(), serverName, reason);

        // Heuristic: if the kick reason includes "Server closed" or similar, treat as crash/shutdown
        if (reason.toLowerCase().contains("server closed") || reason.toLowerCase().contains("closed")) {
            logger.warn("[LyttleProxyRecovery] Kick appears to be due to server shutdown/crash. Adding player to pendingRedirect and server to downServers.");
            downServers.add(serverName);
            pendingRedirect.put(player.getUniqueId(), serverName);
        }
    }

    private boolean isServerOnline(String serverName) {
        Optional<RegisteredServer> optServer = server.getServer(serverName);
        if (optServer.isPresent()) {
            try {
                boolean online = optServer.get().ping().get(1, TimeUnit.SECONDS) != null;
                logger.info("[LyttleProxyRecovery] isServerOnline('{}') = {}", serverName, online);
                return online;
            } catch (Exception e) {
                logger.error("[LyttleProxyRecovery] Error in isServerOnline('{}'): {}", serverName, e.getMessage());
                return false;
            }
        }
        logger.warn("[LyttleProxyRecovery] isServerOnline('{}'): No such server registered!", serverName);
        return false;
    }
}