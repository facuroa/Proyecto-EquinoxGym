package com.equinox.EquinoxGym;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Eliminaciones definitivas. Solo las puede usar un administrador: lo
 * restringe SecurityConfig sobre toda la ruta /eliminar-definitivo.
 */
@Controller
@RequestMapping("/eliminar-definitivo")
public class EliminacionController {

    private final EliminacionService eliminacionService;
    private final IndiceDniUnico indiceDniUnico;

    public EliminacionController(EliminacionService eliminacionService, IndiceDniUnico indiceDniUnico) {
        this.eliminacionService = eliminacionService;
        this.indiceDniUnico = indiceDniUnico;
    }

    @PostMapping("/socio/{id}")
    public String socio(@PathVariable Long id,
                        @RequestParam(required = false) String confirmacion,
                        @RequestParam(required = false) String motivo,
                        Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        try {
            String nombre = eliminacionService.eliminarSocio(id, confirmacion, motivo, usuario(authentication));
            // Si era el ultimo DNI repetido, ya se puede activar el control en la base.
            indiceDniUnico.asegurar();
            redirectAttributes.addFlashAttribute("mensaje",
                    "Se eliminó definitivamente a " + nombre + ". Quedó registrado en Auditoría.");
            return "redirect:/socios";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/socios/ver/" + id;
        }
    }

    @PostMapping("/cuota/{id}")
    public String cuota(@PathVariable Long id,
                        @RequestParam(required = false) String confirmacion,
                        @RequestParam(required = false) String motivo,
                        Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        try {
            String cuota = eliminacionService.eliminarCuota(id, confirmacion, motivo, usuario(authentication));
            redirectAttributes.addFlashAttribute("mensaje", "Se eliminó: " + cuota + ". Quedó registrado en Auditoría.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cuotas";
    }

    @PostMapping("/pago/{id}")
    public String pago(@PathVariable Long id,
                       @RequestParam(required = false) String confirmacion,
                       @RequestParam(required = false) String motivo,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {
        try {
            String numero = eliminacionService.eliminarPago(id, confirmacion, motivo, usuario(authentication));
            redirectAttributes.addFlashAttribute("mensaje",
                    "Se eliminó el pago " + numero + ". Quedó registrado en Auditoría.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pagos";
    }

    private static String usuario(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
