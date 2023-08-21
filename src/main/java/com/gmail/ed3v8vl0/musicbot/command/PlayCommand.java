package com.gmail.ed3v8vl0.musicbot.command;

import com.gmail.ed3v8vl0.musicbot.MusicBot;
import com.gmail.ed3v8vl0.musicbot.audio.GuildAudioManager;
import com.gmail.ed3v8vl0.musicbot.audio.YoutubeAudioLoadResultHandler;
import com.gmail.ed3v8vl0.musicbot.youtube.VideoData;
import com.gmail.ed3v8vl0.musicbot.youtube.YoutubeAPI;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.Member;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.VoiceChannelJoinSpec;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Color;
import discord4j.voice.AudioProvider;
import discord4j.voice.VoiceConnection;
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
        Interaction interaction = event.getInteraction();
        Member member = interaction.getMember().get();

        return member.getVoiceState()
                .flatMap(VoiceState::getChannel)
                .doOnSuccess(voiceChannel -> {
                    if (voiceChannel == null) {
                        event.reply("보이스 채널에 접속한 후 명령어를 입력해주세요.")
                                .withEphemeral(true)
                                .then(Mono.delay(Duration.ofSeconds(5)).then(event.deleteReply())).subscribe();
                        return;
                    }

                    GuildAudioManager manager = GuildAudioManager.of(member.getGuildId());
                    AudioProvider provider = manager.getProvider();
                    String content = event.getOption("query-or-link")
                            .flatMap(ApplicationCommandInteractionOption::getValue)
                            .map(ApplicationCommandInteractionOptionValue::asString)
                            .orElse("");

                    VoiceConnection voiceConnection = voiceChannel.getVoiceConnection().block();

                    if (voiceConnection == null || (voiceConnection != null && !voiceConnection.isConnected().block())) {
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
                                    return Mono.firstWithSignal(onDelay, onEvent).then(connection.disconnect()).doOnSuccess(ignoreElement -> manager.getScheduler().trackStop());
                                }).subscribe();
                    }

                    YoutubeAPI youtubeAPI = musicBot.getYoutubeAPI();
                    VideoData video = null;

                    try {
                        URL url = new URL(URLDecoder.decode(content, StandardCharsets.UTF_8));
                        String host = url.getHost();

                        if (host.equals("www.youtube.com") || host.equals("youtu.be") || host.equals("youtube.com")) {
                            String query = url.getQuery();
                            video = youtubeAPI.getVideo(query == null ? url.getPath().substring(1) : YoutubeAPI.parseQuery(query));
                        } else {
                            interaction.getChannel()
                                    .flatMap(channel -> channel.createMessage(EmbedCreateSpec.builder()
                                            .description("유튜브 비디오 주소만 재생이 가능합니다.")
                                            .color(Color.RED)
                                            .build()))
                                    .flatMap(responseMessage -> responseMessage
                                            .delete()
                                            .delaySubscription(Duration.ofSeconds(5))).subscribe();
                        }
                    } catch (MalformedURLException e) {
                        video = youtubeAPI.searchVideo(content);
                    }

                    if (video != null) {
                        musicBot.getAudioPlayerManager().loadItemOrdered(manager, "https://www.youtube.com/watch?v=" + video.getId(), new YoutubeAudioLoadResultHandler(manager, video));
                        event.reply("노래를 재생했습니다.").withEphemeral(true).then(event.deleteReply().delaySubscription(Duration.ofSeconds(5))).subscribe();
                    } else {
                        interaction.getChannel()
                                .flatMap(channel -> channel.createMessage(EmbedCreateSpec.builder()
                                        .description("해당 비디오를 재생하지 못하였습니다.")
                                        .color(Color.RED)
                                        .build()))
                                .flatMap(responseMessage -> responseMessage
                                        .delete()
                                        .delaySubscription(Duration.ofSeconds(5))).subscribe();
                    }
                })
                .doOnError(throwable -> event.reply("명령어 입력 중 에러가 발생하였습니다. 관리자에게 문의해주세요")
                        .withEphemeral(true)
                        .then(Mono.delay(Duration.ofSeconds(5)).then(event.deleteReply())).subscribe());
    }
}