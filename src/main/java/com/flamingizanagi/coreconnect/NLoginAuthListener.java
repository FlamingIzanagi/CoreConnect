package com.flamingizanagi.coreconnect;

import com.nickuc.login.api.event.velocity.auth.AuthenticateEvent;
import com.nickuc.login.api.event.velocity.auth.BedrockLoginEvent;
import com.nickuc.login.api.event.velocity.auth.LoginEvent;
import com.nickuc.login.api.event.velocity.auth.PremiumLoginEvent;
import com.nickuc.login.api.event.velocity.auth.RegisterEvent;
import com.nickuc.login.api.event.velocity.auth.SessionLoginEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;

/**
 * nLogin Velocity auth hooks. UUID tickets are removed immediately after a successful auth event.
 */
public final class NLoginAuthListener {

    private final CoreConnect plugin;

    public NLoginAuthListener(CoreConnect plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        complete(event.getPlayer());
    }

    @Subscribe
    public void onRegister(RegisterEvent event) {
        complete(event.getPlayer());
    }

    @Subscribe
    public void onPremiumLogin(PremiumLoginEvent event) {
        complete(event.getPlayer());
    }

    @Subscribe
    public void onSessionLogin(SessionLoginEvent event) {
        complete(event.getPlayer());
    }

    @Subscribe
    public void onBedrockLogin(BedrockLoginEvent event) {
        complete(event.getPlayer());
    }

    @Subscribe
    public void onAuthenticate(AuthenticateEvent event) {
        complete(event.getPlayer());
    }

    private void complete(Player player) {
        plugin.completePendingRoute(player);
    }
}
