package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.VersionResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.VersionUseCase;
import com.skillhub.domain.model.ElementVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

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

    @GetMapping
    public List<VersionResponse> list(@PathVariable String slug, Authentication auth) {
        return versionUseCase.listVersions(slug, currentUser.resolve(auth)).stream()
            .map(VersionResponse::from)
            .toList();
    }

    @GetMapping("/{version}/download")
    public ResponseEntity<Void> download(@PathVariable String slug,
                                         @PathVariable String version,
                                         Authentication auth) {
        ElementVersion v = versionUseCase.getVersion(slug, version, currentUser.resolve(auth));
        String url = versionUseCase.getArchive(v);
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(java.net.URI.create(url))
            .build();
    }

    @GetMapping("/{version}/files")
    public ResponseEntity<byte[]> file(@PathVariable String slug,
                                       @PathVariable String version,
                                       @RequestParam("path") String path,
                                       Authentication auth) {
        ElementVersion v = versionUseCase.getVersion(slug, version, currentUser.resolve(auth));
        byte[] data = versionUseCase.getFile(v, path);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(data);
    }
}
