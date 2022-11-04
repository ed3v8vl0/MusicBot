package com.gmail.ed3v8vl0.musicbot.command;

import com.gmail.ed3v8vl0.musicbot.audio.GuildAudioManager;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
public class SkipCommand extends ICommand {
    public SkipCommand() {
        super(ApplicationCommandRequest.builder()
                .name("skip")
                .description("재생중인 노래를 건너뜁니다.")
                .build());
    }

    @Override
    public Mono<?> execute(ChatInputInteractionEvent event) {
        return Mono.justOrEmpty(event.getInteraction().getGuildId())
                .doOnNext(guildId -> GuildAudioManager.of(guildId).getScheduler().trackSkip())
                .then(event.reply("재생중인 노래를 건너뛰었습니다.").withEphemeral(true)).then(Mono.delay(Duration.ofSeconds(10)).then(event.deleteReply()))
                .doOnError(throwable -> {
                    log.trace("Skip Command Error Occurred.", throwable);
                    log.error("Skip Command Error Occurred.\n{}", throwable.getMessage());
                })
                .onErrorResume(throwable -> Mono.never());
    }
}