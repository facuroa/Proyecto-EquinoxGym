package com.equinox.EquinoxGym;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final CajaService cajaService;
    private final AuditoriaService auditoriaService;

    public CobroService(SocioRepository socioRepository,
                        CuotaRepository cuotaRepository,
                        PagoRepository pagoRepository,
                        CuotaService cuotaService,
                        SocioService socioService,
                        CajaService cajaService,
                        AuditoriaService auditoriaService) {
        this.socioRepository = socioRepository;
        this.cuotaRepository = cuotaRepository;
        this.pagoRepository = pagoRepository;
        this.cuotaService = cuotaService;
        this.socioService = socioService;
        this.cajaService = cajaService;
        this.auditoriaService = auditoriaService;
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

        return altaRapidaConPlanYCobro(nombre, apellido, dni, telefono, email, null,
                fechaNacimiento, false, null, plan, fechaInicio,
                cobrarAlta, montoInicial, medioPagoInicial);
    }

    public Socio altaRapidaConPlanYCobro(String nombre,
                                         String apellido,
                                         String dni,
                                         String telefono,
                                         String email,
                                         String domicilioActual,
                                         LocalDate fechaNacimiento,
                                         boolean tieneLesiones,
                                         String detalleLesiones,
                                         Plan plan,
                                         LocalDate fechaInicio,
                                         boolean cobrarAlta,
                                         BigDecimal montoInicial,
                                         String medioPagoInicial) {

        if (cobrarAlta) {
            cajaService.validarCajaParaPago(medioPagoInicial, usuarioActual());
        }

        Socio socio = new Socio();
        socio.setNombre(nombre);
        socio.setApellido(apellido);
        socio.setDni(dni);
        socio.setTelefono(telefono);
        socio.setEmail(email);
        socio.setDomicilioActual(domicilioActual);
        socio.setFechaNacimiento(fechaNacimiento);
        socio.setTieneLesiones(tieneLesiones);
        socio.setDetalleLesiones(tieneLesiones ? detalleLesiones : null);
        socio.setEstado(EstadoSocio.ACTIVO);

        Socio socioGuardado = socioRepository.save(socio);

        Cuota primeraCuota = asignarPlanAExistente(socioGuardado, plan, fechaInicio, cobrarAlta);

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
        return asignarPlanAExistente(socio, plan, fechaInicio, true);
    }

    public Cuota asignarPlanAExistente(Socio socio,
                                       Plan plan,
                                       LocalDate fechaInicio,
                                       boolean cobrarAlta) {
        validarAsignacionPlan(socio, plan);
        Cuota primeraCuota = asignarPlanYCrearPrimeraCuota(socio, plan, fechaInicio, cobrarAlta);
        socioRepository.save(socio);
        return cuotaRepository.save(primeraCuota);
    }

    private Cuota asignarPlanYCrearPrimeraCuota(Socio socio,
                                                Plan plan,
                                                LocalDate fechaInicio,
                                                boolean cobrarAlta) {
        LocalDate inicio = (fechaInicio != null) ? fechaInicio : LocalDate.now();
        LocalDate finPeriodo = inicio.plusMonths(plan.getDuracionMeses());

        socio.setPlan(plan);
        socio.setFechaInicioPlan(inicio);
        socio.setFechaVencimientoPlan(finPeriodo);

        Cuota primeraCuota = new Cuota();
        primeraCuota.setSocio(socio);
        primeraCuota.setMonto(plan.getPrecio());
        // Con cobro de alta, la cuota vence al iniciar y su pago genera la renovación
        // al final del período. Sin cobro inicial, la primera cuota queda programada
        // para el final del período y no genera morosidad desde el día del alta.
        primeraCuota.setFechaVencimiento(cobrarAlta ? inicio : finPeriodo);
        primeraCuota.setEstado(EstadoCuota.PENDIENTE);

        return primeraCuota;
    }

    public Pago registrarPagoPorId(Long cuotaId, BigDecimal monto, String medioPago) {
        Cuota cuota = cuotaRepository.findByIdForUpdate(cuotaId)
                .orElseThrow(() -> new IllegalArgumentException("Cuota no encontrada"));
        return registrarPago(cuota, monto, medioPago);
    }

    public Pago registrarPago(Cuota cuota, BigDecimal monto, String medioPago) {
        validarPago(cuota, monto, medioPago);
        String usuario = usuarioActual();
        cajaService.validarCajaParaPago(medioPago, usuario);

        LocalDate fechaPago = LocalDate.now();

        Pago pago = new Pago();
        pago.setCuota(cuota);
        pago.setMonto(monto);
        pago.setMedioPago(medioPago.trim());
        pago.setFechaPago(fechaPago);
        pago.setFechaRegistro(LocalDateTime.now());
        pago.setRegistradoPor(usuario);

        cuota.setFechaPago(fechaPago);
        cuotaService.actualizarEstadoCuota(cuota);
        cuotaRepository.save(cuota);

        Socio socio = cuota.getSocio();
        if (socio != null) {
            socioService.actualizarEstadoSocio(socio);

            if (socio.getPlan() != null) {
                pago.setCuotaRenovacionGenerada(generarSiguienteCuota(socio, cuota, fechaPago));
            }

            socioRepository.save(socio);
        }

        Pago pagoGuardado = pagoRepository.save(pago);
        cajaService.registrarPago(pagoGuardado, usuario);
        String socioNombre = cuota.getSocio() == null ? "socio" : cuota.getSocio().getNombreCompleto();
        auditoriaService.registrar(usuario, "Pago registrado",
                "Cuota de " + socioNombre + " · $ " + pagoGuardado.getMonto()
                        + " · " + pagoGuardado.getMedioPago());
        return pagoGuardado;
    }

    public void anularPago(Long pagoId, String motivo) {
        if (motivo == null || motivo.trim().length() < 5) {
            throw new IllegalArgumentException("Ingresá un motivo de al menos 5 caracteres.");
        }

        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado."));
        if (pago.isAnulado()) {
            throw new IllegalStateException("Este pago ya fue anulado.");
        }
        if (pago.getFechaRegistro() == null) {
            throw new IllegalStateException(
                    "Este pago es anterior al sistema de auditoría y requiere una revisión manual.");
        }

        Cuota renovacion = pago.getCuotaRenovacionGenerada();
        if (renovacion != null) {
            boolean renovacionConMovimientos = renovacion.getFechaPago() != null
                    || pagoRepository.existsByCuota_IdAndAnuladoFalse(renovacion.getId());
            if (renovacionConMovimientos) {
                throw new IllegalStateException(
                        "No se puede anular porque la renovación siguiente ya tiene un pago registrado.");
            }
            pago.setCuotaRenovacionGenerada(null);
            pagoRepository.saveAndFlush(pago);
            Socio socioRenovacion = renovacion.getSocio();
            if (socioRenovacion != null && socioRenovacion.getCuotas() != null) {
                socioRenovacion.getCuotas().removeIf(c -> c.getId() != null && c.getId().equals(renovacion.getId()));
            }
            cuotaRepository.delete(renovacion);
        }

        String usuario = usuarioActual();
        cajaService.validarCajaParaPago(pago.getMedioPago(), usuario);

        pago.setAnulado(true);
        pago.setFechaAnulacion(LocalDateTime.now());
        pago.setAnuladoPor(usuario);
        pago.setMotivoAnulacion(motivo.trim());

        Cuota cuotaOriginal = pago.getCuota();
        cuotaOriginal.setFechaPago(null);
        cuotaService.actualizarEstadoCuota(cuotaOriginal);
        cuotaRepository.save(cuotaOriginal);

        Socio socio = cuotaOriginal.getSocio();
        if (socio != null) {
            if (socio.getPlan() != null && cuotaOriginal.getFechaVencimiento() != null) {
                socio.setFechaVencimientoPlan(cuotaOriginal.getFechaVencimiento());
                socio.setFechaInicioPlan(cuotaOriginal.getFechaVencimiento()
                        .minusMonths(socio.getPlan().getDuracionMeses()));
            }
            socioService.actualizarEstadoSocio(socio);
            socioRepository.save(socio);
        }

        pagoRepository.save(pago);
        cajaService.registrarAnulacionPago(pago, usuario);
        String socioNombre = cuotaOriginal.getSocio() == null ? "socio" : cuotaOriginal.getSocio().getNombreCompleto();
        auditoriaService.registrar(usuario, "Pago anulado",
                "Cuota de " + socioNombre + " · motivo: " + motivo.trim());
    }

    public void validarCajaParaMedioPago(String medioPago) {
        cajaService.validarCajaParaPago(medioPago, usuarioActual());
    }

    private void validarPago(Cuota cuota, BigDecimal monto, String medioPago) {
        if (cuota == null) {
            throw new IllegalArgumentException("La cuota no existe.");
        }
        if (cuota.getFechaPago() != null
                || (cuota.getId() != null && pagoRepository.existsByCuota_IdAndAnuladoFalse(cuota.getId()))) {
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

    private Cuota generarSiguienteCuota(Socio socio, Cuota cuotaPagada, LocalDate fechaPago) {
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
            return siguienteCuota;
        }
        return null;
    }

    private String usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return "sistema";
        }
        return authentication.getName();
    }
}
