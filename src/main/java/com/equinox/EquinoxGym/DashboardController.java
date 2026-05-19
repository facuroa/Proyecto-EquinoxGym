package com.equinox.EquinoxGym;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public String mostrarDashboard(Model model) {
        DashboardDTO resumen = dashboardService.obtenerResumen();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        model.addAttribute("resumen", resumen);
        model.addAttribute("usuarioLogueado", username);

        return "dashboard";
    }
}