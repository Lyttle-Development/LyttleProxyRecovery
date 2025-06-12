package com.lyttledev.lyttleproxyrecovery;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.plugin.Plugin;

import com.google.inject.Inject;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Plugin(id = "lyttleproxyrecovery", name = "LyttleProxyRecovery", version = "1.0", authors = {"LyttleDevelopment", "Stualyttle"})
public class LyttleProxyRecovery {

    private final ProxyServer server;
    private final Map<UUID, String> lastServer = new HashMap<>();
    private final Map<UUID, String> pendingRedirect = new HashMap<>();
    private final Set<String> downServers = new HashSet<>();

    @Inject
    public LyttleProxyRecovery(ProxyServer server) {
        this.server = server;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Periodic task to check for server recovery
        server.getScheduler().buildTask(this, () -> {
            for (String srv : new HashSet<>(downServers)) {
                Optional<RegisteredServer> optServer = server.getServer(srv);
                optServer.ifPresent(regServer -> {
                    regServer.ping().thenAccept(ping -> {
                        if (ping != null) {
                            // Server is back, redirect players
                            List<UUID> toRemove = new ArrayList<>();
                            for (Map.Entry<UUID, String> entry : new HashMap<>(pendingRedirect).entrySet()) {
                                if (entry.getValue().equals(srv)) {
                                    Optional<Player> optPlayer = server.getPlayer(entry.getKey());
                                    optPlayer.ifPresent(player -> {
                                        player.createConnectionRequest(regServer).connect();
                                    });
                                    toRemove.add(entry.getKey());
                                }
                            }
                            for (UUID uuid : toRemove) {
                                pendingRedirect.remove(uuid);
                            }
                            downServers.remove(srv);
                        }
                    }).exceptionally(e -> null);
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
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        String lastSrv = lastServer.get(player.getUniqueId());
        if (lastSrv != null && !isServerOnline(lastSrv)) {
            // Server went down; track for resend
            downServers.add(lastSrv);
            pendingRedirect.put(player.getUniqueId(), lastSrv);
        }
        lastServer.remove(player.getUniqueId());
    }

    private boolean isServerOnline(String serverName) {
        Optional<RegisteredServer> optServer = server.getServer(serverName);
        if (optServer.isPresent()) {
            try {
                return optServer.get().ping().get(1, TimeUnit.SECONDS) != null;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
}