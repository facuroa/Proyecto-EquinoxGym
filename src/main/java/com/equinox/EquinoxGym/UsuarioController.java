package com.equinox.EquinoxGym;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private static final Pattern USERNAME_VALIDO = Pattern.compile("^[A-Za-z0-9._-]{3,40}$");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioRepository usuarioRepository,
                             PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioRepository.findAll());
        return "usuarios";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("roles", RolUsuario.values());
        return "nuevo-usuario";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable Long id, Model model) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        model.addAttribute("usuario", usuario);
        model.addAttribute("roles", RolUsuario.values());
        return "nuevo-usuario";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Usuario usuario,
                          @RequestParam(value = "passwordNuevo", required = false) String passwordNuevo,
                          Model model) {

        boolean esNuevo = (usuario.getId() == null);
        if (usuario.getUsername() != null) {
            usuario.setUsername(usuario.getUsername().trim());
        }

        if (usuario.getUsername() == null || usuario.getUsername().trim().isEmpty()) {
            return volverConError(model, usuario, "El nombre de usuario es obligatorio.");
        }
        if (!USERNAME_VALIDO.matcher(usuario.getUsername()).matches()) {
            return volverConError(model, usuario,
                    "El usuario debe tener entre 3 y 40 caracteres y usar solo letras, números, punto, guion o guion bajo.");
        }
        if (usuario.getRol() == null) {
            return volverConError(model, usuario, "Debe seleccionar un rol.");
        }
        if (esNuevo && (passwordNuevo == null || passwordNuevo.trim().isEmpty())) {
            return volverConError(model, usuario, "La contraseña es obligatoria para nuevos usuarios.");
        }
        if (passwordNuevo != null && !passwordNuevo.isBlank()
                && (passwordNuevo.length() < 8 || passwordNuevo.length() > 72)) {
            return volverConError(model, usuario, "La contraseña debe tener entre 8 y 72 caracteres.");
        }

        boolean usernameDuplicado = esNuevo
                ? usuarioRepository.existsByUsernameIgnoreCase(usuario.getUsername())
                : usuarioRepository.existsByUsernameIgnoreCaseAndIdNot(usuario.getUsername(), usuario.getId());

        if (usernameDuplicado) {
            return volverConError(model, usuario, "Ya existe un usuario con ese nombre.");
        }

        if (esNuevo) {
            usuario.setPassword(passwordEncoder.encode(passwordNuevo));
            usuarioRepository.save(usuario);
        } else {
            Usuario existente = usuarioRepository.findById(usuario.getId())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            if (existente.getRol() == RolUsuario.ADMIN && !usuario.isActivo()) {
                long adminsActivos = usuarioRepository.countByRolAndActivoTrue(RolUsuario.ADMIN);
                if (adminsActivos <= 1) {
                    return volverConError(model, usuario, "No se puede desactivar el único administrador activo.");
                }
            }

            existente.setUsername(usuario.getUsername());
            existente.setRol(usuario.getRol());
            existente.setActivo(usuario.isActivo());

            if (passwordNuevo != null && !passwordNuevo.trim().isEmpty()) {
                existente.setPassword(passwordEncoder.encode(passwordNuevo));
            }

            usuarioRepository.save(existente);
        }

        return "redirect:/usuarios";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, Model model) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (usuario.getRol() == RolUsuario.ADMIN) {
            long adminsActivos = usuarioRepository.countByRolAndActivoTrue(RolUsuario.ADMIN);
            if (adminsActivos <= 1) {
                model.addAttribute("usuarios", usuarioRepository.findAll());
                model.addAttribute("error", "No se puede eliminar el único administrador activo.");
                return "usuarios";
            }
        }

        usuarioRepository.deleteById(id);
        return "redirect:/usuarios";
    }

    private String volverConError(Model model, Usuario usuario, String mensaje) {
        model.addAttribute("usuario", usuario);
        model.addAttribute("roles", RolUsuario.values());
        model.addAttribute("error", mensaje);
        return "nuevo-usuario";
    }
}
