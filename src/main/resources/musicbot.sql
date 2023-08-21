CREATE DATABASE IF NOT EXISTS musicbox;
USE musicbox;
create table IF NOT EXISTS channels(guildId INT8 UNIQUE KEY, channelId INT8 NOT NULL);