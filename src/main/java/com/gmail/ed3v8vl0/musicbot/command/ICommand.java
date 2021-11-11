package com.gmail.ed3v8vl0.musicbot.command;

import discord4j.core.event.domain.Event;
import reactor.core.publisher.Mono;

public interface ICommand {
    Mono<?> execute(Event event);
}