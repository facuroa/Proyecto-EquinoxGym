package com.equinox.EquinoxGym;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class CuotaController {

    private final CuotaRepository cuotaRepository;
    private final SocioRepository socioRepository;
    private final CuotaService cuotaService;
    private final SocioService socioService;

    public CuotaController(CuotaRepository cuotaRepository,
                           SocioRepository socioRepository,
                           CuotaService cuotaService,
                           SocioService socioService) {
        this.cuotaRepository = cuotaRepository;
        this.socioRepository = socioRepository;
        this.cuotaService = cuotaService;
        this.socioService = socioService;
    }

    @GetMapping("/cuotas")
    public String listarCuotas(@RequestParam(name = "estado", required = false) String estado,
                               Model model) {

        List<Cuota> cuotas = cuotaRepository.findAll();

        for (Cuota cuota : cuotas) {
            cuotaService.actualizarEstadoCuota(cuota);
        }
        cuotaRepository.saveAll(cuotas);

        List<Cuota> cuotasFiltradas = cuotas;

        if (estado != null && !estado.isBlank() && !estado.equalsIgnoreCase("TODAS")) {
            try {
                EstadoCuota estadoEnum = EstadoCuota.valueOf(estado.toUpperCase());
                cuotasFiltradas = cuotas.stream()
                        .filter(c -> c.getEstado() == estadoEnum)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                cuotasFiltradas = cuotas;
            }
        }

        model.addAttribute("cuotas", cuotasFiltradas);
        model.addAttribute("estadoSeleccionado", estado == null ? "TODAS" : estado.toUpperCase());

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
        }

        if (cuota.getMonto() == null) {
            result.rejectValue("monto", "error.cuota", "El monto es obligatorio");
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
        }

        if (cuotaForm.getMonto() == null) {
            result.rejectValue("monto", "error.cuota", "El monto es obligatorio");
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

    @GetMapping("/cuotas/eliminar/{id}")
    public String eliminarCuota(@PathVariable Long id) {
        Cuota cuota = cuotaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada"));

        Socio socio = cuota.getSocio();
        cuotaRepository.delete(cuota);

        if (socio != null) {
            socioService.actualizarEstadoSocio(socio);
            socioRepository.save(socio);
        }

        return "redirect:/cuotas";
    }
}