package com.gmail.ed3v8vl0.musicbot.command;

import com.gmail.ed3v8vl0.musicbot.MusicBot;
import com.gmail.ed3v8vl0.musicbot.audio.GuildAudioManager;
import com.gmail.ed3v8vl0.musicbot.audio.YoutubeAudioLoadResultHandler;
import com.gmail.ed3v8vl0.musicbot.schedule.MessageScheduler;
import com.gmail.ed3v8vl0.musicbot.youtube.YoutubeAPI;
import com.google.api.services.youtube.model.Video;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import discord4j.voice.AudioProvider;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class PlayCommand implements ICommand {
    private final MusicBot musicBot;
    private final AudioPlayerManager audioPlayerManager;

    public PlayCommand(MusicBot musicBot, AudioPlayerManager audioPlayerManager) {
        this.musicBot = musicBot;
        this.audioPlayerManager = audioPlayerManager;
    }

    @Override
    public Mono<?> execute(Event event) {
        if (event instanceof MessageCreateEvent) {
            MessageCreateEvent messageCreateEvent = (MessageCreateEvent) event;

            return Mono.justOrEmpty(messageCreateEvent.getMember())
                    .flatMap(Member::getVoiceState)
                    .flatMap(VoiceState::getChannel)
                    .doOnNext(voiceChannel -> {
                        String content = messageCreateEvent.getMessage().getContent();

                        if (content.startsWith("!play"))
                            content = content.substring(5);

                        if (content.isEmpty()) {
                            messageCreateEvent.getMessage().getRestChannel().createMessage(new MessageCreateSpec().addEmbed(embed -> embed.setColor(Color.WHITE).setTitle("노래 제목이나 Youtube URL 주소를 입력해주세요!")).asRequest()).block();
                            return;
                        }

                        GuildAudioManager manager = GuildAudioManager.of(messageCreateEvent.getGuildId().orElse(null));
                        AudioProvider provider = manager.getProvider();

                        if (voiceChannel.getVoiceConnection().block() == null) {
                            voiceChannel.join(spec -> spec.setProvider(provider))
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
                                        return Mono.first(onDelay, onEvent).then(connection.disconnect());
                                    }).subscribe();
                        }

                        YoutubeAPI youtubeAPI = musicBot.getYoutubeAPI();
                        try {
                            URL url = new URL(URLDecoder.decode(content, StandardCharsets.UTF_8));
                            String host = url.getHost();

                            if (host.equals("www.youtube.com") || host.equals("youtu.be")) {
                                String query = url.getQuery();
                                Video video = youtubeAPI.searchVideo(query == null ? url.getPath() : YoutubeAPI.parseQuery(query));

                                if (video != null)
                                    audioPlayerManager.loadItemOrdered(manager, content, new YoutubeAudioLoadResultHandler(manager, video));
                                else;
                                //Not Found Video
                            } else {
                                //Only support for youtube video
                            }
                        } catch (MalformedURLException e) {
                            Video video = youtubeAPI.searchVideo(content);

                            if (video != null)
                                audioPlayerManager.loadItemOrdered(manager, "https://youtu.be/" + video.getId(), new YoutubeAudioLoadResultHandler(manager, video));
                            else ;
                            //Not Found Video
                        }
                    });
        } else {
            return Mono.empty();
        }
    }
}
