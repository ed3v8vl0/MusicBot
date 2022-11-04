package com.gmail.ed3v8vl0.musicbot.command;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import reactor.core.publisher.Mono;

public abstract class ICommand {
    public final ApplicationCommandRequest commandRequest;

    protected ICommand(ApplicationCommandRequest commandRequest) {
        this.commandRequest = commandRequest;
    }

    public abstract Mono<?> execute(ChatInputInteractionEvent event);
}