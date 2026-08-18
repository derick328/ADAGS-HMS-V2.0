package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.AddWatchlistEntryCommand;
import com.adags.modules.inventory.application.AddWatchlistEntryService;
import com.adags.modules.inventory.domain.NarcoticWatchlistEntry;
import com.adags.modules.inventory.domain.NarcoticWatchlistEntryRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 12-pharmacist.md §10.6, 14-controlled-medicines-tanzania.md §2.1: the
// narcotic watchlist is DATA curated by the pharmacist-in-charge from the
// regulator's First Schedule, not code — gated by ITEM_CLASSIFY, the same
// authority that classifies items.
@RestController
@RequestMapping("/api/narcotic-watchlist")
public class WatchlistController {

    private final AddWatchlistEntryService addWatchlistEntryService;
    private final NarcoticWatchlistEntryRepository repository;
    private final AuthorizationService authorizationService;

    public WatchlistController(
            AddWatchlistEntryService addWatchlistEntryService,
            NarcoticWatchlistEntryRepository repository,
            AuthorizationService authorizationService
    ) {
        this.addWatchlistEntryService = addWatchlistEntryService;
        this.repository = repository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistEntryResponse add(@Valid @RequestBody AddWatchlistEntryRequest request, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "ITEM_CLASSIFY")) {
            throw new AccessDeniedException("Missing permission: ITEM_CLASSIFY");
        }
        NarcoticWatchlistEntry entry = addWatchlistEntryService.add(new AddWatchlistEntryCommand(
                request.innName(), request.atcPrefix(), request.sourceDocument(), request.sourceDate(),
                actor.userId()));
        return WatchlistEntryResponse.from(entry);
    }

    @GetMapping
    public WatchlistListResponse list(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "ITEM_CLASSIFY")) {
            throw new AccessDeniedException("Missing permission: ITEM_CLASSIFY");
        }
        return WatchlistListResponse.of(repository.findAll().stream().map(WatchlistEntryResponse::from).toList());
    }
}
