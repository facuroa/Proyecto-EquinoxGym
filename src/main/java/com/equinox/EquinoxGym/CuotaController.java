package com.equinox.EquinoxGym;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;

@Controller
public class CuotaController {

    private final CuotaRepository cuotaRepository;
    private final SocioRepository socioRepository;
    private final PagoRepository pagoRepository;
    private final CuotaService cuotaService;
    private final SocioService socioService;
    private final EliminacionService eliminacionService;

    public CuotaController(CuotaRepository cuotaRepository,
                           SocioRepository socioRepository,
                           PagoRepository pagoRepository,
                           CuotaService cuotaService,
                           SocioService socioService,
                           EliminacionService eliminacionService) {
        this.cuotaRepository = cuotaRepository;
        this.socioRepository = socioRepository;
        this.pagoRepository = pagoRepository;
        this.cuotaService = cuotaService;
        this.socioService = socioService;
        this.eliminacionService = eliminacionService;
    }

    @GetMapping("/cuotas")
    public String listarCuotas(@RequestParam(name = "estado", defaultValue = "TODAS") String estado,
                               @RequestParam(name = "buscar", defaultValue = "") String buscar,
                               @RequestParam(name = "page", defaultValue = "0") int page,
                               Model model) {
        EstadoCuota estadoFiltro = null;
        try {
            if (!"TODAS".equalsIgnoreCase(estado)) {
                estadoFiltro = EstadoCuota.valueOf(estado.toUpperCase());
            }
        } catch (IllegalArgumentException ignored) {
            estado = "TODAS";
        }

        PageRequest paginacion = PageRequest.of(Math.max(page, 0), 15,
                Sort.by("fechaVencimiento").descending().and(Sort.by("id").descending()));
        Page<Cuota> pagina = cuotaRepository.buscarPaginado(buscar.trim(), estadoFiltro, paginacion);
        List<Cuota> modificadas = pagina.getContent().stream()
                .filter(cuotaService::actualizarEstadoCuota)
                .toList();
        if (!modificadas.isEmpty()) {
            cuotaRepository.saveAll(modificadas);
        }

        model.addAttribute("cuotas", pagina.getContent());
        model.addAttribute("buscar", buscar.trim());
        model.addAttribute("estadoSeleccionado", estado.toUpperCase());
        model.addAttribute("paginaActual", pagina.getNumber());
        model.addAttribute("totalPaginas", pagina.getTotalPages());
        model.addAttribute("totalElementos", pagina.getTotalElements());
        model.addAttribute("primerElemento", pagina.getNumber() * pagina.getSize());

        return "cuotas";
    }

    @GetMapping("/cuotas/nueva")
    public String mostrarFormularioNuevaCuota(Model model) {
        Cuota cuota = new Cuota();
        cuota.setFechaVencimiento(LocalDate.now());
        cuota.setEstado(EstadoCuota.PENDIENTE);

        model.addAttribute("cuota", cuota);
        model.addAttribute("socios", socioRepository.findAll());

        return "nueva-cuota";
    }

    @PostMapping("/cuotas/guardar")
    public String guardarCuota(@ModelAttribute("cuota") Cuota cuota,
                               BindingResult result,
                               Model model) {

        if (cuota.getSocioId() == null) {
            result.rejectValue("socioId", "error.cuota", "Debe seleccionar un socio");
        } else if (!socioRepository.existsById(cuota.getSocioId())) {
            result.rejectValue("socioId", "error.cuota", "El socio seleccionado ya no está disponible");
        }

        if (cuota.getMonto() == null) {
            result.rejectValue("monto", "error.cuota", "El monto es obligatorio");
        } else if (cuota.getMonto().signum() <= 0) {
            result.rejectValue("monto", "error.cuota", "El monto debe ser mayor a cero");
        }

        if (cuota.getFechaVencimiento() == null) {
            result.rejectValue("fechaVencimiento", "error.cuota", "La fecha de vencimiento es obligatoria");
        }

        if (result.hasErrors()) {
            model.addAttribute("socios", socioRepository.findAll());
            return "nueva-cuota";
        }

        Socio socio = socioRepository.findById(cuota.getSocioId())
                .orElseThrow(() -> new RuntimeException("Socio no encontrado"));

        cuota.setSocio(socio);
        cuota.setFechaPago(null);
        cuota.setEstado(EstadoCuota.PENDIENTE);

        cuotaService.actualizarEstadoCuota(cuota);
        cuotaRepository.save(cuota);

        socioService.actualizarEstadoSocio(socio);
        socioRepository.save(socio);

        return "redirect:/cuotas";
    }

    @GetMapping("/cuotas/editar/{id}")
    public String editarCuota(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Cuota cuota = cuotaRepository.findById(id).orElse(null);
        if (cuota == null) {
            redirectAttributes.addFlashAttribute("error", "Esa cuota ya no existe.");
            return "redirect:/cuotas";
        }

        if (cuota.getSocio() != null) {
            cuota.setSocioId(cuota.getSocio().getId());
        }

        cuotaService.actualizarEstadoCuota(cuota);
        cuotaRepository.save(cuota);

        model.addAttribute("cuota", cuota);
        model.addAttribute("socios", socioRepository.findAll());

        return "editar-cuota";
    }

    @PostMapping("/cuotas/actualizar/{id}")
    public String actualizarCuota(@PathVariable Long id,
                                  @ModelAttribute("cuota") Cuota cuotaForm,
                                  BindingResult result,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {

        Cuota cuota = cuotaRepository.findById(id).orElse(null);
        if (cuota == null) {
            redirectAttributes.addFlashAttribute("error", "Esa cuota ya no existe.");
            return "redirect:/cuotas";
        }

        if (cuotaForm.getSocioId() == null) {
            result.rejectValue("socioId", "error.cuota", "Debe seleccionar un socio");
        } else if (!socioRepository.existsById(cuotaForm.getSocioId())) {
            result.rejectValue("socioId", "error.cuota", "El socio seleccionado ya no está disponible");
        }

        if (cuotaForm.getMonto() == null) {
            result.rejectValue("monto", "error.cuota", "El monto es obligatorio");
        } else if (cuotaForm.getMonto().signum() <= 0) {
            result.rejectValue("monto", "error.cuota", "El monto debe ser mayor a cero");
        }

        if (cuotaForm.getFechaVencimiento() == null) {
            result.rejectValue("fechaVencimiento", "error.cuota", "La fecha de vencimiento es obligatoria");
        }

        if (result.hasErrors()) {
            model.addAttribute("socios", socioRepository.findAll());
            return "editar-cuota";
        }

        Socio socio = socioRepository.findById(cuotaForm.getSocioId()).orElse(null);
        if (socio == null) {
            redirectAttributes.addFlashAttribute("error", "El socio seleccionado ya no está disponible.");
            return "redirect:/cuotas";
        }
        Socio socioAnterior = cuota.getSocio();

        cuota.setFechaVencimiento(cuotaForm.getFechaVencimiento());
        cuota.setMonto(cuotaForm.getMonto());
        cuota.setSocio(socio);
        // Si la cuota pasa a otro socio, las dos listas tienen que enterarse:
        // si no, el socio anterior la vuelve a grabar como propia.
        if (socioAnterior != null && !socioAnterior.getId().equals(socio.getId())) {
            socioAnterior.getCuotas().removeIf(c -> c.getId() != null && c.getId().equals(cuota.getId()));
            socio.getCuotas().add(cuota);
        }

        cuotaService.actualizarEstadoCuota(cuota);
        cuotaRepository.save(cuota);

        // Recalcula el estado y deja "Vence plan" igual a la cuota pendiente.
        socioService.actualizarEstadoSocio(socio);
        socioRepository.save(socio);
        if (socioAnterior != null && !socioAnterior.getId().equals(socio.getId())) {
            socioService.actualizarEstadoSocio(socioAnterior);
            socioRepository.save(socioAnterior);
        }

        return "redirect:/cuotas";
    }

    @PostMapping("/cuotas/eliminar/{id}")
    public String eliminarCuota(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            String cuota = eliminacionService.eliminarCuotaSinPagos(
                    id, authentication != null ? authentication.getName() : null);
            redirectAttributes.addFlashAttribute("mensaje", "Se eliminó: " + cuota + ".");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cuotas";
    }
}
