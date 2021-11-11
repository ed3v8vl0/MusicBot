package com.gmail.ed3v8vl0.musicbot.command;

import com.gmail.ed3v8vl0.musicbot.audio.GuildAudioManager;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.message.MessageCreateEvent;
import reactor.core.publisher.Mono;

public class ResumeCommand implements ICommand {
    @Override
    public Mono<?> execute(Event event) {
        if (event instanceof MessageCreateEvent) {
            MessageCreateEvent messageCreateEvent = (MessageCreateEvent) event;

            return Mono.justOrEmpty(messageCreateEvent.getGuildId())
                    .doOnNext(guildId -> GuildAudioManager.of(guildId).getScheduler().trackResume());
        } else {
            return Mono.empty();
        }
    }
}