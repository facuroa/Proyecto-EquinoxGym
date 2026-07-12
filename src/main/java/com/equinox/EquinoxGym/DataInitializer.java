package com.equinox.EquinoxGym;

import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public DataInitializer(UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${equinox.bootstrap-admin.username:admin}") String adminUsername,
                           @Value("${equinox.bootstrap-admin.password:}") String adminPassword) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() == 0) {
            if (adminPassword == null || adminPassword.isBlank()) {
                throw new IllegalStateException(
                        "No hay usuarios. Configure EQUINOX_ADMIN_PASSWORD para crear el administrador inicial.");
            }

            Usuario admin = new Usuario();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRol(RolUsuario.ADMIN);
            admin.setActivo(true);
            usuarioRepository.save(admin);
            System.out.println(">>> Usuario administrador inicial creado correctamente");
        }
    }
}
