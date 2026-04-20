package com.fos.workspace.search.api;

import com.fos.workspace.document.api.DocumentResponse;
import com.fos.workspace.event.api.EventResponse;

import java.util.List;

/**
 * Combined search result containing matched documents and events.
 * Each section may be empty if no results were found in that collection.
 */
public record SearchResponse(
    String query,
    List<DocumentResponse> documents,
    List<EventResponse> events,
    int totalDocuments,
    int totalEvents
) {}
