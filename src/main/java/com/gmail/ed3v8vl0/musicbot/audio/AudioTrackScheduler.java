package com.gmail.ed3v8vl0.musicbot.audio;

import com.gmail.ed3v8vl0.musicbot.MusicBot;
import com.gmail.ed3v8vl0.musicbot.youtube.VideoData;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.List;

@Slf4j
public final class AudioTrackScheduler extends AudioEventAdapter {
    //ConcurrentModificationException
    private final List<AbstractMap.SimpleEntry<VideoData, AudioTrack>> trackQueue;
    private final Snowflake guildId;
    private final AudioPlayer player;

    @Getter
    private VideoData videoData;

    public AudioTrackScheduler(final Snowflake guildId, final AudioPlayer player) {
        trackQueue = new LinkedList<>();
        this.guildId = guildId;
        this.player = player;
    }

    public List<AbstractMap.SimpleEntry<VideoData, AudioTrack>> getTrackQueue() {
        return trackQueue;
    }

    public void trackPlay(VideoData video, AudioTrack audioTrack) {
        trackPlay(video, audioTrack, false);
    }

    public void trackPlay(VideoData video, AudioTrack audioTrack, boolean noInterrupt) {
        final boolean trackStared = player.startTrack(audioTrack, !noInterrupt);

        if (!trackStared)
            trackQueue.add(new AbstractMap.SimpleEntry<>(video, audioTrack));
        else
            videoData = video;
    }

    public void trackStop() {
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
                .subscribe();
    }

    public void trackPause() {
        player.setPaused(true);
    }

    public void trackResume() {
        player.setPaused(false);
    }

    public void trackSkip() {
        if (!trackQueue.isEmpty()) {
            AbstractMap.SimpleEntry<VideoData, AudioTrack> entry = trackQueue.remove(0);
            trackPlay(entry.getKey(), entry.getValue(), true);
        } else if (player.getPlayingTrack() != null) {
            trackStop();
        }
    }

    public boolean isPaused() {
        return player.isPaused();
    }

    public boolean isTrackStarted() {
        return player.getPlayingTrack() != null;
    }
    @Override
    public void onTrackEnd(final AudioPlayer player, final AudioTrack track, final AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            trackSkip();
        }
    }
}