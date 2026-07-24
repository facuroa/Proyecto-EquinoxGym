package com.equinox.EquinoxGym;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditoriaService {

    private final EventoAuditoriaRepository eventoRepository;

    public AuditoriaService(EventoAuditoriaRepository eventoRepository) {
        this.eventoRepository = eventoRepository;
    }

    @Transactional
    public void registrar(String usuario, String categoria, String detalle) {
        EventoAuditoria evento = new EventoAuditoria();
        evento.setUsuario(usuario == null || usuario.isBlank() ? "sistema" : usuario.trim());
        evento.setCategoria(limitar(categoria, 40));
        evento.setDetalle(limitar(detalle, 500));
        evento.setFechaRegistro(LocalDateTime.now());
        eventoRepository.save(evento);
    }

    @Transactional(readOnly = true)
    public List<EventoAuditoria> ultimosEventos() {
        return eventoRepository.findTop100ByOrderByFechaRegistroDescIdDesc();
    }

    private String limitar(String texto, int maximo) {
        if (texto == null || texto.isBlank()) {
            return "Sin detalle";
        }
        String limpio = texto.trim();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }
}
