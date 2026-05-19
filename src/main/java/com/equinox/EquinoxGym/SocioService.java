package com.equinox.EquinoxGym;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SocioService {

    private final SocioRepository socioRepository;
    private final CuotaService cuotaService;

    public SocioService(SocioRepository socioRepository, CuotaService cuotaService) {
        this.socioRepository = socioRepository;
        this.cuotaService = cuotaService;
    }

    public void actualizarEstadoSocio(Socio socio) {
        if (socio == null) {
            return;
        }

        LocalDate hoy = LocalDate.now();

        boolean tieneVencida = false;
        boolean tieneInactivaPor20Dias = false;

        for (Cuota cuota : socio.getCuotas()) {
            cuotaService.actualizarEstadoCuota(cuota);

            if (cuota.getEstado() == EstadoCuota.VENCIDA) {
                tieneVencida = true;

                long diasAtraso = ChronoUnit.DAYS.between(cuota.getFechaVencimiento(), hoy);
                if (diasAtraso >= 20) {
                    tieneInactivaPor20Dias = true;
                }
            }
        }

        if (tieneInactivaPor20Dias) {
            socio.setEstado(EstadoSocio.INACTIVO);
        } else if (tieneVencida) {
            socio.setEstado(EstadoSocio.MOROSO);
        } else {
            socio.setEstado(EstadoSocio.ACTIVO);
        }
    }

    public List<Socio> listarTodosActualizados() {
        List<Socio> socios = socioRepository.findAll();

        for (Socio socio : socios) {
            actualizarEstadoSocio(socio);
            socioRepository.save(socio);
        }

        return socios;
    }
}