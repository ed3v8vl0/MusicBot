package com.gmail.ed3v8vl0.musicbot.audio;

import com.gmail.ed3v8vl0.musicbot.MusicBot;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import discord4j.common.util.Snowflake;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GuildAudioManager {
    private static final Map<Snowflake, GuildAudioManager> MANAGERS = new ConcurrentHashMap<>();

    public static GuildAudioManager of(final Snowflake guildId) {
        return MANAGERS.computeIfAbsent(guildId, key -> new GuildAudioManager(guildId));
    }

    private final Snowflake guildId;
    private final AudioPlayer player;
    private final AudioTrackScheduler scheduler;
    private final LavaPlayerAudioProvider provider;

    private GuildAudioManager(Snowflake guildId) {
        this.guildId = guildId;
        player = MusicBot.getInstance().getAudioPlayerManager().createPlayer();
        scheduler = new AudioTrackScheduler(guildId, player);
        provider = new LavaPlayerAudioProvider(player);

        player.addListener(scheduler);
    }

    public AudioPlayer getPlayer() {
        return player;
    }

    public AudioTrackScheduler getScheduler() {
        return scheduler;
    }

    public LavaPlayerAudioProvider getProvider() {
        return provider;
    }
}