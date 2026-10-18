package io.github.pedrolucas167.lastro.api;

import io.github.pedrolucas167.lastro.config.Roles;
import io.github.pedrolucas167.lastro.dto.HaltRequest;
import io.github.pedrolucas167.lastro.dto.MarketStatusResponse;
import io.github.pedrolucas167.lastro.service.MarketHaltService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/market")
public class MarketController {
    private final MarketHaltService marketHaltService;

    public MarketController(MarketHaltService marketHaltService) {
        this.marketHaltService = marketHaltService;
    }

    @PostMapping("/{symbol}/halt")
    public ResponseEntity<MarketStatusResponse> halt(Authentication authentication,
                                                     @PathVariable String symbol,
                                                     @RequestBody(required = false) HaltRequest request) {
        marketHaltService.halt(Roles.actor(authentication), symbol,
            request != null ? request.getReason() : null);
        return ResponseEntity.ok(status(symbol));
    }

    @PostMapping("/{symbol}/resume")
    public ResponseEntity<MarketStatusResponse> resume(Authentication authentication,
                                                      @PathVariable String symbol) {
        marketHaltService.resume(Roles.actor(authentication), symbol);
        return ResponseEntity.ok(status(symbol));
    }

    @GetMapping("/halted")
    public ResponseEntity<Map<String, String>> halted() {
        return ResponseEntity.ok(marketHaltService.haltedSymbols());
    }

    private MarketStatusResponse status(String symbol) {
        return new MarketStatusResponse(
            symbol,
            marketHaltService.isHalted(symbol),
            marketHaltService.haltReason(symbol));
    }
}
