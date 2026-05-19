package com.equinox.EquinoxGym;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {

    private final SocioRepository socioRepository;
    private final CuotaRepository cuotaRepository;
    private final SocioService socioService;

    public DashboardService(SocioRepository socioRepository,
                            CuotaRepository cuotaRepository,
                            SocioService socioService) {
        this.socioRepository = socioRepository;
        this.cuotaRepository = cuotaRepository;
        this.socioService = socioService;
    }

    public DashboardDTO obtenerResumen() {
        List<Socio> socios = socioRepository.findAll();

        for (Socio socio : socios) {
            socioService.actualizarEstadoSocio(socio);
            socioRepository.save(socio);
        }

        LocalDate hoy = LocalDate.now();
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        LocalDate finMes = hoy.withDayOfMonth(hoy.lengthOfMonth());

        long totalSocios = socioRepository.count();
        long sociosActivos = socioRepository.countByEstado(EstadoSocio.ACTIVO);
        long sociosMorosos = socioRepository.countByEstado(EstadoSocio.MOROSO);
        long sociosInactivos = socioRepository.countByEstado(EstadoSocio.INACTIVO);

        long cuotasVencidas = cuotaRepository.countByEstado(EstadoCuota.VENCIDA);
        long cuotasPagadasMes = cuotaRepository.countByEstadoAndFechaPagoBetween(
                EstadoCuota.PAGADA, inicioMes, finMes
        );

        BigDecimal recaudadoMes = cuotaRepository.sumarRecaudadoDelPeriodo(inicioMes, finMes);

        DashboardDTO dto = new DashboardDTO();
        dto.setTotalSocios(totalSocios);
        dto.setSociosActivos(sociosActivos);
        dto.setSociosMorosos(sociosMorosos);
        dto.setSociosInactivos(sociosInactivos);
        dto.setCuotasVencidas(cuotasVencidas);
        dto.setCuotasPagadasMes(cuotasPagadasMes);
        dto.setRecaudadoMes(recaudadoMes);

        return dto;
    }
}