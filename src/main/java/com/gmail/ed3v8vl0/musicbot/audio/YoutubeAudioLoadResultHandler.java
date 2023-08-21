package com.gmail.ed3v8vl0.musicbot.audio;

import com.gmail.ed3v8vl0.musicbot.scheduler.PacketScheduler;
import com.gmail.ed3v8vl0.musicbot.youtube.VideoData;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

@RequiredArgsConstructor
@AllArgsConstructor
public class YoutubeAudioLoadResultHandler implements AudioLoadResultHandler {
    private static final Logger logger = LoggerFactory.getLogger(YoutubeAudioLoadResultHandler.class);
    private final GuildAudioManager manager;
    private final VideoData video;

    @Nullable
    private Snowflake guildId;
    @Nullable
    private Message message;

    @Override
    public void trackLoaded(AudioTrack track) {
        manager.getScheduler().trackPlay(video, track);

        if (guildId != null && message != null)
            PacketScheduler.getScheduler(guildId).addMessage(message);
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {}

    @Override
    public void noMatches() {
        logger.warn("No Match Music");
    }

    @Override
    public void loadFailed(FriendlyException exception) {
        logger.error("Music loadFailed: {}", exception);

        if (guildId != null && message != null)
            PacketScheduler.getScheduler(guildId).addMessage(message);
    }
}
