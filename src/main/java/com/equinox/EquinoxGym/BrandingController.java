package com.equinox.EquinoxGym;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

@Controller
public class BrandingController {

    private final LogoService logoService;

    public BrandingController(LogoService logoService) {
        this.logoService = logoService;
    }

    @GetMapping("/branding/logo")
    public ResponseEntity<byte[]> logo() throws IOException {
        LogoService.Logo logo = logoService.obtenerLogo();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .cacheControl(CacheControl.noCache())
                .body(logo.bytes());
    }
}
