package com.sekwah.advancedportals.paper.connector.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sekwah.advancedportals.core.commands.CommandTemplate;
import com.sekwah.advancedportals.core.connector.commands.CommandRegister;
import com.sekwah.advancedportals.paper.PortalsProMaxPlugin;
import com.sekwah.advancedportals.paper.connector.container.PaperCommandSenderContainer;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Registers the core commands with Paper's Brigadier command API.
 *
 * <p>The core does its own sub command parsing and tab completion, so each
 * command is a literal with one greedy argument that is handed to it. Using
 * Brigadier instead of plugin.yml gives proper client side command trees
 * (the commands show up in the chat suggestions immediately), works with
 * {@code /execute as}, and registers the aliases without any extra config.
 */
public class PaperCommandRegister implements CommandRegister {
    private static final Map<String, List<String>> ALIASES = Map.of(
        "portal", List.of("portals", "aportals", "advancedportals"),
        "destination", List.of("desti"));

    private static final Map<String, String> DESCRIPTIONS =
        Map.of("portal", "The main command for Portals Pro Max",
               "destination", "Can be used to access portal destinations");

    private final Map<String, CommandTemplate> templates =
        new LinkedHashMap<>();

    /**
     * Must be created while the plugin is enabling, as that is the only time
     * lifecycle handlers can be registered. The commands themselves are added
     * to the registrar a little later by Paper, after the core has registered
     * them with {@link #registerCommand}.
     */
    public PaperCommandRegister(PortalsProMaxPlugin plugin) {
        plugin.getLifecycleManager().registerEventHandler(
            LifecycleEvents.COMMANDS, event -> {
                Commands registrar = event.registrar();
                for (Map.Entry<String, CommandTemplate> entry :
                     templates.entrySet()) {
                    registrar.register(
                        this.buildCommand(entry.getKey(), entry.getValue()),
                        DESCRIPTIONS.getOrDefault(entry.getKey(), ""),
                        ALIASES.getOrDefault(entry.getKey(),
                                             Collections.emptyList()));
                }
            });
    }

    /**
     * Remembers the command, it is registered with Paper when the commands
     * lifecycle event fires.
     */
    @Override
    public void registerCommand(String commandName,
                                CommandTemplate commandExecutor) {
        this.templates.put(commandName, commandExecutor);
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack>
    buildCommand(String name, CommandTemplate template) {
        LiteralArgumentBuilder<CommandSourceStack> root =
            Commands.literal(name).executes(
                context -> this.run(name, template, context, ""));
        root.then(
            Commands
                .argument("arguments", StringArgumentType.greedyString())
                .suggests(
                    (context, builder) -> this.suggest(template, context, builder))
                .executes(context
                          -> this.run(name, template, context,
                                      StringArgumentType.getString(
                                          context, "arguments"))));
        return root.build();
    }

    private int run(String name, CommandTemplate template,
                    CommandContext<CommandSourceStack> context,
                    String arguments) {
        template.onCommand(
            new PaperCommandSenderContainer(this.senderOf(context)), name,
            arguments.isBlank() ? new String[0] : arguments.split(" "));
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions>
    suggest(CommandTemplate template, CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String typed = builder.getRemaining();
        String[] args = typed.split(" ", -1);
        List<String> suggestions = template.onTabComplete(
            new PaperCommandSenderContainer(this.senderOf(context)), args);

        // Only replace the word that is being typed
        String lastWord = args[args.length - 1];
        SuggestionsBuilder wordBuilder = builder.createOffset(
            builder.getStart() + typed.length() - lastWord.length());
        if (suggestions != null) {
            for (String suggestion : new ArrayList<>(suggestions)) {
                if (suggestion.startsWith(lastWord)) {
                    wordBuilder.suggest(suggestion);
                }
            }
        }
        return wordBuilder.buildFuture();
    }

    /**
     * {@code /execute as <player> run portal ...} runs as that player.
     */
    private CommandSender senderOf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (source.getExecutor() instanceof Player player) {
            return player;
        }
        return source.getSender();
    }
}
