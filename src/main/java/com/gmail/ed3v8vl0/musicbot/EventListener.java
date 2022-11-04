package com.gmail.ed3v8vl0.musicbot;

import com.gmail.ed3v8vl0.musicbot.audio.AudioTrackScheduler;
import com.gmail.ed3v8vl0.musicbot.audio.GuildAudioManager;
import com.gmail.ed3v8vl0.musicbot.audio.YoutubeAudioLoadResultHandler;
import com.gmail.ed3v8vl0.musicbot.command.ICommand;
import com.gmail.ed3v8vl0.musicbot.youtube.YoutubeAPI;
import com.google.api.services.youtube.model.Video;
import discord4j.common.util.Snowflake;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.VoiceChannelJoinSpec;
import discord4j.rest.util.Color;
import discord4j.voice.AudioProvider;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
public class EventListener extends ReactiveEventAdapter {
    private final MusicBot musicBot;

    public EventListener(MusicBot musicBot) {
        this.musicBot = musicBot;
    }

    @Override
    public Publisher<?> onMessageCreate(MessageCreateEvent event) {
        return event.getGuild()
                .flatMap(guild -> event.getMessage().getChannel()
                        .flatMap(channel -> {
                            Snowflake registerChannel = musicBot.getRegisterChannel(guild.getId());
                            Snowflake channelId = channel.getId();

                            if (!registerChannel.equals(channelId))
                                return Mono.empty();

                            return Mono.justOrEmpty(event.getMember())
                                    .filter(member -> !member.getId().equals(event.getClient().getSelfId()))
                                    .flatMap(Member::getVoiceState)
                                    .flatMap(VoiceState::getChannel)
                                    .flatMap(voiceChannel -> {
                                        GuildAudioManager manager = GuildAudioManager.of(guild.getId());
                                        AudioProvider provider = manager.getProvider();
                                        String content = event.getMessage().getContent();

                                        if (voiceChannel.getVoiceConnection().block() == null) {
                                            voiceChannel.join(VoiceChannelJoinSpec.builder().provider(provider).build())
                                                    .flatMap(connection -> {
                                                        final Publisher<Boolean> voiceStateCounter = voiceChannel.getVoiceStates()
                                                                .count()
                                                                .map(count -> 1L == count);

                                                        final Mono<Void> onDelay = Mono.delay(Duration.ofSeconds(1L))
                                                                .filterWhen(ignored -> voiceStateCounter)
                                                                .switchIfEmpty(Mono.never())
                                                                .then();

                                                        final Mono<Void> onEvent = musicBot.getGatewayDiscordClient().getEventDispatcher().on(VoiceStateUpdateEvent.class)
                                                                .filter(voiceEvent -> voiceEvent.getOld().flatMap(VoiceState::getChannelId).map(voiceChannel.getId()::equals).orElse(false))
                                                                .filterWhen(ignored -> voiceStateCounter)
                                                                .next()
                                                                .then();

                                                        // Disconnect the bot if either onDelay or onEvent are completed!
                                                        return Mono.firstWithSignal(onDelay, onEvent).then(connection.disconnect());
                                                    })
                                                    .doOnError(throwable -> {
                                                        log.trace("Play Command Error Occurred. (Voice Channel Connection Side)", throwable);
                                                        log.error("Play Command Error Occurred. (Voice Channel Connection Side)\n{}", throwable.getMessage());
                                                    })
                                                    .onErrorResume(throwable -> Mono.never())
                                                    .subscribe();
                                        }

                                        YoutubeAPI youtubeAPI = musicBot.getYoutubeAPI();
                                        Video video;

                                        try {
                                            URL url = new URL(URLDecoder.decode(content, StandardCharsets.UTF_8));
                                            String host = url.getHost();

                                            if (host.equals("www.youtube.com") || host.equals("youtu.be")) {
                                                String query = url.getQuery();
                                                video = youtubeAPI.getVideo(query == null ? url.getPath().substring(1) : YoutubeAPI.parseQuery(query));
                                            } else {
                                                return event.getMessage().delete().then(channel.createMessage(EmbedCreateSpec.builder().description("유튜브 비디오 주소만 재생이 가능합니다.").color(Color.ORANGE).build())
                                                        .flatMap(message -> message.delete().delaySubscription(Duration.ofSeconds(10))));
                                            }
                                        } catch (MalformedURLException e) {
                                            video = youtubeAPI.searchVideo(content);
                                        }

                                        if (video != null) {
                                            musicBot.getAudioPlayerManager().loadItemOrdered(manager, "https://youtu.be/" + video.getId(), new YoutubeAudioLoadResultHandler(manager, video));
                                            return event.getMessage().delete();
                                        } else {
                                            return event.getMessage().delete().then(channel.createMessage(EmbedCreateSpec.builder().description("해당 비디오를 재생하지 못하였습니다.").color(Color.RED).build())
                                                    .flatMap(message -> message.delete().delaySubscription(Duration.ofSeconds(10))));
                                        }
                                    });
                        }))
                .doOnError(throwable -> {
                    log.trace("Play Message Execute Error Occurred.", throwable);
                    log.error("Play Message Error Occurred.\n{}", throwable.getMessage());
                })
                .onErrorResume(throwable -> Mono.never());
    }

    @Override
    public Publisher<?> onChatInputInteraction(ChatInputInteractionEvent event) {
        return Mono.just(event).doOnNext(command -> {
            log.info("{} Command Received.", command.getCommandName());
            String commandName = command.getCommandName();
            ICommand commandBase = musicBot.getCommand(commandName);

            if (commandBase != null) {
                log.info("{} Command Executed.", command.getCommandName());
                commandBase.execute(event).subscribe();
            } else {
                log.error("{} 명령어가 초기화되지 않았습니다.", commandName);
            }
        });
    }

    @Override
    public Publisher<?> onButtonInteraction(ButtonInteractionEvent event) {
        return Mono.justOrEmpty(event.getInteraction().getMember())
                .flatMap(member -> Mono.justOrEmpty(musicBot.getRegisterChannel(member.getGuildId())) //등록되어 있는 Chanel을 가져와서
                        .filter(channelId -> channelId.equals(event.getInteraction().getChannelId())) //버튼을 작동한 Channel과 동일하며
                        .flatMap(channelId -> member.getVoiceState()))
                .flatMap(VoiceState::getChannel).flatMap(voiceChannel -> { //보이스 채널에 접속된 상태인지
                    GuildAudioManager manager = GuildAudioManager.of(voiceChannel.getGuildId());
                    AudioTrackScheduler scheduler = manager.getScheduler();
                    String customId = event.getCustomId();

                    switch (customId) {
                        case "play_pause":
                            if (scheduler.isPaused()) scheduler.trackResume();
                            else scheduler.trackPause();
                            break;
                        case "stop":
                            scheduler.trackStop();
                            break;
                        case "trackNext":
                            scheduler.trackSkip();
                            break;
                    }

                    return event.deferEdit();
                });
    }
}
