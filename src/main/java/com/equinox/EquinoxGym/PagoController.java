package com.equinox.EquinoxGym;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;

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
    public String listarPagos(@RequestParam(name = "buscar", defaultValue = "") String buscar,
                              @RequestParam(name = "medioPago", defaultValue = "") String medioPago,
                              @RequestParam(name = "desde", required = false) LocalDate desde,
                              @RequestParam(name = "hasta", required = false) LocalDate hasta,
                              @RequestParam(name = "estado", defaultValue = "TODOS") String estado,
                              @RequestParam(name = "page", defaultValue = "0") int page,
                              Model model) {
        Boolean anulado = switch (estado.toUpperCase()) {
            case "CONFIRMADOS" -> false;
            case "ANULADOS" -> true;
            default -> null;
        };
        if (anulado == null) {
            estado = "TODOS";
        }

        PageRequest paginacion = PageRequest.of(Math.max(page, 0), 15,
                Sort.by("fechaPago").descending().and(Sort.by("id").descending()));
        Page<Pago> pagina = pagoRepository.buscarPaginado(
                buscar.trim(), medioPago.trim(), desde, hasta, anulado, paginacion);

        model.addAttribute("pagos", pagina.getContent());
        model.addAttribute("buscar", buscar.trim());
        model.addAttribute("medioPago", medioPago.trim());
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("estadoSeleccionado", estado.toUpperCase());
        model.addAttribute("paginaActual", pagina.getNumber());
        model.addAttribute("totalPaginas", pagina.getTotalPages());
        model.addAttribute("totalElementos", pagina.getTotalElements());
        model.addAttribute("primerElemento", pagina.getNumber() * pagina.getSize());
        return "pagos";
    }

    @GetMapping("/pagos/nuevo")
    public String mostrarFormularioPago(Model model) {
        List<Cuota> cuotasImpagas = cuotaRepository.findByFechaPagoIsNullOrderByFechaVencimientoAsc();
        guardarCuotasConEstadoModificado(cuotasImpagas);

        model.addAttribute("pago", new Pago());
        model.addAttribute("cuotasPendientes", cuotasImpagas);
        return "nuevo-pago";
    }

    @PostMapping("/pagos/guardar")
    public String guardarPago(@ModelAttribute("pago") Pago pago,
                              @RequestParam(value = "cuotaId", required = false) Long cuotaId,
                              Model model) {

        if (cuotaId == null) {
            agregarErrorPago(model, pago, cuotaId, "Debe seleccionar una cuota.");
            return "nuevo-pago";
        }

        try {
            Pago pagoRegistrado = cobroService.registrarPagoPorId(cuotaId, pago.getMonto(), pago.getMedioPago());
            return "redirect:/pagos/" + pagoRegistrado.getId() + "/comprobante";
        } catch (CajaCerradaException e) {
            agregarErrorPago(model, pago, cuotaId, e.getMessage());
            return "nuevo-pago";
        } catch (IllegalStateException e) {
            agregarErrorPago(model, pago, cuotaId, e.getMessage());
            return "nuevo-pago";
        } catch (IllegalArgumentException e) {
            agregarErrorPago(model, pago, cuotaId, e.getMessage());
            return "nuevo-pago";
        }
    }

    @GetMapping("/pagos/{id}/comprobante")
    public String verComprobante(@PathVariable Long id,
                                 @RequestParam(name = "origenSocio", required = false) Long origenSocio,
                                 Model model) {
        Pago pago = pagoRepository.findById(id).orElse(null);
        if (pago == null) {
            return "redirect:/pagos";
        }

        model.addAttribute("pago", pago);
        model.addAttribute("socio", pago.getCuota() != null ? pago.getCuota().getSocio() : null);
        model.addAttribute("origenSocio", origenSocio);
        return "comprobante-pago";
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

    private void agregarErrorPago(Model model, Pago pago, Long cuotaId, String mensaje) {
        List<Cuota> cuotasImpagas = cuotaRepository.findByFechaPagoIsNullOrderByFechaVencimientoAsc();
        guardarCuotasConEstadoModificado(cuotasImpagas);

        model.addAttribute("pago", pago);
        model.addAttribute("cuotasPendientes", cuotasImpagas);
        model.addAttribute("cuotaIdSeleccionada", cuotaId);
        model.addAttribute("error", mensaje);
    }

    private void guardarCuotasConEstadoModificado(List<Cuota> cuotas) {
        List<Cuota> modificadas = cuotas.stream()
                .filter(cuotaService::actualizarEstadoCuota)
                .toList();
        if (!modificadas.isEmpty()) {
            cuotaRepository.saveAll(modificadas);
        }
    }
}
