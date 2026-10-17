package org.example.lastro.api;

import org.example.lastro.dto.HaltRequest;
import org.example.lastro.dto.MarketStatusResponse;
import org.example.lastro.service.MarketHaltService;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<MarketStatusResponse> halt(@PathVariable String symbol,
                                                     @RequestBody(required = false) HaltRequest request) {
        marketHaltService.halt(symbol, request != null ? request.reason() : null);
        return ResponseEntity.ok(status(symbol));
    }

    @PostMapping("/{symbol}/resume")
    public ResponseEntity<MarketStatusResponse> resume(@PathVariable String symbol) {
        marketHaltService.resume(symbol);
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
