package com.equinox.EquinoxGym;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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

        List<Pago> pagos = pagoRepository.findAll();
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
        if (pago.getMonto() == null) {
            agregarErrorPago(model, pago, "El monto es obligatorio.");
            return "nuevo-pago";
        }
        if (pago.getMedioPago() == null || pago.getMedioPago().trim().isEmpty()) {
            agregarErrorPago(model, pago, "El medio de pago es obligatorio.");
            return "nuevo-pago";
        }

        // Toda la lógica de "marcar cuota como pagada + generar la siguiente"
        // vive ahora en CobroService, así la pantalla de Pagos y la pantalla
        // de Cobro rápido usan exactamente la misma regla de negocio.
        cobroService.registrarPagoPorId(cuotaId, pago.getMonto(), pago.getMedioPago());

        return "redirect:/pagos";
    }

    private void agregarErrorPago(Model model, Pago pago, String mensaje) {
        model.addAttribute("pago", pago);
        model.addAttribute("cuotasPendientes",
                cuotaRepository.findByFechaPagoIsNullOrderByFechaVencimientoAsc());
        model.addAttribute("error", mensaje);
    }
}
