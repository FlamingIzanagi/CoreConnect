package com.flamingizanagi.coreconnect;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CoreConnectCommand implements SimpleCommand {

    private static final String RELOAD_PERMISSION = "coreconnect.reload";

    private final CoreConnect plugin;

    public CoreConnectCommand(CoreConnect plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!source.hasPermission(RELOAD_PERMISSION)) {
                source.sendMessage(Component.text("[CoreConnect] You do not have permission to reload.", NamedTextColor.RED));
                return;
            }
            plugin.reloadConfiguration(source);
            return;
        }
        source.sendMessage(Component.text("[CoreConnect] Usage: /coreconnect reload", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0 || (args.length == 1 && "reload".startsWith(args[0].toLowerCase()))) {
            return List.of("reload");
        }
        return List.of();
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(suggest(invocation));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            return invocation.source().hasPermission(RELOAD_PERMISSION);
        }
        return true;
    }
}
