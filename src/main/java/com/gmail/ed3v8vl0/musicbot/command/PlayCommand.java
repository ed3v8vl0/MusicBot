package com.gmail.ed3v8vl0.musicbot.command;

import com.gmail.ed3v8vl0.musicbot.MusicBot;
import com.gmail.ed3v8vl0.musicbot.audio.GuildAudioManager;
import com.gmail.ed3v8vl0.musicbot.audio.YoutubeAudioLoadResultHandler;
import com.gmail.ed3v8vl0.musicbot.youtube.YoutubeAPI;
import com.google.api.services.youtube.model.Video;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Member;
import discord4j.core.spec.VoiceChannelJoinSpec;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
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
public class PlayCommand extends ICommand {
    private final MusicBot musicBot;
    private final AudioPlayerManager audioPlayerManager;

    public PlayCommand(MusicBot musicBot, AudioPlayerManager audioPlayerManager) {
        super(ApplicationCommandRequest.builder()
                .name("play")
                .description("해당 노래를 재생합니다.")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("query-or-link")
                        .description("제목 또는 링크 주소를 통해 노래를 재생합니다.")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(true)
                        .build())
                .build());

        this.musicBot = musicBot;
        this.audioPlayerManager = audioPlayerManager;
    }

    @Override
    public Mono<?> execute(ChatInputInteractionEvent event) {
        return Mono.justOrEmpty(event.getInteraction().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .doOnNext(voiceChannel -> {
                    String content = event.getOption("query-or-link").flatMap(ApplicationCommandInteractionOption::getValue)
                            .map(ApplicationCommandInteractionOptionValue::asString)
                            .orElse("");

                    GuildAudioManager manager = GuildAudioManager.of(event.getInteraction().getGuildId().get());
                    AudioProvider provider = manager.getProvider();

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
                            //Only support for youtube video
                            event.reply("유튜브 비디오 주소만 재생이 가능합니다.").withEphemeral(true).then(Mono.delay(Duration.ofSeconds(10)).then(event.deleteReply())).block();
                            return;
                        }
                    } catch (MalformedURLException e) {
                        video = youtubeAPI.searchVideo(content);
                    }

                    if (video != null) {
                        audioPlayerManager.loadItemOrdered(manager, "https://youtu.be/" + video.getId(), new YoutubeAudioLoadResultHandler(manager, video));
                        //event.reply("노래를 재생했습니다.").withEphemeral(true).then(event.deleteReply().delaySubscription(Duration.ofSeconds(10))).subscribe();
                    } else {
                        //Not Found Video
                        event.reply("해당 비디오를 재생하지 못하였습니다.").withEphemeral(true).then(Mono.delay(Duration.ofSeconds(10)).then(event.deleteReply())).block();
                    }
                });
    }
}