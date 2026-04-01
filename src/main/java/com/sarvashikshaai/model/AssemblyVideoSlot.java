package com.sarvashikshaai.model;

/**
 * One assembly segment (YouTube) for the main player + thumbnail rail.
 *
 * @param embedError {@code null} if YouTube oEmbed check passed; otherwise a short message for teachers.
 * @param slotKey    Config key: anthem, pledge, prayer, hindi — used for backoffice ordering only.
 */
public record AssemblyVideoSlot(String videoId, String title, String embedError, String slotKey) {}
