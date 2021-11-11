package com.gmail.ed3v8vl0.musicbot;

import com.gmail.ed3v8vl0.musicbot.command.*;
import com.gmail.ed3v8vl0.musicbot.youtube.YoutubeAPI;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.TextChannel;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class MusicBot {
    private static final Logger logger = LoggerFactory.getLogger(MusicBot.class);
    private static MusicBot INSTANCE;

    private final String DISCORD_TOKEN;
    // Key: GuildId | Value: ChannelId
    private final Map<Snowflake, Snowflake> channelMap = new HashMap<>();
    private final Map<String, ICommand> commandMap = new HashMap<>();

    @Getter
    private final YoutubeAPI youtubeAPI;
    @Getter
    private final AudioPlayerManager audioPlayerManager;
    @Getter
    private final DiscordClient discordClient;
    @Getter
    private final GatewayDiscordClient gatewayDiscordClient;
    @Getter
    private Connection connection;

    private MusicBot() {
        Properties properties = new Properties();

        try {
            properties.load(getClass().getClassLoader().getResourceAsStream("server.properties"));
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(properties.getProperty("url"), properties.getProperty("username"), properties.getProperty("password"));
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM channels");
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                channelMap.put(Snowflake.of(resultSet.getLong(1)), Snowflake.of(resultSet.getLong(2)));
            }
        } catch (ClassNotFoundException | SQLException | IOException e) {
            logger.error("", e);
        }

        youtubeAPI = new YoutubeAPI(properties.getProperty("GOOGLE_KEY"));
        DISCORD_TOKEN = properties.getProperty("DISCORD_TOKEN");
        discordClient = DiscordClient.create(DISCORD_TOKEN);
        gatewayDiscordClient = discordClient.login().block();
        gatewayDiscordClient.on(new EventListener(this)).subscribe();

        audioPlayerManager = new DefaultAudioPlayerManager();
        audioPlayerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        AudioSourceManagers.registerRemoteSources(audioPlayerManager);
        AudioSourceManagers.registerLocalSource(audioPlayerManager);

        commandMap.put("!setup", new SetupCommand(this));
        commandMap.put("!play", new PlayCommand(this, audioPlayerManager));
        commandMap.put("!stop", new StopCommand());
        commandMap.put("!pause", new PauseCommand());
        commandMap.put("!resume", new ResumeCommand());
        commandMap.put("!skip", new SkipCommand());
    }

    public static void main(final String[] args) {
        INSTANCE = new MusicBot();
        INSTANCE.getGatewayDiscordClient().onDisconnect().block();
    }

    public boolean registerChannel(Guild guild, TextChannel channel) {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO channels(guildId, channelId) VALUES(?,?)\n" +
                "ON DUPLICATE KEY UPDATE channelId=VALUES(channelId)")) {
            statement.setLong(1, guild.getId().asLong());
            statement.setLong(2, channel.getId().asLong());

            statement.execute();
            channelMap.put(guild.getId(), channel.getId());
            return true;
        } catch (SQLException e) {
            logger.error("", e);
        }

        return false;
    }

    public boolean hasRegisterChannel(Guild guild) {
        Snowflake channelId = channelMap.get(guild.getId());

        if (channelId != null) {
            return guild.getChannels()
                    .filter(channel -> channel.getId().equals(channelId))
                    .count()
                    .map(count -> 1L == count)
                    .block();
        } else {
            return false;
        }
    }

    public Snowflake getRegisterChannel(Snowflake guildId) {
        return channelMap.get(guildId);
    }

    public ICommand getCommand(String command) {
        return commandMap.get(command);
    }

    public static MusicBot getInstance() {
        return MusicBot.INSTANCE;
    }
}