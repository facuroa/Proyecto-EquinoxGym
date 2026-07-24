package com.equinox.EquinoxGym;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReporteService {

    private static final DateTimeFormatter ETIQUETA_MES =
            DateTimeFormatter.ofPattern("MMM yy", new Locale("es", "AR"));

    private final PagoRepository pagoRepository;
    private final SocioRepository socioRepository;

    public ReporteService(PagoRepository pagoRepository, SocioRepository socioRepository) {
        this.pagoRepository = pagoRepository;
        this.socioRepository = socioRepository;
    }

    @Transactional(readOnly = true)
    public ReporteResumenDTO obtenerResumen(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("El período del reporte es obligatorio.");
        }
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta.");
        }

        List<Pago> pagosConfirmados = pagoRepository.findByAnuladoFalseOrderByFechaPagoAscIdAsc();
        List<Socio> socios = socioRepository.findAll();
        Map<Long, Long> primerPagoPorSocio = identificarPrimerPagoPorSocio(pagosConfirmados);

        List<Pago> pagosPeriodo = pagosConfirmados.stream()
                .filter(pago -> estaEntre(pago.getFechaPago(), desde, hasta))
                .toList();

        BigDecimal ingresos = sumarPagos(pagosPeriodo);
        long altas = contarAltas(socios, desde, hasta);
        long renovaciones = pagosPeriodo.stream()
                .filter(pago -> esRenovacion(pago, primerPagoPorSocio))
                .count();

        ReporteResumenDTO resumen = new ReporteResumenDTO();
        resumen.setDesde(desde);
        resumen.setHasta(hasta);
        resumen.setIngresos(ingresos);
        resumen.setPagosConfirmados(pagosPeriodo.size());
        resumen.setAltas(altas);
        resumen.setRenovaciones(renovaciones);
        resumen.setTicketPromedio(pagosPeriodo.isEmpty()
                ? BigDecimal.ZERO
                : ingresos.divide(BigDecimal.valueOf(pagosPeriodo.size()), 2, RoundingMode.HALF_UP));
        resumen.setMediosPago(agruparMediosPago(pagosPeriodo, ingresos));
        resumen.setEvolucionMensual(construirEvolucion(
                hasta, pagosConfirmados, socios, primerPagoPorSocio));
        return resumen;
    }

    private Map<Long, Long> identificarPrimerPagoPorSocio(List<Pago> pagos) {
        Map<Long, Long> primeros = new HashMap<>();
        for (Pago pago : pagos) {
            Long socioId = obtenerSocioId(pago);
            if (socioId != null && pago.getId() != null) {
                primeros.putIfAbsent(socioId, pago.getId());
            }
        }
        return primeros;
    }

    private List<ReporteMedioPagoDTO> agruparMediosPago(List<Pago> pagos, BigDecimal ingresos) {
        class Acumulado {
            private BigDecimal total = BigDecimal.ZERO;
            private long cantidad;
        }

        Map<String, Acumulado> acumulados = new LinkedHashMap<>();
        for (Pago pago : pagos) {
            String medio = pago.getMedioPago() == null || pago.getMedioPago().isBlank()
                    ? "Sin especificar" : pago.getMedioPago().trim();
            Acumulado acumulado = acumulados.computeIfAbsent(medio, clave -> new Acumulado());
            acumulado.total = acumulado.total.add(montoSeguro(pago));
            acumulado.cantidad++;
        }

        return acumulados.entrySet().stream()
                .sorted(Map.Entry.<String, Acumulado>comparingByValue(
                        Comparator.comparing(acumulado -> acumulado.total)).reversed())
                .map(entry -> {
                    int porcentaje = ingresos.signum() == 0 ? 0 : entry.getValue().total
                            .multiply(BigDecimal.valueOf(100))
                            .divide(ingresos, 0, RoundingMode.HALF_UP)
                            .intValue();
                    return new ReporteMedioPagoDTO(entry.getKey(), entry.getValue().total,
                            entry.getValue().cantidad, porcentaje);
                })
                .toList();
    }

    private List<ReporteMesDTO> construirEvolucion(LocalDate hasta,
                                                    List<Pago> pagos,
                                                    List<Socio> socios,
                                                    Map<Long, Long> primerPagoPorSocio) {
        YearMonth mesFinal = YearMonth.from(hasta);
        List<ReporteMesDTO> evolucion = new ArrayList<>();

        for (int desplazamiento = 5; desplazamiento >= 0; desplazamiento--) {
            YearMonth mes = mesFinal.minusMonths(desplazamiento);
            LocalDate inicio = mes.atDay(1);
            LocalDate fin = mes.atEndOfMonth();
            List<Pago> pagosMes = pagos.stream()
                    .filter(pago -> estaEntre(pago.getFechaPago(), inicio, fin))
                    .toList();
            String etiqueta = mes.format(ETIQUETA_MES);
            etiqueta = Character.toUpperCase(etiqueta.charAt(0)) + etiqueta.substring(1);
            evolucion.add(new ReporteMesDTO(
                    etiqueta,
                    sumarPagos(pagosMes),
                    contarAltas(socios, inicio, fin),
                    pagosMes.stream().filter(pago -> esRenovacion(pago, primerPagoPorSocio)).count()));
        }

        BigDecimal maximo = evolucion.stream()
                .map(ReporteMesDTO::getIngresos)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        for (ReporteMesDTO mes : evolucion) {
            int porcentaje = maximo.signum() == 0 ? 0 : mes.getIngresos()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(maximo, 0, RoundingMode.HALF_UP)
                    .intValue();
            mes.setPorcentajeAltura(porcentaje);
        }
        return evolucion;
    }

    private long contarAltas(List<Socio> socios, LocalDate desde, LocalDate hasta) {
        return socios.stream()
                .map(this::fechaAltaEfectiva)
                .filter(fecha -> estaEntre(fecha, desde, hasta))
                .count();
    }

    private LocalDate fechaAltaEfectiva(Socio socio) {
        return socio.getFechaAlta() != null ? socio.getFechaAlta() : socio.getFechaInicioPlan();
    }

    private boolean esRenovacion(Pago pago, Map<Long, Long> primerPagoPorSocio) {
        Long socioId = obtenerSocioId(pago);
        Long primerPagoId = socioId == null ? null : primerPagoPorSocio.get(socioId);
        return primerPagoId != null && pago.getId() != null && !pago.getId().equals(primerPagoId);
    }

    private Long obtenerSocioId(Pago pago) {
        return pago.getCuota() != null && pago.getCuota().getSocio() != null
                ? pago.getCuota().getSocio().getId() : null;
    }

    private BigDecimal sumarPagos(List<Pago> pagos) {
        return pagos.stream().map(this::montoSeguro).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal montoSeguro(Pago pago) {
        return pago.getMonto() == null ? BigDecimal.ZERO : pago.getMonto();
    }

    private boolean estaEntre(LocalDate fecha, LocalDate desde, LocalDate hasta) {
        return fecha != null && !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }
}
