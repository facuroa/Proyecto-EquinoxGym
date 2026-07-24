package com.equinox.EquinoxGym;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/caja")
public class CajaController {

    private final CajaService cajaService;

    public CajaController(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    @GetMapping
    public String mostrarCaja(Authentication authentication, Model model) {
        String usuario = authentication.getName();
        boolean administrador = authentication.getAuthorities().stream()
                .anyMatch(autoridad -> "ROLE_ADMIN".equals(autoridad.getAuthority()));
        model.addAttribute("resumen", cajaService.obtenerResumen(usuario, administrador));
        model.addAttribute("tiposMovimiento", TipoMovimientoCaja.values());
        return "caja";
    }

    @PostMapping("/abrir")
    public String abrirCaja(@RequestParam BigDecimal montoInicial,
                            @RequestParam(required = false) String observaciones,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            cajaService.abrirCaja(authentication.getName(), montoInicial, observaciones);
            redirectAttributes.addFlashAttribute("mensaje", "Caja abierta correctamente.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/caja";
    }

    @PostMapping("/movimientos")
    public String registrarMovimiento(@RequestParam TipoMovimientoCaja tipo,
                                      @RequestParam BigDecimal monto,
                                      @RequestParam String concepto,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        try {
            cajaService.registrarMovimientoManual(authentication.getName(), tipo, monto, concepto);
            redirectAttributes.addFlashAttribute("mensaje", "Movimiento registrado.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/caja";
    }

    @PostMapping("/cerrar")
    public String cerrarCaja(@RequestParam Long cajaId,
                             @RequestParam BigDecimal montoDeclarado,
                             @RequestParam(required = false) String observaciones,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            CajaSesion caja = cajaService.cerrarCaja(
                    authentication.getName(), cajaId, montoDeclarado, observaciones);
            String detalle = caja.getDiferenciaCierre().signum() == 0
                    ? "Caja cerrada sin diferencias."
                    : "Caja cerrada con una diferencia de $ " + caja.getDiferenciaCierre();
            redirectAttributes.addFlashAttribute("mensaje", detalle);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/caja";
    }
}
