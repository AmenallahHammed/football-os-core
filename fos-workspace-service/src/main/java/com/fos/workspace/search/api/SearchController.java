package com.fos.workspace.search.api;

import com.fos.workspace.search.application.WorkspaceSearchService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final WorkspaceSearchService searchService;

    public SearchController(WorkspaceSearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Search workspace content.
     * GET /api/v1/search?q=contract
     * Returns matching documents and events filtered by caller's permissions.
     */
    @GetMapping
    public SearchResponse search(@RequestParam @NotBlank String q) {
        return searchService.search(q);
    }
}
