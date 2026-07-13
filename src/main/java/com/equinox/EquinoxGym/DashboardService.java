package com.equinox.EquinoxGym;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final SocioRepository socioRepository;
    private final CuotaRepository cuotaRepository;
    private final PagoRepository pagoRepository;
    private final SocioService socioService;

    public DashboardService(SocioRepository socioRepository,
                            CuotaRepository cuotaRepository,
                            PagoRepository pagoRepository,
                            SocioService socioService) {
        this.socioRepository = socioRepository;
        this.cuotaRepository = cuotaRepository;
        this.pagoRepository = pagoRepository;
        this.socioService = socioService;
    }

    public DashboardDTO obtenerResumen() {
        List<Socio> socios = socioRepository.findAll();

        List<Socio> modificados = socios.stream()
                .filter(socioService::actualizarEstadoSocio)
                .toList();
        if (!modificados.isEmpty()) {
            socioRepository.saveAll(modificados);
        }

        LocalDate hoy = LocalDate.now();
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        LocalDate finMes = hoy.withDayOfMonth(hoy.lengthOfMonth());
        LocalDate finProximos = hoy.plusDays(7);

        long totalSocios = socioRepository.count();
        long sociosActivos = socioRepository.countByEstado(EstadoSocio.ACTIVO);
        long sociosMorosos = socioRepository.countByEstado(EstadoSocio.MOROSO);
        long sociosInactivos = socioRepository.countByEstado(EstadoSocio.INACTIVO);

        long cuotasVencidas = cuotaRepository.countByEstado(EstadoCuota.VENCIDA);
        long cuotasPagadasMes = pagoRepository.countByFechaPagoBetweenAndAnuladoFalse(inicioMes, finMes);

        BigDecimal recaudadoMes = pagoRepository.obtenerTotalRecaudadoDelMes(inicioMes, finMes);
        if (recaudadoMes == null) {
            recaudadoMes = BigDecimal.ZERO;
        }

        BigDecimal recaudadoHoy = pagoRepository.obtenerTotalRecaudadoDelMes(hoy, hoy);
        if (recaudadoHoy == null) {
            recaudadoHoy = BigDecimal.ZERO;
        }

        List<Cuota> cuotasVencidasPrioritarias = cuotaRepository
                .findTop6ByFechaPagoIsNullAndFechaVencimientoBeforeOrderByFechaVencimientoAsc(hoy);
        List<Cuota> cuotasProximas = cuotaRepository
                .findTop6ByFechaPagoIsNullAndFechaVencimientoBetweenOrderByFechaVencimientoAsc(hoy, finProximos);
        long vencenProximos = cuotaRepository
                .countByFechaPagoIsNullAndFechaVencimientoBetween(hoy, finProximos);
        long pagosHoy = pagoRepository.countByFechaPagoAndAnuladoFalse(hoy);
        List<Pago> ultimosPagosHoy = pagoRepository
                .findTop6ByAnuladoFalseAndFechaPagoOrderByFechaRegistroDescIdDesc(hoy);

        List<Socio> cumpleaniosHoy = socios.stream()
                .filter(s -> s.getFechaNacimiento() != null)
                .filter(s -> s.getFechaNacimiento().getDayOfMonth() == hoy.getDayOfMonth()
                        && s.getFechaNacimiento().getMonth() == hoy.getMonth())
                .collect(Collectors.toList());

        List<MonthDay> fechasProximas = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            fechasProximas.add(MonthDay.from(hoy.plusDays(i)));
        }
        List<Socio> cumpleaniosProximos = socios.stream()
                .filter(s -> s.getFechaNacimiento() != null)
                .filter(s -> fechasProximas.contains(MonthDay.from(s.getFechaNacimiento())))
                .sorted(Comparator.comparingInt(
                        s -> fechasProximas.indexOf(MonthDay.from(s.getFechaNacimiento()))))
                .collect(Collectors.toList());

        DashboardDTO dto = new DashboardDTO();
        dto.setTotalSocios(totalSocios);
        dto.setSociosActivos(sociosActivos);
        dto.setSociosMorosos(sociosMorosos);
        dto.setSociosInactivos(sociosInactivos);
        dto.setCuotasVencidas(cuotasVencidas);
        dto.setCuotasPagadasMes(cuotasPagadasMes);
        dto.setRecaudadoMes(recaudadoMes);
        dto.setRecaudadoHoy(recaudadoHoy);
        dto.setPagosHoy(pagosHoy);
        dto.setVencenProximos(vencenProximos);
        dto.setCumpleaniosHoy(cumpleaniosHoy);
        dto.setCumpleaniosProximos(cumpleaniosProximos);
        dto.setCuotasVencidasPrioritarias(cuotasVencidasPrioritarias);
        dto.setCuotasProximas(cuotasProximas);
        dto.setUltimosPagosHoy(ultimosPagosHoy);

        return dto;
    }
}
