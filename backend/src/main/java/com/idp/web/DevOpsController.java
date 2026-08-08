package com.idp.web;

import com.idp.service.DevOpsOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/devops")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DevOpsController {

    private final DevOpsOperationsService devOpsOperationsService;

    @GetMapping("/k8s/cluster")
    public ResponseEntity<Map<String, Object>> getK8sStatus() {
        return ResponseEntity.ok(devOpsOperationsService.getKubernetesClusterStatus());
    }

    @GetMapping("/security/scans")
    public ResponseEntity<Map<String, Object>> getSecurityScans() {
        return ResponseEntity.ok(devOpsOperationsService.getSecurityVulnerabilitiesScan());
    }

    @GetMapping("/finops/costs")
    public ResponseEntity<Map<String, Object>> getFinOpsReport() {
        return ResponseEntity.ok(devOpsOperationsService.getFinOpsCostReport());
    }

    @PostMapping("/pipelines/trigger")
    public ResponseEntity<Map<String, Object>> triggerPipeline(
            @RequestParam(defaultValue = "payment-service") String repository,
            @RequestParam(defaultValue = "main") String branch) {
        return ResponseEntity.ok(devOpsOperationsService.triggerPipelineRun(repository, branch));
    }
}
