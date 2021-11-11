package com.gmail.ed3v8vl0.musicbot.audio;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import discord4j.voice.AudioProvider;

import java.nio.ByteBuffer;

public final class LavaPlayerAudioProvider extends AudioProvider {
    private final AudioPlayer player;
    private final MutableAudioFrame frame;

    public LavaPlayerAudioProvider(final AudioPlayer player) {
        super(ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize()));

        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(this.getBuffer());
        this.player = player;
    }

    @Override
    public boolean provide() {
        final boolean didProvide = this.player.provide(this.frame);

        if (didProvide) {
            this.getBuffer().flip();
        }

        return didProvide;
    }
}