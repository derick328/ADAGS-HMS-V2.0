package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.application.MachineOutputStorageService;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 15-lab-technician.md §5.4: machine output goes to object storage, keyed
// by an opaque object key the frontend then includes in
// RecordLabResultRequest.machineOutputObjectKey. Two calls, not one, so a
// tech can attach the file before the rest of the form is complete.
@RestController
@RequestMapping("/api/lab-results/machine-output")
public class MachineOutputController {

    private final MachineOutputStorageService machineOutputStorageService;
    private final AuthorizationService authorizationService;

    public MachineOutputController(
            MachineOutputStorageService machineOutputStorageService, AuthorizationService authorizationService
    ) {
        this.machineOutputStorageService = machineOutputStorageService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    public UploadMachineOutputResponse upload(
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "LAB_RESULT_ENTER");
        return new UploadMachineOutputResponse(machineOutputStorageService.upload(file));
    }

    @GetMapping("/{objectKey}")
    public MachineOutputUrlResponse url(@PathVariable String objectKey, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "LAB_RESULT_READ");
        return new MachineOutputUrlResponse(machineOutputStorageService.presignedUrl(objectKey));
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }

    public record UploadMachineOutputResponse(String objectKey) {
    }

    public record MachineOutputUrlResponse(String url) {
    }
}
