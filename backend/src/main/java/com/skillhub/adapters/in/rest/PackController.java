package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.PackContentRequest;
import com.skillhub.adapters.in.rest.dto.PackResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.PackUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/packs/{slug}")
@RequiredArgsConstructor
public class PackController {

    private final PackUseCase packUseCase;
    private final CurrentUserResolver currentUser;

    @PostMapping("/contents")
    public ResponseEntity<PackResponse> addContent(@PathVariable String slug,
                                                   @Valid @RequestBody PackContentRequest req,
                                                   Authentication auth) {
        packUseCase.addContent(slug, req.element(), req.versionConstraint(), currentUser.resolve(auth));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PackResponse.from(slug, packUseCase.getContents(slug, currentUser.resolve(auth))));
    }

    @GetMapping
    public PackResponse get(@PathVariable String slug, Authentication auth) {
        return PackResponse.from(slug, packUseCase.getContents(slug, currentUser.resolve(auth)));
    }

    @GetMapping("/versions/{version}/download")
    public ResponseEntity<byte[]> download(@PathVariable String slug,
                                           @PathVariable String version,
                                           Authentication auth) {
        byte[] data = packUseCase.downloadPack(slug, currentUser.resolve(auth));
        String filename = slug + "-" + version + ".zip";
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(data);
    }
}
