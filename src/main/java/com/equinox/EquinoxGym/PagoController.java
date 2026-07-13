package com.equinox.EquinoxGym;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class PagoController {

    private final PagoRepository pagoRepository;
    private final CuotaRepository cuotaRepository;
    private final CuotaService cuotaService;
    private final CobroService cobroService;

    public PagoController(PagoRepository pagoRepository,
                          CuotaRepository cuotaRepository,
                          CuotaService cuotaService,
                          CobroService cobroService) {
        this.pagoRepository = pagoRepository;
        this.cuotaRepository = cuotaRepository;
        this.cuotaService = cuotaService;
        this.cobroService = cobroService;
    }

    @GetMapping("/pagos")
    public String listarPagos(@RequestParam(name = "buscar", required = false) String buscar,
                              Model model) {
        List<Cuota> cuotasImpagas = cuotaRepository.findByFechaPagoIsNullOrderByFechaVencimientoAsc();
        for (Cuota cuota : cuotasImpagas) {
            cuotaService.actualizarEstadoCuota(cuota);
        }
        cuotaRepository.saveAll(cuotasImpagas);

        List<Pago> pagos = pagoRepository.findAllByOrderByFechaPagoDescIdDesc();
        if (buscar != null && !buscar.trim().isEmpty()) {
            String texto = buscar.trim().toLowerCase();
            pagos = pagos.stream()
                    .filter(pago -> {
                        String nombre = "", dni = "";
                        String medio = pago.getMedioPago() != null ? pago.getMedioPago().toLowerCase() : "";
                        String fecha = pago.getFechaPago() != null ? pago.getFechaPago().toString() : "";
                        if (pago.getCuota() != null && pago.getCuota().getSocio() != null) {
                            Socio s = pago.getCuota().getSocio();
                            nombre = s.getNombreCompleto() != null ? s.getNombreCompleto().toLowerCase() : "";
                            dni = s.getDni() != null ? s.getDni().toLowerCase() : "";
                        }
                        return nombre.contains(texto) || dni.contains(texto)
                                || medio.contains(texto) || fecha.contains(texto);
                    })
                    .collect(Collectors.toList());
        }

        model.addAttribute("pagos", pagos);
        model.addAttribute("buscar", buscar);
        return "pagos";
    }

    @GetMapping("/pagos/nuevo")
    public String mostrarFormularioPago(Model model) {
        List<Cuota> cuotasImpagas = cuotaRepository.findByFechaPagoIsNullOrderByFechaVencimientoAsc();
        for (Cuota cuota : cuotasImpagas) {
            cuotaService.actualizarEstadoCuota(cuota);
        }
        cuotaRepository.saveAll(cuotasImpagas);

        model.addAttribute("pago", new Pago());
        model.addAttribute("cuotasPendientes", cuotasImpagas);
        return "nuevo-pago";
    }

    @PostMapping("/pagos/guardar")
    public String guardarPago(@ModelAttribute("pago") Pago pago,
                              @RequestParam(value = "cuotaId", required = false) Long cuotaId,
                              Model model) {

        if (cuotaId == null) {
            agregarErrorPago(model, pago, "Debe seleccionar una cuota.");
            return "nuevo-pago";
        }

        try {
            cobroService.registrarPagoPorId(cuotaId, pago.getMonto(), pago.getMedioPago());
        } catch (IllegalStateException e) {
            agregarErrorPago(model, pago, "Esta cuota ya fue pagada. No se puede registrar dos veces.");
            return "nuevo-pago";
        } catch (IllegalArgumentException e) {
            agregarErrorPago(model, pago, e.getMessage());
            return "nuevo-pago";
        }

        return "redirect:/pagos";
    }

    @PostMapping("/pagos/{id}/anular")
    public String anularPago(@PathVariable Long id,
                             @RequestParam String motivo,
                             RedirectAttributes redirectAttributes) {
        try {
            cobroService.anularPago(id, motivo);
            redirectAttributes.addFlashAttribute("mensaje", "El pago fue anulado y quedó registrado en el historial.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pagos";
    }

    private void agregarErrorPago(Model model, Pago pago, String mensaje) {
        List<Cuota> cuotasImpagas = cuotaRepository.findByFechaPagoIsNullOrderByFechaVencimientoAsc();
        for (Cuota cuota : cuotasImpagas) {
            cuotaService.actualizarEstadoCuota(cuota);
        }
        cuotaRepository.saveAll(cuotasImpagas);

        model.addAttribute("pago", pago);
        model.addAttribute("cuotasPendientes", cuotasImpagas);
        model.addAttribute("error", mensaje);
    }
}
