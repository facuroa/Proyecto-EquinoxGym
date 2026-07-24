package com.equinox.EquinoxGym;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MorosidadService {

    private static final Set<String> FILTROS_VALIDOS = Set.of(
            "VENCIDAS", "1_7", "8_30", "MAS_30", "HOY", "PROXIMAS", "TODOS");

    private final CuotaRepository cuotaRepository;
    private final SocioRepository socioRepository;
    private final SeguimientoMorosidadRepository seguimientoRepository;
    private final CuotaService cuotaService;
    private final SocioService socioService;

    public MorosidadService(CuotaRepository cuotaRepository,
                            SocioRepository socioRepository,
                            SeguimientoMorosidadRepository seguimientoRepository,
                            CuotaService cuotaService,
                            SocioService socioService) {
        this.cuotaRepository = cuotaRepository;
        this.socioRepository = socioRepository;
        this.seguimientoRepository = seguimientoRepository;
        this.cuotaService = cuotaService;
        this.socioService = socioService;
    }

    @Transactional
    public MorosidadResumenDTO obtenerResumen(String filtroSolicitado, String busquedaSolicitada) {
        LocalDate hoy = LocalDate.now();
        List<Cuota> cuotas = cuotaRepository
                .findByFechaPagoIsNullAndFechaVencimientoLessThanEqualOrderByFechaVencimientoAsc(hoy.plusDays(7));

        List<Cuota> cuotasActualizadas = cuotas.stream()
                .filter(cuotaService::actualizarEstadoCuota)
                .toList();
        if (!cuotasActualizadas.isEmpty()) {
            cuotaRepository.saveAll(cuotasActualizadas);
        }

        List<Socio> sociosRelacionados = cuotas.stream()
                .map(Cuota::getSocio)
                .filter(socio -> socio != null && socio.getId() != null)
                .distinct()
                .toList();
        List<Socio> sociosActualizados = sociosRelacionados.stream()
                .filter(socioService::actualizarEstadoSocio)
                .toList();
        if (!sociosActualizados.isEmpty()) {
            socioRepository.saveAll(sociosActualizados);
        }

        Map<Long, List<Cuota>> cuotasPorSocio = new LinkedHashMap<>();
        for (Cuota cuota : cuotas) {
            if (cuota.getSocio() == null || cuota.getSocio().getId() == null || cuota.getFechaVencimiento() == null) {
                continue;
            }
            cuotasPorSocio.computeIfAbsent(cuota.getSocio().getId(), id -> new ArrayList<>()).add(cuota);
        }

        List<GestionMorosidadDTO> gestiones = new ArrayList<>();
        for (List<Cuota> cuotasSocio : cuotasPorSocio.values()) {
            Cuota primera = cuotasSocio.get(0);
            LocalDate vencimiento = primera.getFechaVencimiento();
            long diasAtraso = Math.max(ChronoUnit.DAYS.between(vencimiento, hoy), 0);

            GestionMorosidadDTO gestion = new GestionMorosidadDTO();
            gestion.setSocio(primera.getSocio());
            gestion.setVencimientoMasAntiguo(vencimiento);
            gestion.setDiasAtraso(diasAtraso);
            gestion.setCantidadCuotas(cuotasSocio.size());
            gestion.setSaldoPendiente(cuotasSocio.stream()
                    .map(Cuota::getMonto)
                    .filter(monto -> monto != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            gestion.setCategoria(categoria(vencimiento, hoy));
            gestiones.add(gestion);
        }

        if (!gestiones.isEmpty()) {
            Map<Long, SeguimientoMorosidad> ultimoPorSocio = new LinkedHashMap<>();
            List<Long> ids = gestiones.stream().map(g -> g.getSocio().getId()).toList();
            for (SeguimientoMorosidad seguimiento : seguimientoRepository.buscarPorSocios(ids)) {
                ultimoPorSocio.putIfAbsent(seguimiento.getSocio().getId(), seguimiento);
            }
            gestiones.forEach(g -> g.setUltimoSeguimiento(ultimoPorSocio.get(g.getSocio().getId())));
        }

        MorosidadResumenDTO resumen = new MorosidadResumenDTO();
        resumen.setSociosMorosos(gestiones.stream().filter(g -> g.getDiasAtraso() > 0).count());
        resumen.setDeudaVencida(cuotas.stream()
                .filter(c -> c.getFechaVencimiento() != null && c.getFechaVencimiento().isBefore(hoy))
                .map(Cuota::getMonto)
                .filter(monto -> monto != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        resumen.setVencenHoy(gestiones.stream().filter(g -> "HOY".equals(g.getCategoria())).count());
        resumen.setVencenProximos(gestiones.stream().filter(g -> "PROXIMAS".equals(g.getCategoria())).count());

        String filtro = normalizarFiltro(filtroSolicitado);
        String busqueda = busquedaSolicitada == null ? "" : busquedaSolicitada.trim().toLowerCase(Locale.ROOT);
        List<GestionMorosidadDTO> filtradas = gestiones.stream()
                .filter(g -> coincideFiltro(g, filtro))
                .filter(g -> coincideBusqueda(g, busqueda))
                .sorted(Comparator.comparingInt(GestionMorosidadDTO::getOrdenPrioridad)
                        .thenComparing(GestionMorosidadDTO::getVencimientoMasAntiguo)
                        .thenComparing(g -> g.getSocio().getApellido(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        resumen.setGestiones(filtradas);
        return resumen;
    }

    @Transactional
    public void registrarSeguimiento(Long socioId, CanalSeguimiento canal, String nota, String usuario) {
        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new IllegalArgumentException("El socio seleccionado ya no existe."));
        if (!cuotaRepository.existsBySocio_IdAndFechaPagoIsNullAndFechaVencimientoLessThanEqual(
                socioId, LocalDate.now().plusDays(7))) {
            throw new IllegalStateException("El socio ya no tiene una cuota pendiente en esta mesa de trabajo.");
        }
        String notaLimpia = nota == null ? "" : nota.trim();
        if (notaLimpia.length() < 2 || notaLimpia.length() > 500) {
            throw new IllegalArgumentException("La nota debe tener entre 2 y 500 caracteres.");
        }
        if (canal == null) {
            throw new IllegalArgumentException("Seleccioná el canal de contacto.");
        }

        SeguimientoMorosidad seguimiento = new SeguimientoMorosidad();
        seguimiento.setSocio(socio);
        seguimiento.setCanal(canal);
        seguimiento.setNota(notaLimpia);
        seguimiento.setFechaRegistro(LocalDateTime.now());
        seguimiento.setRegistradoPor(usuario == null || usuario.isBlank() ? "usuario" : usuario);
        seguimientoRepository.save(seguimiento);
    }

    public String normalizarFiltro(String filtro) {
        String normalizado = filtro == null ? "VENCIDAS" : filtro.trim().toUpperCase(Locale.ROOT);
        return FILTROS_VALIDOS.contains(normalizado) ? normalizado : "VENCIDAS";
    }

    private String categoria(LocalDate vencimiento, LocalDate hoy) {
        if (vencimiento.isAfter(hoy)) {
            return "PROXIMAS";
        }
        if (vencimiento.isEqual(hoy)) {
            return "HOY";
        }
        long dias = ChronoUnit.DAYS.between(vencimiento, hoy);
        if (dias <= 7) {
            return "1_7";
        }
        if (dias <= 30) {
            return "8_30";
        }
        return "MAS_30";
    }

    private boolean coincideFiltro(GestionMorosidadDTO gestion, String filtro) {
        return switch (filtro) {
            case "TODOS" -> true;
            case "VENCIDAS" -> gestion.getDiasAtraso() > 0;
            default -> filtro.equals(gestion.getCategoria());
        };
    }

    private boolean coincideBusqueda(GestionMorosidadDTO gestion, String busqueda) {
        if (busqueda.isBlank()) {
            return true;
        }
        Socio socio = gestion.getSocio();
        String texto = String.join(" ",
                socio.getNombre() == null ? "" : socio.getNombre(),
                socio.getApellido() == null ? "" : socio.getApellido(),
                socio.getDni() == null ? "" : socio.getDni(),
                socio.getTelefono() == null ? "" : socio.getTelefono()).toLowerCase(Locale.ROOT);
        if (texto.contains(busqueda)) {
            return true;
        }
        if (!busqueda.matches("[+0-9()\\s.\\-]+")) {
            return false;
        }

        String digitosBuscados = soloDigitos(busqueda);
        String dni = soloDigitos(socio.getDni());
        String telefono = soloDigitos(socio.getTelefono());
        return !digitosBuscados.isEmpty()
                && (dni.contains(digitosBuscados)
                || telefono.contains(digitosBuscados)
                || (telefono.length() >= 7 && digitosBuscados.contains(telefono)));
    }

    private String soloDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }
}
