package com.equinox.EquinoxGym;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class PagoController {

    private final PagoRepository pagoRepository;
    private final CuotaRepository cuotaRepository;
    private final SocioRepository socioRepository;
    private final CuotaService cuotaService;
    private final SocioService socioService;

    public PagoController(PagoRepository pagoRepository,
                          CuotaRepository cuotaRepository,
                          SocioRepository socioRepository,
                          CuotaService cuotaService,
                          SocioService socioService) {
        this.pagoRepository = pagoRepository;
        this.cuotaRepository = cuotaRepository;
        this.socioRepository = socioRepository;
        this.cuotaService = cuotaService;
        this.socioService = socioService;
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
                        String nombreSocio = "";
                        String dniSocio = "";
                        String medioPago = pago.getMedioPago() != null ? pago.getMedioPago().toLowerCase() : "";
                        String fechaPago = pago.getFechaPago() != null ? pago.getFechaPago().toString().toLowerCase() : "";

                        if (pago.getCuota() != null && pago.getCuota().getSocio() != null) {
                            Socio socio = pago.getCuota().getSocio();
                            nombreSocio = socio.getNombreCompleto() != null ? socio.getNombreCompleto().toLowerCase() : "";
                            dniSocio = socio.getDni() != null ? socio.getDni().toLowerCase() : "";
                        }

                        return nombreSocio.contains(texto)
                                || dniSocio.contains(texto)
                                || medioPago.contains(texto)
                                || fechaPago.contains(texto);
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
            model.addAttribute("pago", pago);
            model.addAttribute("cuotasPendientes", cuotaRepository.findByFechaPagoIsNullOrderByFechaVencimientoAsc());
            model.addAttribute("error", "Debe seleccionar una cuota.");
            return "nuevo-pago";
        }

        if (pago.getMonto() == null) {
            model.addAttribute("pago", pago);
            model.addAttribute("cuotasPendientes", cuotaRepository.findByFechaPagoIsNullOrderByFechaVencimientoAsc());
            model.addAttribute("error", "El monto es obligatorio.");
            return "nuevo-pago";
        }

        if (pago.getMedioPago() == null || pago.getMedioPago().trim().isEmpty()) {
            model.addAttribute("pago", pago);
            model.addAttribute("cuotasPendientes", cuotaRepository.findByFechaPagoIsNullOrderByFechaVencimientoAsc());
            model.addAttribute("error", "El medio de pago es obligatorio.");
            return "nuevo-pago";
        }

        Cuota cuota = cuotaRepository.findById(cuotaId)
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada"));

        pago.setCuota(cuota);
        pago.setFechaPago(LocalDate.now());

        pagoRepository.save(pago);

        cuota.setFechaPago(LocalDate.now());
        cuotaService.actualizarEstadoCuota(cuota);
        cuotaRepository.save(cuota);

        if (cuota.getSocio() != null) {
            socioService.actualizarEstadoSocio(cuota.getSocio());
            socioRepository.save(cuota.getSocio());
        }

        return "redirect:/pagos";
    }
}