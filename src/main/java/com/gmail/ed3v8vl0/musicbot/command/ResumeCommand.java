package com.gmail.ed3v8vl0.musicbot.command;

import com.gmail.ed3v8vl0.musicbot.audio.GuildAudioManager;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class ResumeCommand extends ICommand {
    public ResumeCommand() {
        super(ApplicationCommandRequest.builder()
                .name("resume")
                .description("노래 재생을 재개합니다.")
                .build());
    }

    @Override
    public Mono<?> execute(ChatInputInteractionEvent event) {
        return Mono.justOrEmpty(event.getInteraction().getGuildId())
                .doOnNext(guildId -> GuildAudioManager.of(guildId).getScheduler().trackResume())
                .then(event.reply("노래 재생을 재개했습니다.").withEphemeral(true)).then(Mono.delay(Duration.ofSeconds(10)).then(event.deleteReply()));
    }
}