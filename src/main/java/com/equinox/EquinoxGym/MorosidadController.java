package com.equinox.EquinoxGym;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Locale;

@Controller
public class MorosidadController {

    private static final int TAMANO_PAGINA = 15;
    private final MorosidadService morosidadService;

    public MorosidadController(MorosidadService morosidadService) {
        this.morosidadService = morosidadService;
    }

    @GetMapping("/morosidad")
    public String listar(@RequestParam(name = "filtro", defaultValue = "VENCIDAS") String filtro,
                         @RequestParam(name = "buscar", defaultValue = "") String buscar,
                         @RequestParam(name = "page", defaultValue = "0") int page,
                         Model model) {
        String filtroNormalizado = morosidadService.normalizarFiltro(filtro);
        String busqueda = buscar.trim();
        MorosidadResumenDTO resumen = morosidadService.obtenerResumen(filtroNormalizado, busqueda);
        List<GestionMorosidadDTO> todas = resumen.getGestiones();

        int totalPaginas = todas.isEmpty() ? 0 : (int) Math.ceil((double) todas.size() / TAMANO_PAGINA);
        int paginaActual = totalPaginas == 0 ? 0 : Math.min(Math.max(page, 0), totalPaginas - 1);
        int desde = paginaActual * TAMANO_PAGINA;
        int hasta = Math.min(desde + TAMANO_PAGINA, todas.size());

        model.addAttribute("gestiones", todas.subList(desde, hasta));
        model.addAttribute("resumen", resumen);
        model.addAttribute("filtroSeleccionado", filtroNormalizado);
        model.addAttribute("buscar", busqueda);
        model.addAttribute("paginaActual", paginaActual);
        model.addAttribute("totalPaginas", totalPaginas);
        model.addAttribute("totalElementos", todas.size());
        model.addAttribute("primerElemento", desde);
        model.addAttribute("canales", CanalSeguimiento.values());
        return "morosidad";
    }

    @PostMapping("/morosidad/{socioId}/seguimientos")
    public String registrarSeguimiento(@PathVariable Long socioId,
                                       @RequestParam String canal,
                                       @RequestParam String nota,
                                       @RequestParam(defaultValue = "VENCIDAS") String filtro,
                                       @RequestParam(defaultValue = "") String buscar,
                                       @RequestParam(defaultValue = "0") int page,
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes) {
        try {
            CanalSeguimiento canalSeleccionado = CanalSeguimiento.valueOf(canal.toUpperCase(Locale.ROOT));
            morosidadService.registrarSeguimiento(
                    socioId, canalSeleccionado, nota, authentication != null ? authentication.getName() : null);
            redirectAttributes.addFlashAttribute("mensaje", "seguimientoGuardado");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorSeguimiento", e.getMessage());
        }

        redirectAttributes.addAttribute("filtro", morosidadService.normalizarFiltro(filtro));
        redirectAttributes.addAttribute("buscar", buscar.trim());
        redirectAttributes.addAttribute("page", Math.max(page, 0));
        return "redirect:/morosidad";
    }
}
