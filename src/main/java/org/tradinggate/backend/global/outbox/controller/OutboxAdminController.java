package org.tradinggate.backend.global.outbox.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.tradinggate.backend.global.outbox.service.OutboxAdminService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/outbox")
@Profile({"clearing", "risk"})
public class OutboxAdminController {

    private final OutboxAdminService outboxAdminService;

    /**
     * 큐 상태 조회
     * 예: /internal/outbox/snapshot?pendingStuckSeconds=60
     */
    @GetMapping("/snapshot")
    public OutboxAdminService.OutboxQueueSnapshot snapshot(
            @RequestParam(defaultValue = "60") long pendingStuckSeconds
    ) {
        return outboxAdminService.snapshot(Duration.ofSeconds(pendingStuckSeconds));
    }

    /**
     * 단건 FAILED 리셋
     */
    @PostMapping("/failed/{id}/reset")
    public Map<String, Object> resetFailed(@PathVariable Long id) {
        int updated = outboxAdminService.resetFailedEvent(id);
        return Map.of("updated", updated);
    }

    /**
     * 전체 FAILED 리셋
     */
    @PostMapping("/failed/reset-all")
    public Map<String, Object> resetAllFailed() {
        int updated = outboxAdminService.resetAllFailed();
        return Map.of("updated", updated);
    }

    /**
     * 특정 시각 이후 FAILED 리셋
     * 예: /internal/outbox/failed/reset-since?from=2026-01-05T00:00:00Z
     */
    @PostMapping("/failed/reset-since")
    public Map<String, Object> resetFailedSince(@RequestParam Instant from) {
        int updated = outboxAdminService.resetFailedSince(from);
        return Map.of("updated", updated);
    }

    /**
     * 지금 바로 퍼블리셔를 N번 돌려 빠르게 큐 비우기
     * 예: /internal/outbox/drain?loops=20
     */
    @PostMapping("/drain")
    public Map<String, Object> drainNow(@RequestParam(defaultValue = "10") int loops) {
        int published = outboxAdminService.drainNow(loops);
        return Map.of("published", published);
    }
}
