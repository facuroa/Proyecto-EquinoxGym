package com.equinox.EquinoxGym;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Comparator;
import java.util.regex.Pattern;

@Controller
public class SocioController {

    private static final Pattern DNI_VALIDO = Pattern.compile("^[0-9]{6,12}$");
    private static final Pattern TELEFONO_VALIDO = Pattern.compile("^[+0-9()\\s-]{6,25}$");
    private static final Pattern EMAIL_VALIDO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> MEDIOS_PAGO = Set.of("Efectivo", "Transferencia", "Tarjeta");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SocioRepository socioRepository;
    private final PlanRepository planRepository;
    private final PagoRepository pagoRepository;
    private final SeguimientoMorosidadRepository seguimientoMorosidadRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final AsistenciaService asistenciaService;
    private final SocioService socioService;
    private final CobroService cobroService;
    private final AuditoriaService auditoriaService;
    private final EliminacionService eliminacionService;
    private final ReservaDni reservaDni;
    private final IndiceDniUnico indiceDniUnico;
    private final TransactionTemplate transaccion;

    private static final String DNI_REPETIDO_ALTA = "Ya existe un socio con ese DNI";
    private static final String DNI_REPETIDO_EDICION = "Ya existe otro socio con ese DNI. Si quedó cargado dos veces, "
            + "un administrador puede eliminar el repetido desde su ficha.";

    public SocioController(SocioRepository socioRepository,
                           PlanRepository planRepository,
                           PagoRepository pagoRepository,
                           SeguimientoMorosidadRepository seguimientoMorosidadRepository,
                           AsistenciaRepository asistenciaRepository,
                           AsistenciaService asistenciaService,
                           SocioService socioService,
                           CobroService cobroService,
                           AuditoriaService auditoriaService,
                           EliminacionService eliminacionService,
                           ReservaDni reservaDni,
                           IndiceDniUnico indiceDniUnico,
                           PlatformTransactionManager transactionManager) {
        this.socioRepository = socioRepository;
        this.planRepository = planRepository;
        this.pagoRepository = pagoRepository;
        this.seguimientoMorosidadRepository = seguimientoMorosidadRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.asistenciaService = asistenciaService;
        this.socioService = socioService;
        this.cobroService = cobroService;
        this.auditoriaService = auditoriaService;
        this.eliminacionService = eliminacionService;
        this.reservaDni = reservaDni;
        this.indiceDniUnico = indiceDniUnico;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    @GetMapping("/socios")
    public String socios(@RequestParam(name = "buscar", defaultValue = "") String buscar,
                         @RequestParam(name = "estado", defaultValue = "TODOS") String estado,
                         @RequestParam(name = "page", defaultValue = "0") int page,
                         Model model) {
        EstadoSocio estadoFiltro = null;
        try {
            if (!"TODOS".equalsIgnoreCase(estado)) {
                estadoFiltro = EstadoSocio.valueOf(estado.toUpperCase());
            }
        } catch (IllegalArgumentException ignored) {
            estado = "TODOS";
        }

        PageRequest paginacion = PageRequest.of(Math.max(page, 0), 15,
                Sort.by("apellido").ascending().and(Sort.by("nombre").ascending()));
        Page<Socio> pagina = socioRepository.buscarPaginado(buscar.trim(), estadoFiltro, paginacion);
        List<Socio> modificados = pagina.getContent().stream()
                .filter(socioService::actualizarEstadoSocio)
                .toList();
        if (!modificados.isEmpty()) {
            socioRepository.saveAll(modificados);
        }

        model.addAttribute("socios", pagina.getContent());
        model.addAttribute("buscar", buscar.trim());
        model.addAttribute("estadoSeleccionado", estado.toUpperCase());
        model.addAttribute("paginaActual", pagina.getNumber());
        model.addAttribute("totalPaginas", pagina.getTotalPages());
        model.addAttribute("totalElementos", pagina.getTotalElements());
        model.addAttribute("primerElemento", pagina.getNumber() * pagina.getSize());
        model.addAttribute("dnisRepetidos", socioRepository.dnisRepetidos());
        return "socios";
    }

    @GetMapping("/socios/nuevo")
    public String nuevoSocio(Model model) {
        model.addAttribute("socio", new Socio());
        model.addAttribute("planes", planRepository.findByActivoTrueOrderByDuracionMesesAsc());
        return "nuevo-socio";
    }

    @GetMapping("/socios/editar/{id}")
    public String editarSocio(@PathVariable Long id, Model model) {
        Optional<Socio> socioOpt = socioRepository.findById(id);
        if (socioOpt.isPresent()) {
            model.addAttribute("socio", socioOpt.get());
            model.addAttribute("planes", planRepository.findByActivoTrueOrderByDuracionMesesAsc());
            return "nuevo-socio";
        }
        return "redirect:/socios";
    }

    @GetMapping("/socios/ver/{id}")
    public String verSocio(@PathVariable Long id, Model model) {
        Socio socio = socioRepository.findById(id).orElse(null);
        if (socio == null) {
            return "redirect:/socios";
        }
        if (socioService.actualizarEstadoSocio(socio)) {
            socioRepository.save(socio);
        }

        List<Pago> pagos = pagoRepository.findByCuota_Socio_IdOrderByFechaPagoDescIdDesc(socio.getId());
        List<Pago> pagosRecientes = pagos.stream().limit(10).toList();
        BigDecimal totalPagado = pagos.stream()
                .filter(p -> !p.isAnulado())
                .map(Pago::getMonto)
                .filter(monto -> monto != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Cuota> cuotasOrdenadas = socio.getCuotas().stream()
                .sorted(Comparator.comparing(Cuota::getFechaVencimiento,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<Cuota> cuotasPendientes = cuotasOrdenadas.stream()
                .filter(c -> c.getFechaPago() == null)
                .toList();
        BigDecimal deudaPendiente = cuotasPendientes.stream()
                .map(Cuota::getMonto)
                .filter(monto -> monto != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("socio", socio);
        model.addAttribute("pagosRecientes", pagosRecientes);
        model.addAttribute("totalPagado", totalPagado);
        model.addAttribute("ultimoPago", pagos.stream().filter(p -> !p.isAnulado()).findFirst().orElse(null));
        model.addAttribute("cuotasOrdenadas", cuotasOrdenadas);
        model.addAttribute("cuotasPendientes", cuotasPendientes.size());
        model.addAttribute("deudaPendiente", deudaPendiente);
        model.addAttribute("proximaCuota", cuotasPendientes.stream()
                .min(Comparator.comparing(Cuota::getFechaVencimiento,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null));
        model.addAttribute("resumenAsistencia", asistenciaService.resumenDeSocio(socio));
        model.addAttribute("impactoEliminacion", eliminacionService.impactoSocio(socio));
        return "detalle-socio";
    }

    @PostMapping("/socios/guardar")
    public String guardarSocio(@ModelAttribute("socio") Socio socioForm,
                               BindingResult result,
                               @RequestParam(value = "planId", required = false) Long planId,
                               @RequestParam(value = "fechaInicioPlan", required = false) String fechaInicioPlanStr,
                               @RequestParam(value = "cobrarAlta", required = false) Boolean cobrarAlta,
                               @RequestParam(value = "montoInicial", required = false) BigDecimal montoInicial,
                               @RequestParam(value = "medioPagoInicial", required = false) String medioPagoInicial,
                               Model model,
                               RedirectAttributes redirectAttributes,
                               Authentication authentication) {

        normalizarDatos(socioForm);
        boolean esNuevo = (socioForm.getId() == null);
        boolean cobrarAltaMarcado = Boolean.TRUE.equals(cobrarAlta);
        Plan planSeleccionado = null;
        LocalDate inicio = null;

        validarDatosPersonales(socioForm, result);
        if (dniDuplicado(socioForm)) {
            result.rejectValue("dni", "error.socio", esNuevo ? DNI_REPETIDO_ALTA : DNI_REPETIDO_EDICION);
        }

        if (planId != null) {
            planSeleccionado = planRepository.findById(planId).orElse(null);
            if (planSeleccionado == null) {
                result.reject("error.socio", "El plan seleccionado ya no está disponible.");
            }
        }

        if (fechaInicioPlanStr != null && !fechaInicioPlanStr.isBlank()) {
            try {
                inicio = LocalDate.parse(fechaInicioPlanStr);
            } catch (DateTimeParseException e) {
                result.reject("error.socio", "Ingresá una fecha de inicio válida.");
            }
        }

        if (cobrarAltaMarcado && planSeleccionado == null) {
            model.addAttribute("errorCobro", "Para registrar el cobro inicial primero tenés que seleccionar un plan.");
            result.reject("error.socio", "Seleccione un plan para cobrar el alta.");
        }
        if (cobrarAltaMarcado && (medioPagoInicial == null || medioPagoInicial.trim().isEmpty())) {
            model.addAttribute("errorCobro", "Para cobrar el alta tenés que seleccionar un medio de pago.");
            result.reject("error.socio", "Seleccione un medio de pago para cobrar el alta.");
        }
        if (cobrarAltaMarcado && medioPagoInicial != null && !medioPagoInicial.isBlank()
                && !MEDIOS_PAGO.contains(medioPagoInicial.trim())) {
            result.reject("error.socio", "El medio de pago seleccionado no es válido.");
        }
        if (cobrarAltaMarcado && montoInicial != null && montoInicial.signum() <= 0) {
            result.reject("error.socio", "El monto inicial debe ser mayor a cero.");
        }
        if (cobrarAltaMarcado && !result.hasErrors()) {
            try {
                cobroService.validarCajaParaMedioPago(medioPagoInicial);
            } catch (CajaCerradaException e) {
                model.addAttribute("errorCobro", e.getMessage());
                result.reject("error.socio", e.getMessage());
            }
        }

        Socio socioActual = null;
        if (!esNuevo) {
            socioActual = socioRepository.findById(socioForm.getId()).orElse(null);
            if (socioActual == null) {
                return "redirect:/socios";
            }
        }

        if (result.hasErrors()) {
            return volverAlFormulario(model, socioForm, socioActual, planId, fechaInicioPlanStr,
                    cobrarAltaMarcado, montoInicial, medioPagoInicial);
        }

        if (inicio == null) {
            // Al editar, dejar el campo vacio conserva la fecha que ya tenia.
            inicio = socioActual != null && socioActual.getFechaInicioPlan() != null
                    ? socioActual.getFechaInicioPlan() : LocalDate.now();
        }

        // Todo el guardado va en una sola transaccion: si falla el cobro, no
        // queda un socio a medias con una cuota impaga desde el dia del alta.
        final Socio existente = socioActual;
        final Plan plan = planSeleccionado;
        final LocalDate fechaInicio = inicio;
        final String usuario = authentication != null ? authentication.getName() : null;
        ResultadoGuardado guardado;
        try {
            // De a un guardado por vez, y la transaccion se confirma antes de
            // soltar el candado (ver ReservaDni).
            guardado = reservaDni.ejecutar(() -> transaccion.execute(estado -> persistirSocio(socioForm, existente,
                    plan, fechaInicio, cobrarAltaMarcado, montoInicial, medioPagoInicial, usuario)));
        } catch (DataIntegrityViolationException e) {
            // Otro pedido (por ejemplo un doble clic) creo un socio con el
            // mismo DNI justo antes que este.
            result.rejectValue("dni", "error.socio", existente == null ? DNI_REPETIDO_ALTA : DNI_REPETIDO_EDICION);
            return volverAlFormulario(model, socioForm, socioActual, planId, fechaInicioPlanStr,
                    cobrarAltaMarcado, montoInicial, medioPagoInicial);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Incluye CajaCerradaException: la caja se cerro entre el control y el cobro.
            model.addAttribute("errorCobro", e.getMessage());
            result.reject("error.socio", e.getMessage());
            return volverAlFormulario(model, socioForm, socioActual, planId, fechaInicioPlanStr,
                    cobrarAltaMarcado, montoInicial, medioPagoInicial);
        }

        if (guardado.pagoInicialId() != null) {
            return "redirect:/pagos/" + guardado.pagoInicialId() + "/comprobante?origenSocio=" + guardado.socioId();
        }
        if (guardado.aviso() != null) {
            redirectAttributes.addFlashAttribute("error", guardado.aviso());
        } else {
            redirectAttributes.addFlashAttribute("mensaje", "Se guardaron los datos de " + guardado.nombre() + ".");
        }
        return "redirect:/socios";
    }

    /** Lo que hace falta saber despues de guardar, fuera de la transaccion. */
    private record ResultadoGuardado(Long socioId, String nombre, Long pagoInicialId, String aviso) { }

    private ResultadoGuardado persistirSocio(Socio socioForm,
                                             Socio socioActual,
                                             Plan planSeleccionado,
                                             LocalDate inicio,
                                             boolean cobrarAlta,
                                             BigDecimal montoInicial,
                                             String medioPagoInicial,
                                             String usuario) {
        boolean esNuevo = socioActual == null;
        // Se vuelve a controlar adentro del candado: el control del principio
        // pudo correr al mismo tiempo que otro guardado con el mismo DNI.
        if (dniDuplicado(socioForm)) {
            throw new DataIntegrityViolationException("DNI repetido: " + socioForm.getDni());
        }
        Socio socio = esNuevo ? new Socio() : socioActual;
        boolean teniaPlan = !esNuevo && socio.getPlan() != null;
        boolean teniaCuotas = !esNuevo && socio.tieneCuotas();

        socio.setNombre(socioForm.getNombre());
        socio.setApellido(socioForm.getApellido());
        socio.setDni(socioForm.getDni());
        socio.setTelefono(socioForm.getTelefono());
        socio.setEmail(socioForm.getEmail());
        socio.setDomicilioActual(socioForm.getDomicilioActual());
        socio.setFechaNacimiento(socioForm.getFechaNacimiento());
        socio.setTieneLesiones(socioForm.isTieneLesiones());
        socio.setDetalleLesiones(socioForm.isTieneLesiones() ? socioForm.getDetalleLesiones() : null);
        socio.setObservaciones(socioForm.getObservaciones());
        if (socio.getEstado() == null) {
            socio.setEstado(EstadoSocio.ACTIVO);
        }

        // El flush adelanta el chequeo del DNI unico de la base a este punto.
        Socio socioGuardado = socioRepository.saveAndFlush(socio);
        Pago pagoInicial = null;
        String aviso = null;

        if (planSeleccionado == null) {
            socioGuardado.setPlan(null);
            socioGuardado.setFechaInicioPlan(null);
            socioGuardado.setFechaVencimientoPlan(null);
            socioRepository.save(socioGuardado);
        } else if (esNuevo || !teniaPlan || !teniaCuotas) {
            Cuota cuotaInicial = cobroService.asignarPlanAExistente(
                    socioGuardado, planSeleccionado, inicio, cobrarAlta);
            if (cobrarAlta) {
                BigDecimal montoACobrar = montoInicial != null ? montoInicial : planSeleccionado.getPrecio();
                pagoInicial = cobroService.registrarPago(cuotaInicial, montoACobrar, medioPagoInicial);
            }
        } else {
            aviso = reprogramarCuotaPendiente(socioGuardado, planSeleccionado, inicio, usuario);
        }

        return new ResultadoGuardado(socioGuardado.getId(), socioGuardado.getNombreCompleto().trim(),
                pagoInicial != null ? pagoInicial.getId() : null, aviso);
    }

    /**
     * Cambiar el plan o la fecha de inicio de un socio que ya tiene cuotas
     * mueve su cuota pendiente al final del nuevo periodo, con la misma regla
     * que el alta sin cobro. Antes solo cambiaba la fecha "Vence plan" y la
     * cuota quedaba donde estaba: la pantalla mostraba "Faltan 21 dias" y a
     * la vez la cuota vencida.
     *
     * @return un aviso para mostrar si no se pudo mover ningun vencimiento
     */
    private String reprogramarCuotaPendiente(Socio socio, Plan plan, LocalDate inicio, String usuario) {
        boolean cambioPlan = socio.getPlan() == null || !socio.getPlan().getId().equals(plan.getId());
        boolean cambioInicio = !inicio.equals(socio.getFechaInicioPlan());
        socio.setPlan(plan);
        String aviso = null;

        if (cambioPlan || cambioInicio) {
            List<Cuota> impagas = socio.getCuotas().stream()
                    .filter(c -> c.getFechaPago() == null && c.getFechaVencimiento() != null)
                    .toList();
            if (impagas.isEmpty()) {
                socio.setFechaInicioPlan(inicio);
                socio.setFechaVencimientoPlan(inicio.plusMonths(plan.getDuracionMeses()));
            } else if (impagas.size() == 1) {
                Cuota pendiente = impagas.get(0);
                LocalDate vencimientoAnterior = pendiente.getFechaVencimiento();
                LocalDate vencimientoNuevo = inicio.plusMonths(plan.getDuracionMeses());
                pendiente.setFechaVencimiento(vencimientoNuevo);
                if (cambioPlan) {
                    pendiente.setMonto(plan.getPrecio());
                }
                auditoriaService.registrar(usuario, "Cuota reprogramada",
                        socio.getNombreCompleto().trim() + " (DNI " + socio.getDni() + ")"
                                + " · vencimiento " + FORMATO_FECHA.format(vencimientoAnterior)
                                + " → " + FORMATO_FECHA.format(vencimientoNuevo)
                                + (cambioPlan ? " · plan " + plan.getNombre() + " $ " + plan.getPrecio() : ""));
            } else {
                aviso = "Se guardaron los datos de " + socio.getNombreCompleto().trim() + ", pero tiene "
                        + impagas.size() + " cuotas impagas y no se movió ningún vencimiento. Revisalas en Cuotas.";
            }
        }

        // Recalcula el estado y deja "Vence plan" alineado con la cuota pendiente.
        socioService.actualizarEstadoSocio(socio);
        socioRepository.save(socio);
        return aviso;
    }

    /**
     * Vuelve a mostrar el formulario con los errores. Al editar se le pasan
     * las cuotas del socio para que no aparezca la opcion de cobro inicial,
     * que solo corresponde a socios sin cuotas.
     */
    private String volverAlFormulario(Model model, Socio socioForm, Socio socioActual, Long planId,
                                      String fechaInicioPlanStr, boolean cobrarAlta,
                                      BigDecimal montoInicial, String medioPagoInicial) {
        if (socioActual != null && socioActual.getCuotas() != null) {
            socioForm.setCuotas(new java.util.ArrayList<>(socioActual.getCuotas()));
        }
        prepararFormularioConErrores(model, planId, fechaInicioPlanStr, cobrarAlta, montoInicial, medioPagoInicial);
        return "nuevo-socio";
    }

    @PostMapping("/socios/eliminar/{id}")
    public String eliminarSocio(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        String usuario = authentication != null ? authentication.getName() : null;
        Boolean eliminado = transaccion.execute(estado -> {
            Socio socio = socioRepository.findById(id).orElse(null);
            if (socio == null) {
                return false;
            }
            if (pagoRepository.existsByCuota_Socio_Id(socio.getId())) {
                redirectAttributes.addFlashAttribute("error",
                        "No se puede eliminar a " + socio.getNombre() + " " + socio.getApellido()
                        + " porque tiene pagos registrados: borrarlo descuadraria la caja y los reportes. "
                        + "Usá \"Dar de baja\" para sacarlo de los listados sin perder el historial. "
                        + "Si fue un error de carga, un administrador puede eliminarlo definitivamente desde su ficha.");
                return false;
            }
            seguimientoMorosidadRepository.deleteBySocio_Id(socio.getId());
            asistenciaRepository.deleteBySocio_Id(socio.getId());
            socioRepository.delete(socio);
            auditoriaService.registrar(usuario,
                    "Socio eliminado", socio.getNombreCompleto().trim() + " (DNI " + socio.getDni() + ") · sin pagos");
            redirectAttributes.addFlashAttribute("mensaje",
                    "Se eliminó a " + socio.getNombre() + " " + socio.getApellido() + ".");
            return true;
        });

        if (Boolean.TRUE.equals(eliminado)) {
            // Afuera de la transaccion: si era un DNI repetido, ya se puede
            // activar el control en la base (ver IndiceDniUnico).
            indiceDniUnico.asegurar();
        }
        return "redirect:/socios";
    }

    @PostMapping("/socios/baja/{id}")
    @Transactional
    public String darDeBaja(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Socio socio = socioRepository.findById(id).orElse(null);

        if (socio != null) {
            socio.setBaja(true);
            socio.setFechaBaja(LocalDate.now());
            socio.setEstado(EstadoSocio.INACTIVO);
            socioRepository.save(socio);
            redirectAttributes.addFlashAttribute("mensaje",
                    socio.getNombre() + " " + socio.getApellido()
                    + " quedó inactivo. Su historial de pagos se conserva.");
        }

        return "redirect:/socios";
    }

    @PostMapping("/socios/reactivar/{id}")
    @Transactional
    public String reactivar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Socio socio = socioRepository.findById(id).orElse(null);

        if (socio != null) {
            socio.setBaja(false);
            socio.setFechaBaja(null);
            // El estado real (activo o moroso) lo resuelve el recalculo segun
            // las cuotas que tenga pendientes.
            socioService.actualizarEstadoSocio(socio);
            socioRepository.save(socio);
            redirectAttributes.addFlashAttribute("mensaje",
                    socio.getNombre() + " " + socio.getApellido() + " volvió a estar activo.");
        }

        return "redirect:/socios";
    }

    private boolean dniDuplicado(Socio socio) {
        if (socio.getDni() == null || socio.getDni().trim().isEmpty()) return false;
        return socio.getId() == null
                ? socioRepository.existsByDni(socio.getDni())
                : socioRepository.existsByDniAndIdNot(socio.getDni(), socio.getId());
    }

    private void validarDatosPersonales(Socio socio, BindingResult result) {
        if (socio.getNombre() == null || socio.getNombre().isBlank()) {
            result.rejectValue("nombre", "error.socio", "El nombre es obligatorio.");
        } else if (socio.getNombre().length() > 80) {
            result.rejectValue("nombre", "error.socio", "El nombre no puede superar los 80 caracteres.");
        }

        if (socio.getApellido() == null || socio.getApellido().isBlank()) {
            result.rejectValue("apellido", "error.socio", "El apellido es obligatorio.");
        } else if (socio.getApellido().length() > 80) {
            result.rejectValue("apellido", "error.socio", "El apellido no puede superar los 80 caracteres.");
        }

        if (socio.getDni() == null || socio.getDni().isBlank()) {
            result.rejectValue("dni", "error.socio", "El DNI es obligatorio.");
        } else if (!DNI_VALIDO.matcher(socio.getDni()).matches()) {
            result.rejectValue("dni", "error.socio", "Ingresá un DNI de 6 a 12 números.");
        }

        if (socio.getTelefono() != null && !socio.getTelefono().isBlank()
                && !TELEFONO_VALIDO.matcher(socio.getTelefono()).matches()) {
            result.rejectValue("telefono", "error.socio", "Ingresá un teléfono válido.");
        }
        if (socio.getEmail() != null && !socio.getEmail().isBlank()
                && !EMAIL_VALIDO.matcher(socio.getEmail()).matches()) {
            result.rejectValue("email", "error.socio", "Ingresá un correo electrónico válido.");
        }
        if (socio.getDomicilioActual() != null && socio.getDomicilioActual().length() > 255) {
            result.rejectValue("domicilioActual", "error.socio", "El domicilio no puede superar los 255 caracteres.");
        }
        if (socio.getFechaNacimiento() != null && socio.getFechaNacimiento().isAfter(LocalDate.now())) {
            result.rejectValue("fechaNacimiento", "error.socio", "La fecha de nacimiento no puede ser futura.");
        }
        if (socio.isTieneLesiones()
                && (socio.getDetalleLesiones() == null || socio.getDetalleLesiones().isBlank())) {
            result.rejectValue("detalleLesiones", "error.socio",
                    "Describí la lesión o condición para que el equipo pueda tenerla en cuenta.");
        }
        if (socio.getDetalleLesiones() != null && socio.getDetalleLesiones().length() > 1000) {
            result.rejectValue("detalleLesiones", "error.socio", "El detalle no puede superar los 1000 caracteres.");
        }
        if (socio.getObservaciones() != null && socio.getObservaciones().length() > 1000) {
            result.rejectValue("observaciones", "error.socio", "Las observaciones no pueden superar los 1000 caracteres.");
        }
    }

    private void normalizarDatos(Socio socio) {
        socio.setNombre(limpiar(socio.getNombre()));
        socio.setApellido(limpiar(socio.getApellido()));
        socio.setDni(limpiar(socio.getDni()) == null ? null : limpiar(socio.getDni()).replaceAll("[.\\s-]", ""));
        socio.setTelefono(limpiar(socio.getTelefono()));
        socio.setEmail(limpiar(socio.getEmail()));
        socio.setDomicilioActual(limpiar(socio.getDomicilioActual()));
        socio.setDetalleLesiones(limpiar(socio.getDetalleLesiones()));
        socio.setObservaciones(limpiar(socio.getObservaciones()));
    }

    private String limpiar(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private void prepararFormularioConErrores(Model model,
                                              Long planId,
                                              String fechaInicioPlan,
                                              boolean cobrarAlta,
                                              BigDecimal montoInicial,
                                              String medioPagoInicial) {
        model.addAttribute("planes", planRepository.findByActivoTrueOrderByDuracionMesesAsc());
        model.addAttribute("planIdSeleccionado", planId);
        model.addAttribute("fechaInicioPlanIngresada", fechaInicioPlan);
        model.addAttribute("cobrarAltaIngresado", cobrarAlta);
        model.addAttribute("montoInicialIngresado", montoInicial);
        model.addAttribute("medioPagoInicialIngresado", medioPagoInicial);
    }
}
