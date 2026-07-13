package com.equinox.EquinoxGym;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
public class SocioController {

    private final SocioRepository socioRepository;
    private final PlanRepository planRepository;
    private final PagoRepository pagoRepository;
    private final SocioService socioService;
    private final CobroService cobroService;

    public SocioController(SocioRepository socioRepository,
                           PlanRepository planRepository,
                           PagoRepository pagoRepository,
                           SocioService socioService,
                           CobroService cobroService) {
        this.socioRepository = socioRepository;
        this.planRepository = planRepository;
        this.pagoRepository = pagoRepository;
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
        model.addAttribute("socio", socio);
        return "detalle-socio";
    }

    @PostMapping("/socios/guardar")
    public String guardarSocio(@ModelAttribute("socio") Socio socioForm,
                               @RequestParam(value = "planId", required = false) Long planId,
                               @RequestParam(value = "fechaInicioPlan", required = false) String fechaInicioPlanStr,
                               @RequestParam(value = "cobrarAlta", required = false) Boolean cobrarAlta,
                               @RequestParam(value = "montoInicial", required = false) BigDecimal montoInicial,
                               @RequestParam(value = "medioPagoInicial", required = false) String medioPagoInicial,
                               BindingResult result,
                               Model model) {

        boolean esNuevo = (socioForm.getId() == null);
        boolean cobrarAltaMarcado = Boolean.TRUE.equals(cobrarAlta);
        Plan planSeleccionado = null;

        if (socioForm.getDni() == null || socioForm.getDni().trim().isEmpty()) {
            result.rejectValue("dni", "error.socio", "El DNI es obligatorio");
        }
        if (dniDuplicado(socioForm)) {
            result.rejectValue("dni", "error.socio", "Ya existe un socio con ese DNI");
        }

        if (planId != null) {
            planSeleccionado = planRepository.findById(planId).orElse(null);
        }

        if (cobrarAltaMarcado && planSeleccionado == null) {
            model.addAttribute("errorCobro", "Para registrar el cobro inicial primero tenés que seleccionar un plan.");
            result.reject("error.socio", "Seleccione un plan para cobrar el alta.");
        }
        if (cobrarAltaMarcado && (medioPagoInicial == null || medioPagoInicial.trim().isEmpty())) {
            model.addAttribute("errorCobro", "Para cobrar el alta tenés que seleccionar un medio de pago.");
            result.reject("error.socio", "Seleccione un medio de pago para cobrar el alta.");
        }

        if (result.hasErrors()) {
            model.addAttribute("planes", planRepository.findByActivoTrueOrderByDuracionMesesAsc());
            return "nuevo-socio";
        }

        Socio socioPersistente;
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

        LocalDate inicio = (fechaInicioPlanStr != null && !fechaInicioPlanStr.isEmpty())
                ? LocalDate.parse(fechaInicioPlanStr)
                : LocalDate.now();

        Socio socioGuardado = socioRepository.save(socioPersistente);

        if (planSeleccionado != null) {
            if (esNuevo || !teniaPlan || !teniaCuotas) {
                Cuota cuotaInicial = cobroService.asignarPlanAExistente(socioGuardado, planSeleccionado, inicio);
                if (cobrarAltaMarcado) {
                    BigDecimal montoACobrar = montoInicial != null ? montoInicial : planSeleccionado.getPrecio();
                    cobroService.registrarPago(cuotaInicial, montoACobrar, medioPagoInicial);
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

        return "redirect:/socios";
    }

    @PostMapping("/socios/eliminar/{id}")
    public String eliminarSocio(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Socio socio = socioRepository.findById(id).orElse(null);

        if (socio != null) {
            if (pagoRepository.existsByCuota_Socio_Id(socio.getId())) {
                redirectAttributes.addFlashAttribute("error",
                        "No se puede eliminar un socio con historial de pagos. Podés dejarlo inactivo.");
                return "redirect:/socios";
            }
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
}
