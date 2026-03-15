package com.sarvashikshaai.model;

/**
 * A single student-friendly news item fetched from RSS and filtered for school suitability.
 */
public record AssemblyNews(String title, String summary, String url) {}
