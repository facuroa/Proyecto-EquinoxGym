package com.equinox.EquinoxGym;

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

    public CuotaController(CuotaRepository cuotaRepository,
                           SocioRepository socioRepository,
                           PagoRepository pagoRepository,
                           CuotaService cuotaService,
                           SocioService socioService) {
        this.cuotaRepository = cuotaRepository;
        this.socioRepository = socioRepository;
        this.pagoRepository = pagoRepository;
        this.cuotaService = cuotaService;
        this.socioService = socioService;
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
    public String editarCuota(@PathVariable Long id, Model model) {
        Cuota cuota = cuotaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada"));

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
                                  Model model) {

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

        Cuota cuota = cuotaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada"));

        Socio socio = socioRepository.findById(cuotaForm.getSocioId())
                .orElseThrow(() -> new RuntimeException("Socio no encontrado"));

        cuota.setFechaVencimiento(cuotaForm.getFechaVencimiento());
        cuota.setMonto(cuotaForm.getMonto());
        cuota.setSocio(socio);

        cuotaService.actualizarEstadoCuota(cuota);
        cuotaRepository.save(cuota);

        socioService.actualizarEstadoSocio(socio);
        socioRepository.save(socio);

        return "redirect:/cuotas";
    }

    @PostMapping("/cuotas/eliminar/{id}")
    public String eliminarCuota(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Cuota cuota = cuotaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada"));

        Socio socio = cuota.getSocio();

        if (pagoRepository.existsByCuota_Id(id)
                || pagoRepository.existsByCuotaRenovacionGenerada_Id(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "No se puede eliminar una cuota vinculada al historial de pagos.");
            return "redirect:/cuotas";
        }
        cuotaRepository.delete(cuota);

        if (socio != null) {
            socioService.actualizarEstadoSocio(socio);
            socioRepository.save(socio);
        }

        return "redirect:/cuotas";
    }
}
