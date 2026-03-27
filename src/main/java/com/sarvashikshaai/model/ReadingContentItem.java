package com.sarvashikshaai.model;

/**
 * One item of reading content shown on the Reading page (title + body).
 * Used for reading practice when news/fetch is removed.
 */
public record ReadingContentItem(String title, String summary, String url) {}
