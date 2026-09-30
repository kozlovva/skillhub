package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.VersionResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.VersionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/elements/{slug}/versions")
@RequiredArgsConstructor
public class VersionController {

    private final VersionUseCase versionUseCase;
    private final CurrentUserResolver currentUser;

    @PostMapping
    public ResponseEntity<VersionResponse> publish(@PathVariable String slug,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "changelog", required = false) String changelog,
                                                   Authentication auth) throws IOException {
        VersionResponse published = VersionResponse.from(
            versionUseCase.publish(slug, file.getBytes(), changelog, currentUser.resolve(auth)));
        return ResponseEntity.status(HttpStatus.CREATED).body(published);
    }
}
