package com.gmail.ed3v8vl0.musicbot.command;

import com.gmail.ed3v8vl0.musicbot.MusicBot;
import com.gmail.ed3v8vl0.musicbot.audio.GuildAudioManager;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
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
        Member member = event.getInteraction().getMember().get();

        return member.getVoiceState()
                .flatMap(VoiceState::getChannel)
                .flatMap(voiceChannel -> voiceChannel.isMemberConnected(MusicBot.getInstance().getGatewayDiscordClient().getSelfId()))
                .defaultIfEmpty(false)
                .flatMap(isConnected -> {
                    if (isConnected) {
                        Snowflake guildId = event.getInteraction().getGuildId().orElse(Snowflake.of(0));

                        GuildAudioManager.of(guildId).getScheduler().trackSkip();
                        return event.reply("현재 재생중인 노래를 건너뛰었습니다.").withEphemeral(true)
                                .then(Mono.delay(Duration.ofSeconds(5)).then(event.deleteReply()));
                    } else {
                        return event.reply("보이스 채널 접속 및 노래 봇과 같은 보이스 채널에 접속을 해주세요.")
                                .withEphemeral(true)
                                .then(Mono.delay(Duration.ofSeconds(5)).then(event.deleteReply()));
                    }
                })
                .doOnError(throwable -> event.reply("명령어 입력 중 에러가 발생하였습니다. 관리자에게 문의해주세요")
                        .withEphemeral(true)
                        .then(Mono.delay(Duration.ofSeconds(5)).then(event.deleteReply())).subscribe());
    }
}