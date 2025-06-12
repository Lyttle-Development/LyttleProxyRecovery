package com.lyttledev.lyttleproxyrecovery;

import com.google.inject.Inject;
import com.lyttledev.lyttleproxyrecovery.handlers.RecoveryManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

@Plugin(id = "lyttleproxyrecovery", name = "LyttleProxyRecovery", version = "1.0", authors = {"LyttleDevelopment", "Stualyttle"})
public class LyttleProxyRecovery {

    private final RecoveryManager recoveryManager;

    @Inject
    public LyttleProxyRecovery(ProxyServer server, Logger logger) {
        this.recoveryManager = new RecoveryManager(this, server, logger);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        recoveryManager.startPeriodicServerCheck();
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        recoveryManager.handleServerPostConnect(event);
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        recoveryManager.handleKickedFromServer(event);
    }

    // Optionally, keep this for debugging connection cleanup
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        recoveryManager.handleDisconnect(event);
    }
}