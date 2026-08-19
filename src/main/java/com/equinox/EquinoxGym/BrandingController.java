package com.equinox.EquinoxGym;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Controller
public class BrandingController {

    private final String logoPath;

    public BrandingController(@Value("${equinox.branding.logo-path:}") String logoPath) {
        this.logoPath = logoPath;
    }

    @GetMapping("/branding/logo")
    public ResponseEntity<byte[]> logo() throws IOException {
        if (logoPath != null && !logoPath.isBlank()) {
            Path path = Path.of(logoPath);
            if (Files.isRegularFile(path)) {
                String contentType = Files.probeContentType(path);
                return ResponseEntity.ok()
                        .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.noCache())
                        .body(Files.readAllBytes(path));
            }
        }

        ClassPathResource defaultLogo = new ClassPathResource("static/img/icono.png");
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noCache())
                .body(defaultLogo.getContentAsByteArray());
    }
}
