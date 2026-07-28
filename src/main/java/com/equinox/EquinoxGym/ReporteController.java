package com.equinox.EquinoxGym;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @GetMapping("/reportes")
    public String mostrarReportes(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Model model) {
        LocalDate hoy = LocalDate.now();
        LocalDate periodoDesde = desde == null ? hoy.withDayOfMonth(1) : desde;
        LocalDate periodoHasta = hasta == null ? hoy : hasta;

        if (periodoDesde.isAfter(periodoHasta)) {
            LocalDate auxiliar = periodoDesde;
            periodoDesde = periodoHasta;
            periodoHasta = auxiliar;
            model.addAttribute("avisoPeriodo", "Ordenamos las fechas para mostrar el período correctamente.");
        }

        model.addAttribute("reporte", reporteService.obtenerResumen(periodoDesde, periodoHasta));
        model.addAttribute("desde", periodoDesde);
        model.addAttribute("hasta", periodoHasta);
        model.addAttribute("inicioMes", hoy.withDayOfMonth(1));
        model.addAttribute("hoy", hoy);
        return "reportes";
    }
}
