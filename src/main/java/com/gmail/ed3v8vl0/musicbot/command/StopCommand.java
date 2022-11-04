package com.gmail.ed3v8vl0.musicbot.command;

import com.gmail.ed3v8vl0.musicbot.audio.GuildAudioManager;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
public class StopCommand extends ICommand {
    public StopCommand() {
        super(ApplicationCommandRequest.builder()
                .name("stop")
                .description("노래 재생을 중단합니다.")
                .build());
    }


    @Override
    public Mono<?> execute(ChatInputInteractionEvent event) {
        return Mono.justOrEmpty(event.getInteraction().getGuildId())
                .doOnNext(guildId -> GuildAudioManager.of(guildId).getScheduler().trackStop())
                .then(event.reply("노래 재생을 중단했습니다.").withEphemeral(true)).then(Mono.delay(Duration.ofSeconds(10)).then(event.deleteReply()))
                .doOnError(throwable -> {
                    log.trace("Stop Command Error Occurred.", throwable);
                    log.error("Stop Command Error Occurred.\n{}", throwable.getMessage());
                })
                .onErrorResume(throwable -> Mono.never());
    }
}
