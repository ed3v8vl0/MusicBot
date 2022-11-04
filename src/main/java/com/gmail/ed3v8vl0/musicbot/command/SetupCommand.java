package com.gmail.ed3v8vl0.musicbot.command;

import com.gmail.ed3v8vl0.musicbot.MusicBot;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Color;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
public class SetupCommand extends ICommand {
    private final MusicBot musicBot;

    public SetupCommand(MusicBot musicBot) {
        super(ApplicationCommandRequest.builder()
                .name("setup")
                .description("노래 전용 채널을 설치합니다.")
                .build());

        this.musicBot = musicBot;
    }

    @Override
    public Mono<?> execute(ChatInputInteractionEvent event) {
        return event.getInteraction().getGuild()
                .filterWhen(musicBot::isUnregisterChannel)
                .doOnSuccess(guild -> {
                    if (guild == null) {
                        event.reply("이미 설치된 채널이 있습니다.").withEphemeral(true).then(Mono.delay(Duration.ofSeconds(10)).then(event.deleteReply()))
                                .doOnError(throwable -> {
                                    log.trace("Setup Command Error Occurred. (Command Responding)", throwable);
                                    log.error("Setup Command Error Occurred. (Command Responding)\n{}", throwable.getMessage());
                                })
                                .onErrorResume(throwable -> Mono.never())
                                .subscribe();
                    } else {
                        guild.createTextChannel("MusicBox")
                                .flatMap(textChannel -> {
                                    Mono<Void> replayMono;

                                    if (musicBot.registerChannel(guild, textChannel))
                                        replayMono = event.reply("채널 설치를 완료했습니다").withEphemeral(true).then(Mono.delay(Duration.ofSeconds(10)).then(event.deleteReply()));
                                    else
                                        replayMono = event.reply("채널 설치중 오류가 발생했습니다. 관리자에게 문의해주세요.").withEphemeral(true).then(Mono.delay(Duration.ofSeconds(10)).then(event.deleteReply()));

                                    return textChannel.createMessage(MessageCreateSpec.builder().content("노래 제목을 입력하거나 Youtube URL 주소를 입력해주세요.")
                                            .addEmbed(EmbedCreateSpec.builder().color(Color.WHITE)
                                                    .title("현재 재생중인 노래가 없습니다.")
                                                    .image("https://c.wallhere.com/photos/cd/8d/1280x720_px_fantasy_Art-740774.jpg!d").build())
                                            .addComponent(ActionRow.of(Button.secondary("play_pause", ReactionEmoji.unicode("\u23EF")),
                                                    Button.secondary("stop", ReactionEmoji.unicode("\u23F9")),
                                                    Button.secondary("trackNext", ReactionEmoji.unicode("\u23ED")))).build()).then(replayMono);
                                })
                                .doOnError(throwable -> {
                                    log.trace("Pause Command Error Occurred. (Channel Create Process)", throwable);
                                    log.error("Pause Command Error Occurred. (Channel Create Process)\n{}", throwable.getMessage());
                                })
                                .onErrorResume(throwable -> Mono.never())
                                .subscribe();
                    }
                })
                .doOnError(throwable -> {
                    log.trace("Pause Command Error Occurred.", throwable);
                    log.error("Pause Command Error Occurred.\n{}", throwable.getMessage());
                })
                .onErrorResume(throwable -> Mono.never());
    }
}