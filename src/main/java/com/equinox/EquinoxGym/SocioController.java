package com.equinox.EquinoxGym;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SocioController {

    private final SocioRepository socioRepository;

    public SocioController(SocioRepository socioRepository) {
        this.socioRepository = socioRepository;
    }

    @GetMapping("/socios")
    public String socios(@RequestParam(name = "buscar", required = false) String buscar,
                         Model model) {

        if (buscar != null && !buscar.trim().isEmpty()) {
            model.addAttribute("socios",
                    socioRepository.findByNombreContainingIgnoreCaseOrApellidoContainingIgnoreCaseOrDniContainingIgnoreCase(
                            buscar, buscar, buscar));
        } else {
            model.addAttribute("socios", socioRepository.findAll());
        }

        model.addAttribute("buscar", buscar);
        return "socios";
    }

    @GetMapping("/socios/nuevo")
    public String nuevoSocio(Model model) {
        model.addAttribute("socio", new Socio());
        return "nuevo-socio";
    }

    @PostMapping("/socios/guardar")
    public String guardarSocio(@ModelAttribute("socio") Socio socio,
                               BindingResult result,
                               Model model) {

        if (socio.getDni() == null || socio.getDni().trim().isEmpty()) {
            result.rejectValue("dni", "error.socio", "El DNI es obligatorio");
        }

        if (dniDuplicado(socio)) {
            result.rejectValue("dni", "error.socio", "Ya existe un socio con ese DNI");
        }

        if (result.hasErrors()) {
            model.addAttribute("socio", socio);
            return "nuevo-socio";
        }

        if (socio.getEstado() == null) {
            socio.setEstado(EstadoSocio.ACTIVO);
        }

        socioRepository.save(socio);
        return "redirect:/socios";
    }

    @GetMapping("/socios/eliminar/{id}")
    public String eliminarSocio(@PathVariable Long id) {
        socioRepository.deleteById(id);
        return "redirect:/socios";
    }

    @GetMapping("/socios/editar/{id}")
    public String editarSocio(@PathVariable Long id, Model model) {
        Optional<Socio> socioOpt = socioRepository.findById(id);

        if (socioOpt.isPresent()) {
            model.addAttribute("socio", socioOpt.get());
            return "nuevo-socio";
        }

        return "redirect:/socios";
    }

    private boolean dniDuplicado(Socio socio) {
        if (socio.getDni() == null || socio.getDni().trim().isEmpty()) {
            return false;
        }

        Optional<Socio> existente = socioRepository.findByDni(socio.getDni());

        if (existente.isEmpty()) {
            return false;
        }

        if (socio.getId() == null) {
            return true;
        }

        return !existente.get().getId().equals(socio.getId());
    }
}