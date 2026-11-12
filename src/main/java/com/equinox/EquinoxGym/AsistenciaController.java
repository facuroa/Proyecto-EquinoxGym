package com.equinox.EquinoxGym;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/asistencia")
public class AsistenciaController {

    private static final int TAMANO_PAGINA = 15;

    private final AsistenciaService asistenciaService;
    private final CobroService cobroService;
    private final SocioRepository socioRepository;

    public AsistenciaController(AsistenciaService asistenciaService,
                                CobroService cobroService,
                                SocioRepository socioRepository) {
        this.asistenciaService = asistenciaService;
        this.cobroService = cobroService;
        this.socioRepository = socioRepository;
    }

    /** Solapa "Hoy": buscador para registrar ingresos y la lista del dia. */
    @GetMapping
    public String hoy(Model model) {
        model.addAttribute("hoy", asistenciaService.resumenDeHoy());
        return "asistencia";
    }

    /** Autocompletado del buscador: el mismo criterio que usa la pantalla de Cobro. */
    @GetMapping("/buscar")
    @ResponseBody
    public List<SocioBusquedaDTO> buscar(@RequestParam(defaultValue = "") String q) {
        return cobroService.buscarSocios(q);
    }

    @PostMapping("/registrar")
    public String registrar(@RequestParam(required = false) Long socioId,
                            @RequestParam(defaultValue = "false") boolean forzar,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            IngresoRegistradoDTO ingreso = asistenciaService.registrarIngreso(
                    socioId, forzar, authentication != null ? authentication.getName() : null);
            redirectAttributes.addFlashAttribute("ingreso", ingreso);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            // Dos pedidos iguales al mismo tiempo (doble clic o doble Enter):
            // el primero ya quedo guardado, el segundo se descarta.
            redirectAttributes.addFlashAttribute("error",
                    "Ese ingreso ya estaba registrado. Revisá la lista de hoy.");
        }
        return "redirect:/asistencia";
    }

    @PostMapping("/{id}/quitar")
    public String quitar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            String socio = asistenciaService.quitarIngreso(id);
            redirectAttributes.addFlashAttribute("mensaje", "Se quitó el ingreso de " + socio + ".");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/asistencia";
    }

    /** Solapa "Historial": semana tipica y socios que dejaron de venir. */
    @GetMapping("/historial")
    public String historial(@RequestParam(defaultValue = "4") String periodo,
                            @RequestParam(required = false) String desde,
                            @RequestParam(required = false) String hasta,
                            @RequestParam(required = false) String dias,
                            @RequestParam(required = false) String page,
                            @RequestParam(required = false) String socio,
                            Model model) {
        PeriodoAsistencia periodoElegido = asistenciaService.resolverPeriodo(periodo, desde, hasta);
        model.addAttribute("periodo", periodoElegido);
        model.addAttribute("semana", asistenciaService.semanaTipica(periodoElegido));
        model.addAttribute("minutosAnticipacion", asistenciaService.getMinutosAnticipacion());

        int umbral = asistenciaService.normalizarUmbral(dias);
        List<SocioAusenteDTO> ausentes = asistenciaService.sociosAusentes(umbral);
        int totalPaginas = ausentes.isEmpty() ? 0 : (int) Math.ceil((double) ausentes.size() / TAMANO_PAGINA);
        int paginaActual = totalPaginas == 0 ? 0 : Math.min(Math.max(enteroSeguro(page), 0), totalPaginas - 1);
        int primerElemento = paginaActual * TAMANO_PAGINA;
        List<SocioAusenteDTO> pagina = ausentes.subList(
                primerElemento, Math.min(primerElemento + TAMANO_PAGINA, ausentes.size()));

        model.addAttribute("umbral", umbral);
        model.addAttribute("umbrales", AsistenciaService.UMBRALES_AUSENCIA);
        model.addAttribute("ausentes", pagina);
        model.addAttribute("totalAusentes", ausentes.size());
        model.addAttribute("paginaActual", paginaActual);
        model.addAttribute("totalPaginas", totalPaginas);
        model.addAttribute("primerElemento", primerElemento);
        model.addAttribute("hayRegistros", asistenciaService.hayIngresosRegistrados());

        // La ficha lateral muestra al socio elegido o, si no se eligio
        // ninguno, al primero de la lista para que el panel no quede vacio.
        Socio elegido = buscarSocio(socio);
        if (elegido == null && !pagina.isEmpty()) {
            elegido = socioRepository.findById(pagina.get(0).getSocioId()).orElse(null);
        }
        model.addAttribute("resumenSocio",
                elegido == null ? null : asistenciaService.resumenDeSocio(elegido));
        return "asistencia-historial";
    }

    private Socio buscarSocio(String id) {
        int socioId = enteroSeguro(id);
        return socioId <= 0 ? null : socioRepository.findById((long) socioId).orElse(null);
    }

    /** Los parametros de la URL los puede tocar cualquiera: nunca deben romper la pantalla. */
    private static int enteroSeguro(String texto) {
        if (texto == null || texto.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
