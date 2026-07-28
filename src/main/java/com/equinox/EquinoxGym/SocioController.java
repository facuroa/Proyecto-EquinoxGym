package com.equinox.EquinoxGym;

import org.springframework.stereotype.Controller;
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

    private final SocioRepository socioRepository;
    private final PlanRepository planRepository;
    private final PagoRepository pagoRepository;
    private final SeguimientoMorosidadRepository seguimientoMorosidadRepository;
    private final SocioService socioService;
    private final CobroService cobroService;

    public SocioController(SocioRepository socioRepository,
                           PlanRepository planRepository,
                           PagoRepository pagoRepository,
                           SeguimientoMorosidadRepository seguimientoMorosidadRepository,
                           SocioService socioService,
                           CobroService cobroService) {
        this.socioRepository = socioRepository;
        this.planRepository = planRepository;
        this.pagoRepository = pagoRepository;
        this.seguimientoMorosidadRepository = seguimientoMorosidadRepository;
        this.socioService = socioService;
        this.cobroService = cobroService;
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
                               Model model) {

        normalizarDatos(socioForm);
        boolean esNuevo = (socioForm.getId() == null);
        boolean cobrarAltaMarcado = Boolean.TRUE.equals(cobrarAlta);
        Plan planSeleccionado = null;
        LocalDate inicio = null;

        validarDatosPersonales(socioForm, result);
        if (dniDuplicado(socioForm)) {
            result.rejectValue("dni", "error.socio", "Ya existe un socio con ese DNI");
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

        if (result.hasErrors()) {
            prepararFormularioConErrores(model, planId, fechaInicioPlanStr, cobrarAltaMarcado,
                    montoInicial, medioPagoInicial);
            return "nuevo-socio";
        }

        Socio socioPersistente;
        Pago pagoInicial = null;
        boolean teniaPlan = false;
        boolean teniaCuotas = false;

        if (esNuevo) {
            socioPersistente = new Socio();
        } else {
            socioPersistente = socioRepository.findById(socioForm.getId()).orElse(null);
            if (socioPersistente == null) {
                return "redirect:/socios";
            }
            teniaPlan = socioPersistente.getPlan() != null;
            teniaCuotas = socioPersistente.getCuotas() != null && !socioPersistente.getCuotas().isEmpty();
        }

        socioPersistente.setNombre(socioForm.getNombre());
        socioPersistente.setApellido(socioForm.getApellido());
        socioPersistente.setDni(socioForm.getDni());
        socioPersistente.setTelefono(socioForm.getTelefono());
        socioPersistente.setEmail(socioForm.getEmail());
        socioPersistente.setDomicilioActual(socioForm.getDomicilioActual());
        socioPersistente.setFechaNacimiento(socioForm.getFechaNacimiento());
        socioPersistente.setTieneLesiones(socioForm.isTieneLesiones());
        socioPersistente.setDetalleLesiones(
                socioForm.isTieneLesiones() ? socioForm.getDetalleLesiones() : null);
        socioPersistente.setObservaciones(socioForm.getObservaciones());

        if (socioPersistente.getEstado() == null) {
            socioPersistente.setEstado(EstadoSocio.ACTIVO);
        }

        if (inicio == null) {
            inicio = LocalDate.now();
        }

        Socio socioGuardado = socioRepository.save(socioPersistente);

        if (planSeleccionado != null) {
            if (esNuevo || !teniaPlan || !teniaCuotas) {
                Cuota cuotaInicial = cobroService.asignarPlanAExistente(
                        socioGuardado, planSeleccionado, inicio, cobrarAltaMarcado);
                if (cobrarAltaMarcado) {
                    BigDecimal montoACobrar = montoInicial != null ? montoInicial : planSeleccionado.getPrecio();
                    pagoInicial = cobroService.registrarPago(cuotaInicial, montoACobrar, medioPagoInicial);
                }
            } else {
                socioGuardado.setPlan(planSeleccionado);
                socioGuardado.setFechaInicioPlan(inicio);
                socioGuardado.setFechaVencimientoPlan(inicio.plusMonths(planSeleccionado.getDuracionMeses()));
                socioRepository.save(socioGuardado);
            }
        } else {
            socioGuardado.setPlan(null);
            socioGuardado.setFechaInicioPlan(null);
            socioGuardado.setFechaVencimientoPlan(null);
            socioRepository.save(socioGuardado);
        }

        if (pagoInicial != null) {
            return "redirect:/pagos/" + pagoInicial.getId() + "/comprobante?origenSocio=" + socioGuardado.getId();
        }
        return "redirect:/socios";
    }

    @PostMapping("/socios/eliminar/{id}")
    @Transactional
    public String eliminarSocio(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Socio socio = socioRepository.findById(id).orElse(null);

        if (socio != null) {
            if (pagoRepository.existsByCuota_Socio_Id(socio.getId())) {
                redirectAttributes.addFlashAttribute("error",
                        "No se puede eliminar un socio con historial de pagos. Podés dejarlo inactivo.");
                return "redirect:/socios";
            }
            seguimientoMorosidadRepository.deleteBySocio_Id(socio.getId());
            socioRepository.delete(socio);
        }

        return "redirect:/socios";
    }

    private boolean dniDuplicado(Socio socio) {
        if (socio.getDni() == null || socio.getDni().trim().isEmpty()) return false;
        Optional<Socio> existente = socioRepository.findByDni(socio.getDni());
        if (existente.isEmpty()) return false;
        if (socio.getId() == null) return true;
        return !existente.get().getId().equals(socio.getId());
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
