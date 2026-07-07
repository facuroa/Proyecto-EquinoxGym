package com.equinox.EquinoxGym;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
public class SocioController {

    private final SocioRepository socioRepository;
    private final PlanRepository planRepository;
    private final CuotaRepository cuotaRepository;
    private final PagoRepository pagoRepository;
    private final SocioService socioService;

    public SocioController(SocioRepository socioRepository,
                           PlanRepository planRepository,
                           CuotaRepository cuotaRepository,
                           PagoRepository pagoRepository,
                           SocioService socioService) {
        this.socioRepository = socioRepository;
        this.planRepository = planRepository;
        this.cuotaRepository = cuotaRepository;
        this.pagoRepository = pagoRepository;
        this.socioService = socioService;
    }

    @GetMapping("/socios")
    public String socios(@RequestParam(name = "buscar", required = false) String buscar,
                         Model model) {
        List<Socio> socios;
        if (buscar != null && !buscar.trim().isEmpty()) {
            socios = socioRepository.findByNombreContainingIgnoreCaseOrApellidoContainingIgnoreCaseOrDniContainingIgnoreCase(
                    buscar, buscar, buscar);
        } else {
            socios = socioService.listarTodosActualizados();
        }
        model.addAttribute("socios", socios);
        model.addAttribute("buscar", buscar);
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

    @PostMapping("/socios/guardar")
    public String guardarSocio(@ModelAttribute("socio") Socio socio,
                               @RequestParam(value = "planId", required = false) Long planId,
                               @RequestParam(value = "fechaInicioPlan", required = false) String fechaInicioPlanStr,
                               BindingResult result,
                               Model model) {

        boolean esNuevo = (socio.getId() == null);

        if (socio.getDni() == null || socio.getDni().trim().isEmpty()) {
            result.rejectValue("dni", "error.socio", "El DNI es obligatorio");
        }
        if (dniDuplicado(socio)) {
            result.rejectValue("dni", "error.socio", "Ya existe un socio con ese DNI");
        }
        if (result.hasErrors()) {
            model.addAttribute("planes", planRepository.findByActivoTrueOrderByDuracionMesesAsc());
            return "nuevo-socio";
        }

        if (planId != null) {
            Plan plan = planRepository.findById(planId).orElse(null);
            socio.setPlan(plan);
            if (plan != null) {
                LocalDate inicio = (fechaInicioPlanStr != null && !fechaInicioPlanStr.isEmpty())
                        ? LocalDate.parse(fechaInicioPlanStr)
                        : LocalDate.now();
                socio.setFechaInicioPlan(inicio);
                socio.setFechaVencimientoPlan(inicio.plusMonths(plan.getDuracionMeses()));
            }
        } else {
            socio.setPlan(null);
            socio.setFechaInicioPlan(null);
            socio.setFechaVencimientoPlan(null);
        }

        if (socio.getEstado() == null) {
            socio.setEstado(EstadoSocio.ACTIVO);
        }

        Socio socioGuardado = socioRepository.save(socio);

        if (esNuevo && socioGuardado.getPlan() != null) {
            Cuota primeraCuota = new Cuota();
            primeraCuota.setSocio(socioGuardado);
            primeraCuota.setMonto(socioGuardado.getPlan().getPrecio());
            primeraCuota.setFechaVencimiento(socioGuardado.getFechaVencimientoPlan());
            primeraCuota.setEstado(EstadoCuota.PENDIENTE);
            cuotaRepository.save(primeraCuota);
        }

        return "redirect:/socios";
    }

    @GetMapping("/socios/eliminar/{id}")
    public String eliminarSocio(@PathVariable Long id) {
        Socio socio = socioRepository.findById(id).orElse(null);

        if (socio != null) {
            // Borramos primero los pagos de cada cuota del socio: no se
            // puede borrar una cuota (ni un socio) si tiene pagos asociados
            // por la restricción de clave foránea en la tabla pagos.
            for (Cuota cuota : socio.getCuotas()) {
                pagoRepository.deleteByCuota_Id(cuota.getId());
            }
            socioRepository.delete(socio); // las cuotas se borran solas (cascade)
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
