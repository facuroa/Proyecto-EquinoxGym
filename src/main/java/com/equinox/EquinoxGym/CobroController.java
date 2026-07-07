package com.equinox.EquinoxGym;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/cobrar")
public class CobroController {

    private final SocioRepository socioRepository;
    private final PlanRepository planRepository;
    private final CobroService cobroService;

    public CobroController(SocioRepository socioRepository,
                           PlanRepository planRepository,
                           CobroService cobroService) {
        this.socioRepository = socioRepository;
        this.planRepository = planRepository;
        this.cobroService = cobroService;
    }

    /**
     * Pantalla principal de Cobro. Según los parámetros que llegan por la URL
     * (después de un redirect) decide qué mostrar: el buscador solo, el panel
     * de un socio ya seleccionado, o el formulario de alta rápida.
     */
    @GetMapping
    public String pantallaCobro(@RequestParam(required = false) Long socioId,
                                @RequestParam(required = false) String altaRapida,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) String mensaje,
                                @RequestParam(required = false) String error,
                                Model model) {

        model.addAttribute("planes", planRepository.findByActivoTrueOrderByDuracionMesesAsc());
        model.addAttribute("mensaje", mensaje);
        model.addAttribute("error", error);

        if (socioId != null) {
            Socio socio = socioRepository.findById(socioId).orElse(null);
            if (socio != null) {
                model.addAttribute("socio", socio);
                Optional<Cuota> cuotaPendiente = cobroService.obtenerCuotaPendienteMasAntigua(socio);
                model.addAttribute("cuotaPendiente", cuotaPendiente.orElse(null));
            }
        }

        if ("true".equals(altaRapida)) {
            model.addAttribute("mostrarAltaRapida", true);
            model.addAttribute("dniSugerido", (q != null && q.matches("\\d+")) ? q : "");
        }

        return "cobrar";
    }

    /**
     * Endpoint del autocompletado. Devuelve JSON (no una vista HTML) porque
     * el JavaScript de cobrar.html lo consume con fetch() para armar el
     * dropdown de sugerencias mientras el usuario tipea.
     */
    @GetMapping("/buscar")
    @ResponseBody
    public List<SocioBusquedaDTO> buscar(@RequestParam String q) {
        return cobroService.buscarSocios(q);
    }

    /** Alta rápida de un socio nuevo + su plan + su primera cuota, en un solo paso. */
    @PostMapping("/nuevo-socio")
    public String altaRapida(@RequestParam String nombre,
                             @RequestParam String apellido,
                             @RequestParam String dni,
                             @RequestParam(required = false) String telefono,
                             @RequestParam Long planId,
                             @RequestParam(required = false) String fechaInicioPlan) {

        if (dni == null || dni.trim().isEmpty()) {
            return "redirect:/cobrar?altaRapida=true&error=dniObligatorio";
        }

        if (socioRepository.findByDni(dni).isPresent()) {
            return "redirect:/cobrar?altaRapida=true&error=dniDuplicado";
        }

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan no encontrado"));

        LocalDate inicio = (fechaInicioPlan != null && !fechaInicioPlan.isBlank())
                ? LocalDate.parse(fechaInicioPlan)
                : LocalDate.now();

        Socio socio = cobroService.altaRapidaConPlan(nombre, apellido, dni, telefono, plan, inicio);

        return "redirect:/cobrar?socioId=" + socio.getId();
    }

    /** Asigna un plan a un socio existente que no tenía uno activo. */
    @PostMapping("/asignar-plan")
    public String asignarPlan(@RequestParam Long socioId,
                              @RequestParam Long planId,
                              @RequestParam(required = false) String fechaInicioPlan) {

        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new RuntimeException("Socio no encontrado"));
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan no encontrado"));

        LocalDate inicio = (fechaInicioPlan != null && !fechaInicioPlan.isBlank())
                ? LocalDate.parse(fechaInicioPlan)
                : LocalDate.now();

        cobroService.asignarPlanAExistente(socio, plan, inicio);

        return "redirect:/cobrar?socioId=" + socioId;
    }

    /** Cobra la cuota pendiente que se le mostró al usuario en pantalla. */
    @PostMapping("/pagar")
    public String pagar(@RequestParam Long cuotaId,
                        @RequestParam Long socioId,
                        @RequestParam BigDecimal monto,
                        @RequestParam String medioPago) {

        cobroService.registrarPagoPorId(cuotaId, monto, medioPago);

        return "redirect:/cobrar?socioId=" + socioId + "&mensaje=pagado";
    }
}
