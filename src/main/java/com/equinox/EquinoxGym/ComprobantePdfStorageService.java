package com.equinox.EquinoxGym;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ComprobantePdfStorageService {

    private static final String UPLOADS_DIR = "uploads/comprobantes";
    private static final DateTimeFormatter FECHA_FORMATO = DateTimeFormatter.ofPattern("yyyyMMdd");

    public ComprobantePdfStorageService() {
        try {
            Files.createDirectories(Paths.get(UPLOADS_DIR));
        } catch (IOException e) {
            System.err.println(">>> No se pudo crear directorio de comprobantes: " + e.getMessage());
        }
    }

    public String guardarComprobante(byte[] pdfBytes, Long pagoId) throws IOException {
        String nombreArchivo = String.format("comprobante_%d_%s.pdf",
            pagoId,
            UUID.randomUUID().toString().substring(0, 8));

        Path rutaArchivo = Paths.get(UPLOADS_DIR, nombreArchivo);
        Files.write(rutaArchivo, pdfBytes);

        return nombreArchivo;
    }

    public byte[] obtenerComprobante(String nombreArchivo) throws IOException {
        Path rutaArchivo = Paths.get(UPLOADS_DIR, nombreArchivo);

        if (!Files.exists(rutaArchivo)) {
            throw new IOException("Comprobante no encontrado: " + nombreArchivo);
        }

        return Files.readAllBytes(rutaArchivo);
    }

    public void eliminarComprobante(String nombreArchivo) throws IOException {
        Path rutaArchivo = Paths.get(UPLOADS_DIR, nombreArchivo);
        if (Files.exists(rutaArchivo)) {
            Files.delete(rutaArchivo);
        }
    }
}
