package com.equinox.EquinoxGym;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/planes")
public class PlanController {

    private final PlanRepository planRepository;

    public PlanController(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("planes", planRepository.findAll());
        return "planes";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("plan", new Plan());
        return "nuevo-plan";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable Long id, Model model) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan no encontrado"));
        model.addAttribute("plan", plan);
        return "nuevo-plan";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Plan plan, Model model) {
        if (plan.getNombre() == null || plan.getNombre().trim().isEmpty()) {
            model.addAttribute("plan", plan);
            model.addAttribute("error", "El nombre es obligatorio.");
            return "nuevo-plan";
        }
        if (plan.getPrecio() == null || plan.getPrecio().doubleValue() <= 0) {
            model.addAttribute("plan", plan);
            model.addAttribute("error", "El precio debe ser mayor a 0.");
            return "nuevo-plan";
        }
        if (plan.getDuracionMeses() <= 0) {
            model.addAttribute("plan", plan);
            model.addAttribute("error", "La duración debe ser al menos 1 mes.");
            return "nuevo-plan";
        }

        boolean nombreDuplicado = plan.getId() == null
                ? planRepository.existsByNombreIgnoreCase(plan.getNombre())
                : planRepository.existsByNombreIgnoreCaseAndIdNot(plan.getNombre(), plan.getId());

        if (nombreDuplicado) {
            model.addAttribute("plan", plan);
            model.addAttribute("error", "Ya existe un plan con ese nombre.");
            return "nuevo-plan";
        }

        planRepository.save(plan);
        return "redirect:/planes";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id) {
        planRepository.deleteById(id);
        return "redirect:/planes";
    }
}
