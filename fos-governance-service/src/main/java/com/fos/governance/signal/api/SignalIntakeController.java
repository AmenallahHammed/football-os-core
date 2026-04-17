package com.fos.governance.signal.api;

import com.fos.governance.signal.application.SignalProcessingService;
import com.fos.sdk.events.SignalEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/signals")
public class SignalIntakeController {

    private final SignalProcessingService processingService;

    public SignalIntakeController(SignalProcessingService processingService) {
        this.processingService = processingService;
    }

    @PostMapping
    public ResponseEntity<Void> intake(@RequestBody SignalEnvelope signal) {
        SignalEnvelope result = processingService.process(signal);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
        return ResponseEntity.accepted().build();
    }
}
