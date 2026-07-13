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
        if (socio == null || socio.getCuotas() == null) {
            return Optional.empty();
        }

        for (Cuota c : socio.getCuotas()) {
            cuotaService.actualizarEstadoCuota(c);
        }

        return socio.getCuotas().stream()
                .filter(c -> c.getFechaPago() == null)
                .min(Comparator.comparing(Cuota::getFechaVencimiento));
    }

    /**
     * Alta rápida de un socio nuevo: lo crea, le asigna un plan y genera
     * su primera cuota. Si se indica cobro inicial, registra el pago de esa
     * primera cuota y genera automáticamente la próxima renovación.
     */
    public Socio altaRapidaConPlan(String nombre,
                                   String apellido,
                                   String dni,
                                   String telefono,
                                   String email,
                                   LocalDate fechaNacimiento,
                                   Plan plan,
                                   LocalDate fechaInicio) {
        return altaRapidaConPlanYCobro(
                nombre, apellido, dni, telefono, email, fechaNacimiento,
                plan, fechaInicio, false, null, null);
    }

    public Socio altaRapidaConPlanYCobro(String nombre,
                                         String apellido,
                                         String dni,
                                         String telefono,
                                         String email,
                                         LocalDate fechaNacimiento,
                                         Plan plan,
                                         LocalDate fechaInicio,
                                         boolean cobrarAlta,
                                         BigDecimal montoInicial,
                                         String medioPagoInicial) {

        Socio socio = new Socio();
        socio.setNombre(nombre);
        socio.setApellido(apellido);
        socio.setDni(dni);
        socio.setTelefono(telefono);
        socio.setEmail(email);
        socio.setFechaNacimiento(fechaNacimiento);
        socio.setEstado(EstadoSocio.ACTIVO);

        Socio socioGuardado = socioRepository.save(socio);

        Cuota primeraCuota = asignarPlanAExistente(socioGuardado, plan, fechaInicio);

        if (cobrarAlta) {
            BigDecimal montoACobrar = montoInicial != null ? montoInicial : plan.getPrecio();
            registrarPago(primeraCuota, montoACobrar, medioPagoInicial);
        }

        return socioGuardado;
    }

    /**
     * Asigna un plan a un socio existente y genera la primera cuota de ese plan.
     * Devuelve la cuota creada para poder cobrarla inmediatamente si corresponde.
     */
    public Cuota asignarPlanAExistente(Socio socio, Plan plan, LocalDate fechaInicio) {
        validarAsignacionPlan(socio, plan);
        Cuota primeraCuota = asignarPlanYCrearPrimeraCuota(socio, plan, fechaInicio);
        socioRepository.save(socio);
        return cuotaRepository.save(primeraCuota);
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
                .orElseThrow(() -> new IllegalArgumentException("Cuota no encontrada"));
        registrarPago(cuota, monto, medioPago);
    }

    public Pago registrarPago(Cuota cuota, BigDecimal monto, String medioPago) {
        validarPago(cuota, monto, medioPago);

        LocalDate fechaPago = LocalDate.now();

        Pago pago = new Pago();
        pago.setCuota(cuota);
        pago.setMonto(monto);
        pago.setMedioPago(medioPago.trim());
        pago.setFechaPago(fechaPago);
        pagoRepository.save(pago);

        cuota.setFechaPago(fechaPago);
        cuotaService.actualizarEstadoCuota(cuota);
        cuotaRepository.save(cuota);

        Socio socio = cuota.getSocio();
        if (socio != null) {
            socioService.actualizarEstadoSocio(socio);

            if (socio.getPlan() != null) {
                generarSiguienteCuota(socio, cuota, fechaPago);
            }

            socioRepository.save(socio);
        }

        return pago;
    }

    private void validarPago(Cuota cuota, BigDecimal monto, String medioPago) {
        if (cuota == null) {
            throw new IllegalArgumentException("La cuota no existe.");
        }
        if (cuota.getFechaPago() != null
                || (cuota.getId() != null && pagoRepository.existsByCuota_Id(cuota.getId()))) {
            throw new IllegalStateException("Esta cuota ya fue pagada.");
        }
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero.");
        }
        if (medioPago == null || medioPago.trim().isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar un medio de pago.");
        }
    }

    private void validarAsignacionPlan(Socio socio, Plan plan) {
        if (socio == null) {
            throw new IllegalArgumentException("El socio no existe.");
        }
        if (plan == null || !plan.isActivo()) {
            throw new IllegalArgumentException("El plan seleccionado no está activo.");
        }
        if (plan.getPrecio() == null || plan.getPrecio().compareTo(BigDecimal.ZERO) <= 0
                || plan.getDuracionMeses() <= 0) {
            throw new IllegalArgumentException("El plan seleccionado tiene datos inválidos.");
        }
    }

    private void generarSiguienteCuota(Socio socio, Cuota cuotaPagada, LocalDate fechaPago) {
        LocalDate vencimientoPagado = cuotaPagada.getFechaVencimiento();

        // Regla de negocio:
        // - Si el socio paga antes o el mismo día del vencimiento, conserva la fecha original de renovación.
        // - Si paga una cuota vencida, la nueva vigencia arranca desde la fecha real de pago.
        LocalDate baseRenovacion = (vencimientoPagado != null && vencimientoPagado.isBefore(fechaPago))
                ? fechaPago
                : vencimientoPagado;

        if (baseRenovacion == null) {
            baseRenovacion = fechaPago;
        }

        LocalDate proximoVencimiento = baseRenovacion.plusMonths(socio.getPlan().getDuracionMeses());

        boolean yaExiste = socio.getId() != null
                && cuotaRepository.existsBySocio_IdAndFechaVencimiento(socio.getId(), proximoVencimiento);

        if (!yaExiste) {
            Cuota siguienteCuota = new Cuota();
            siguienteCuota.setSocio(socio);
            siguienteCuota.setMonto(socio.getPlan().getPrecio());
            siguienteCuota.setFechaVencimiento(proximoVencimiento);
            siguienteCuota.setEstado(EstadoCuota.PENDIENTE);
            cuotaRepository.save(siguienteCuota);

            socio.setFechaInicioPlan(baseRenovacion);
            socio.setFechaVencimientoPlan(proximoVencimiento);
        }
    }
}
