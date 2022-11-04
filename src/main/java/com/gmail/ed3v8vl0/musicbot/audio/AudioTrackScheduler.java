package com.gmail.ed3v8vl0.musicbot.audio;

import com.gmail.ed3v8vl0.musicbot.MusicBot;
import com.gmail.ed3v8vl0.musicbot.youtube.YoutubeAPI;
import com.google.api.services.youtube.model.Video;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.Embed;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.List;

@Slf4j
public final class AudioTrackScheduler extends AudioEventAdapter {
    //ConcurrentModificationException
    private final List<AbstractMap.SimpleEntry<Video, AudioTrack>> trackQueue;
    private final Snowflake guildId;
    private final AudioPlayer player;

    public AudioTrackScheduler(final Snowflake guildId, final AudioPlayer player) {
        trackQueue = new LinkedList<>();
        this.guildId = guildId;
        this.player = player;
    }

    public List<AbstractMap.SimpleEntry<Video, AudioTrack>> getTrackQueue() {
        return trackQueue;
    }

    public void trackPlay(Video video, AudioTrack audioTrack) {
        trackPlay(video, audioTrack, false);
    }

    public synchronized void trackPlay(Video video, AudioTrack audioTrack, boolean noInterrupt) {
        final boolean trackStared = player.startTrack(audioTrack, !noInterrupt);

        if (!trackStared)
            trackQueue.add(new AbstractMap.SimpleEntry<>(video, audioTrack));

        MusicBot musicBot = MusicBot.getInstance();
        GatewayDiscordClient gatewayDiscordClient = musicBot.getGatewayDiscordClient();

        gatewayDiscordClient.getGuildById(guildId)
                .flatMap(guild -> guild.getChannelById(musicBot.getRegisterChannel(guild.getId()))
                        .cast(TextChannel.class)
                        .flatMap(channel -> channel.getMessagesAfter(Snowflake.of(0))
                                .next()
                                .flatMap(message -> {
                                    MessageEditSpec.Builder messageEditSpec = MessageEditSpec.builder();
                                    StringBuilder builder = new StringBuilder();

                                    if (!trackQueue.isEmpty()) {
                                        builder.append("__**재생 목록: **__");
                                        for (int i = 1; i <= trackQueue.size(); i++) {
                                            AbstractMap.SimpleEntry<Video, AudioTrack> entry = trackQueue.get(i - 1);

                                            builder.append(i);
                                            builder.append(". ");
                                            builder.append(entry.getKey().getSnippet().getTitle());

                                            if (i < trackQueue.size())
                                                builder.append('\n');
                                        }
                                    } else {
                                        builder.append("노래 제목을 입력하거나 Youtube URL 주소를 입력해주세요 !");
                                    }

                                    List<Embed> embedList = message.getEmbeds();

                                    if (embedList != null) {
                                        Embed oldEmbed = embedList.get(0);

                                        messageEditSpec.contentOrNull(builder.toString()).addEmbed(EmbedCreateSpec.builder().title(trackStared ? video.getSnippet().getTitle() + " " + YoutubeAPI.getDurationFormat(video.getContentDetails().getDuration()) : oldEmbed.getTitle().orElse("Data Error"))
                                                .image(trackStared ? YoutubeAPI.getMaxThumbnail(video.getSnippet().getThumbnails()).getUrl() : (oldEmbed.getImage().isPresent() ? oldEmbed.getImage().get().getUrl() : ""))
                                                .url(trackStared ? "https://youtu.be/" + video.getId() : "").build());
                                    }

                                    return message.edit(messageEditSpec.build());
                                })))
                .doOnError(throwable -> {
                    log.trace("AudioTrackScheduler trackPlay Error Occurred.", throwable);
                    log.error("AudioTrackScheduler trackPlay Error Occurred.\n{}", throwable.getMessage());
                })
                .onErrorResume(throwable -> Mono.never())
                .subscribe();

    }

    public synchronized void trackStop() {
        MusicBot musicBot = MusicBot.getInstance();
        GatewayDiscordClient gatewayDiscordClient = musicBot.getGatewayDiscordClient();

        trackQueue.clear();
        player.stopTrack();

        gatewayDiscordClient.getGuildById(guildId)
                .flatMap(guild -> guild.getChannelById(musicBot.getRegisterChannel(guild.getId()))
                        .cast(TextChannel.class)
                        .flatMap(channel -> channel.getMessagesAfter(Snowflake.of(0))
                                .next()
                                .flatMap(message -> message.edit(MessageEditSpec.builder().contentOrNull("노래 제목을 입력하거나 Youtube URL 주소를 입력해주세요 !").addEmbed(EmbedCreateSpec.builder().title("현재 재생중인 노래가 없습니다.")
                                        .image("https://c.wallhere.com/photos/cd/8d/1280x720_px_fantasy_Art-740774.jpg!d").build()).build()))))
                .doOnError(throwable -> {
                    log.trace("AudioTrackScheduler trackPlay Error Occurred.", throwable);
                    log.error("AudioTrackScheduler trackPlay Error Occurred.\n{}", throwable.getMessage());
                })
                .onErrorResume(throwable -> Mono.never())
                .subscribe();
    }

    public synchronized void trackPause() {
        player.setPaused(true);
    }

    public synchronized void trackResume() {
        player.setPaused(false);
    }

    public synchronized void trackSkip() {
        if (trackQueue.isEmpty()) {
            trackStop();
        } else {
            AbstractMap.SimpleEntry<Video, AudioTrack> entry = trackQueue.remove(0);
            trackPlay(entry.getKey(), entry.getValue(), true);
        }
    }

    public synchronized boolean isPaused() {
        return player.isPaused();
    }

    @Override
    public void onTrackEnd(final AudioPlayer player, final AudioTrack track, final AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            trackSkip();
        }
    }
}