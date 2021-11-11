package com.gmail.ed3v8vl0.musicbot.audio;

import com.google.api.services.youtube.model.Video;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YoutubeAudioLoadResultHandler implements AudioLoadResultHandler {
    private static final Logger logger = LoggerFactory.getLogger(YoutubeAudioLoadResultHandler.class);
    private final GuildAudioManager manager;
    private final Video video;

    public YoutubeAudioLoadResultHandler(GuildAudioManager manager, Video video) {
        this.manager = manager;
        this.video = video;
    }

    @Override
    public void trackLoaded(AudioTrack track) {
        manager.getScheduler().trackPlay(video, track);
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
    }
}
