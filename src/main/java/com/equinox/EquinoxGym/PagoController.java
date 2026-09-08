package com.equinox.EquinoxGym;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.io.IOException;

@Controller
public class PagoController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PagoRepository pagoRepository;
    private final CuotaRepository cuotaRepository;
    private final CuotaService cuotaService;
    private final CobroService cobroService;
    private final LogoService logoService;
    private final String gymName;

    public PagoController(PagoRepository pagoRepository,
                          CuotaRepository cuotaRepository,
                          CuotaService cuotaService,
                          CobroService cobroService,
                          LogoService logoService,
                          @Value("${equinox.branding.gym-name:Keep Fit Gym}") String gymName) {
        this.pagoRepository = pagoRepository;
        this.cuotaRepository = cuotaRepository;
        this.cuotaService = cuotaService;
        this.cobroService = cobroService;
        this.logoService = logoService;
        this.gymName = gymName;
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

        Socio socio = pago.getCuota() != null ? pago.getCuota().getSocio() : null;

        model.addAttribute("pago", pago);
        model.addAttribute("socio", socio);
        model.addAttribute("origenSocio", origenSocio);
        model.addAttribute("whatsappUrl", construirWhatsappComprobante(pago, socio));
        return "comprobante-pago";
    }

    /**
     * Descarga el comprobante en PDF. El archivo se arma en el momento a partir
     * del pago, igual que el que se adjunta al email: no se guarda nada en disco,
     * asi que no depende de que las notificaciones por email esten encendidas.
     *
     * Sirve para que el mostrador baje el PDF y lo adjunte a mano en WhatsApp Web.
     */
    @GetMapping("/pagos/{id}/descargar-pdf")
    public ResponseEntity<byte[]> descargarComprobantePdf(@PathVariable Long id) {
        Pago pago = pagoRepository.findById(id).orElse(null);
        if (pago == null) {
            return ResponseEntity.notFound().build();
        }

        Socio socio = pago.getCuota() != null ? pago.getCuota().getSocio() : null;
        try {
            byte[] pdf = ComprobantePdfGenerator.generar(pago, socio, gymName, logoService.obtenerLogoBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    "Comprobante-" + pago.getNumeroComprobante() + ".pdf");

            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (IOException | RuntimeException e) {
            System.err.println(">>> No se pudo generar el PDF del comprobante: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
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

    private String construirWhatsappComprobante(Pago pago, Socio socio) {
        if (socio == null || pago.isAnulado()) {
            return null;
        }
        String mensaje = "Hola " + socio.getNombre() + ", te compartimos tu comprobante de " + gymName
                + ": pago de $ " + pago.getMonto() + " registrado el "
                + FORMATO_FECHA.format(pago.getFechaPago()) + " (" + pago.getNumeroComprobante() + "). ¡Gracias!";
        return WhatsAppLinkBuilder.construirUrl(socio.getTelefono(), mensaje);
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
