package com.equinox.EquinoxGym;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resuelve el logo de esta instalacion: el archivo indicado por
 * GYM_LOGO_PATH si existe, o el logo por defecto embebido en la app.
 * Usado tanto por /branding/logo (BrandingController) como por el PDF
 * del comprobante, para no duplicar la logica de resolucion.
 */
@Service
public class LogoService {

    public record Logo(byte[] bytes, String contentType) {
    }

    private final String logoPath;

    public LogoService(@Value("${equinox.branding.logo-path:}") String logoPath) {
        this.logoPath = logoPath;
    }

    public Logo obtenerLogo() throws IOException {
        if (logoPath != null && !logoPath.isBlank()) {
            Path path = Path.of(logoPath);
            if (Files.isRegularFile(path)) {
                String contentType = Files.probeContentType(path);
                return new Logo(Files.readAllBytes(path), contentType != null ? contentType : "image/png");
            }
        }
        ClassPathResource defaultLogo = new ClassPathResource("static/img/icono.png");
        return new Logo(defaultLogo.getContentAsByteArray(), "image/png");
    }

    public byte[] obtenerLogoBytes() throws IOException {
        return obtenerLogo().bytes();
    }
}
