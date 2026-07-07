package com.equinox.EquinoxGym;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class CobroService {

    private final SocioRepository socioRepository;
    private final CuotaRepository cuotaRepository;
    private final PagoRepository pagoRepository;
    private final CuotaService cuotaService;
    private final SocioService socioService;

    public CobroService(SocioRepository socioRepository,
                        CuotaRepository cuotaRepository,
                        PagoRepository pagoRepository,
                        CuotaService cuotaService,
                        SocioService socioService) {
        this.socioRepository = socioRepository;
        this.cuotaRepository = cuotaRepository;
        this.pagoRepository = pagoRepository;
        this.cuotaService = cuotaService;
        this.socioService = socioService;
    }

    public List<SocioBusquedaDTO> buscarSocios(String texto) {
        if (texto == null || texto.trim().length() < 2) {
            return List.of();
        }

        return socioRepository
                .findByNombreContainingIgnoreCaseOrApellidoContainingIgnoreCaseOrDniContainingIgnoreCase(
                        texto, texto, texto)
                .stream()
                .limit(8)
                .map(s -> new SocioBusquedaDTO(
                        s.getId(),
                        s.getNombreCompleto(),
                        s.getDni(),
                        s.getEstado() != null ? s.getEstado().name() : "-"))
                .collect(Collectors.toList());
    }

    public Optional<Cuota> obtenerCuotaPendienteMasAntigua(Socio socio) {
        for (Cuota c : socio.getCuotas()) {
            cuotaService.actualizarEstadoCuota(c);
        }

        return socio.getCuotas().stream()
                .filter(c -> c.getFechaPago() == null)
                .min(Comparator.comparing(Cuota::getFechaVencimiento));
    }

    /**
     * Alta rápida de un socio nuevo: lo crea, le asigna un plan y genera
     * su primera cuota (queda pendiente de cobro, se cobra después desde
     * el panel de "cuota pendiente" en la misma pantalla).
     */
    public Socio altaRapidaConPlan(String nombre, String apellido, String dni, String telefono,
                                   Plan plan, LocalDate fechaInicio) {

        Socio socio = new Socio();
        socio.setNombre(nombre);
        socio.setApellido(apellido);
        socio.setDni(dni);
        socio.setTelefono(telefono);
        socio.setEstado(EstadoSocio.ACTIVO);

        Socio socioGuardado = socioRepository.save(socio);

        Cuota primeraCuota = asignarPlanYCrearPrimeraCuota(socioGuardado, plan, fechaInicio);
        socioRepository.save(socioGuardado);
        cuotaRepository.save(primeraCuota);

        return socioGuardado;
    }

    /**
     * Asigna un plan a un socio existente que no tenía uno activo,
     * y le genera la primera cuota de ese plan.
     */
    public void asignarPlanAExistente(Socio socio, Plan plan, LocalDate fechaInicio) {
        Cuota primeraCuota = asignarPlanYCrearPrimeraCuota(socio, plan, fechaInicio);
        socioRepository.save(socio);
        cuotaRepository.save(primeraCuota);
    }

    private Cuota asignarPlanYCrearPrimeraCuota(Socio socio, Plan plan, LocalDate fechaInicio) {
        LocalDate inicio = (fechaInicio != null) ? fechaInicio : LocalDate.now();

        socio.setPlan(plan);
        socio.setFechaInicioPlan(inicio);
        socio.setFechaVencimientoPlan(inicio.plusMonths(plan.getDuracionMeses()));

        Cuota primeraCuota = new Cuota();
        primeraCuota.setSocio(socio);
        primeraCuota.setMonto(plan.getPrecio());
        primeraCuota.setFechaVencimiento(socio.getFechaVencimientoPlan());
        primeraCuota.setEstado(EstadoCuota.PENDIENTE);

        return primeraCuota;
    }

    public void registrarPagoPorId(Long cuotaId, BigDecimal monto, String medioPago) {
        Cuota cuota = cuotaRepository.findById(cuotaId)
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada"));
        registrarPago(cuota, monto, medioPago);
    }

    public Pago registrarPago(Cuota cuota, BigDecimal monto, String medioPago) {
        Pago pago = new Pago();
        pago.setCuota(cuota);
        pago.setMonto(monto);
        pago.setMedioPago(medioPago);
        pago.setFechaPago(LocalDate.now());
        pagoRepository.save(pago);

        cuota.setFechaPago(LocalDate.now());
        cuotaService.actualizarEstadoCuota(cuota);
        cuotaRepository.save(cuota);

        Socio socio = cuota.getSocio();
        if (socio != null) {
            socioService.actualizarEstadoSocio(socio);

            if (socio.getPlan() != null) {
                generarSiguienteCuota(socio, cuota);
            }

            socioRepository.save(socio);
        }

        return pago;
    }

    private void generarSiguienteCuota(Socio socio, Cuota cuotaPagada) {
        LocalDate proximoVencimiento = cuotaPagada.getFechaVencimiento()
                .plusMonths(socio.getPlan().getDuracionMeses());

        boolean yaExiste = socio.getCuotas().stream()
                .anyMatch(c -> c.getFechaVencimiento() != null
                        && c.getFechaVencimiento().equals(proximoVencimiento));

        if (!yaExiste) {
            Cuota siguienteCuota = new Cuota();
            siguienteCuota.setSocio(socio);
            siguienteCuota.setMonto(socio.getPlan().getPrecio());
            siguienteCuota.setFechaVencimiento(proximoVencimiento);
            siguienteCuota.setEstado(EstadoCuota.PENDIENTE);
            cuotaRepository.save(siguienteCuota);

            socio.setFechaInicioPlan(cuotaPagada.getFechaVencimiento().plusDays(1));
            socio.setFechaVencimientoPlan(proximoVencimiento);
        }
    }
}
