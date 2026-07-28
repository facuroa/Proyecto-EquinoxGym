package com.equinox.EquinoxGym;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Carga datos ficticios para mostrar el sistema en una reunion de venta,
 * sin depender de una base de datos real. Solo se activa con el perfil "demo"
 * (ver application-demo.properties), que usa una base H2 en memoria: cada
 * arranque empieza limpio.
 */
@Component
@Profile("demo")
@Order(2)
public class DemoDataSeeder implements CommandLineRunner {

    private final PlanRepository planRepository;
    private final SocioRepository socioRepository;
    private final CuotaRepository cuotaRepository;
    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(PlanRepository planRepository,
                           SocioRepository socioRepository,
                           CuotaRepository cuotaRepository,
                           PagoRepository pagoRepository,
                           UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder) {
        this.planRepository = planRepository;
        this.socioRepository = socioRepository;
        this.cuotaRepository = cuotaRepository;
        this.pagoRepository = pagoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (socioRepository.count() > 0) {
            return;
        }

        crearUsuarioRecepcion();

        Plan mensual = crearPlan("Musculación Mensual", new BigDecimal("18000"), 1);
        Plan trimestral = crearPlan("Full Access Trimestral", new BigDecimal("48000"), 3);
        Plan anual = crearPlan("Anual Full", new BigDecimal("170000"), 12);

        LocalDate hoy = LocalDate.now();

        // Al dia: ya pagaron este mes, el proximo vencimiento no esta cerca todavia.
        alDia("Lucía", "Fernández", "30111222", mensual, hoy, 25, 10, cumpleanos(hoy, 3, 29));
        alDia("Martín", "Gómez", "30222333", mensual, hoy, 20, 8, null);
        alDiaConLesion("Tomás", "Peralta", "30333444", trimestral, hoy, 18, 12,
                "Molestia en la rodilla derecha, evitar sentadilla profunda");
        alDia("Agustina", "Molina", "30444555", mensual, hoy, 22, 6, null);
        alDia("Mateo", "Vidal", "30555666", anual, hoy, 27, 15, null);

        // Pagaron hoy mismo, para que "Recaudado hoy" y "Pagos de hoy" no arranquen en cero.
        pagoHoy("Bruno", "Medina", "30666777", mensual, hoy);
        pagoHoy("Julieta", "Castro", "30777888", trimestral, hoy);

        // Vencen pronto: alimentan "Renovaciones cercanas".
        vencePronto("Sofía", "Ramírez", "30888999", mensual, hoy, 2);
        vencePronto("Nicolás", "Torres", "30999000", mensual, hoy, 5);

        // Morosos: cuota vencida, alimentan "Socios con cuotas vencidas".
        moroso("Camila", "Ortiz", "31111000", mensual, hoy, 3);
        moroso("Diego", "Herrera", "31222111", mensual, hoy, 15);
        moroso("Ezequiel", "Suárez", "31333222", trimestral, hoy, 1);

        // Atraso prolongado: el sistema los pasa a INACTIVO automaticamente (>=20 dias).
        moroso("Franco", "Álvarez", "31444333", mensual, hoy, 25);

        // Alta reciente, con cobro inicial hecho hoy.
        altaConPagoInicial("Valentina", "Rojas", "31555444", mensual, hoy, cumpleanos(hoy, 6, 24));

        System.out.println(">>> Datos de demostración cargados correctamente");
    }

    private void crearUsuarioRecepcion() {
        Usuario u = new Usuario();
        u.setUsername("recepcion");
        u.setPassword(passwordEncoder.encode("demo1234"));
        u.setRol(RolUsuario.RECEPCIONISTA);
        u.setActivo(true);
        usuarioRepository.save(u);
    }

    private Plan crearPlan(String nombre, BigDecimal precio, int duracionMeses) {
        Plan plan = new Plan();
        plan.setNombre(nombre);
        plan.setPrecio(precio);
        plan.setDuracionMeses(duracionMeses);
        plan.setActivo(true);
        return planRepository.save(plan);
    }

    private LocalDate cumpleanos(LocalDate hoy, int diasHastaCumple, int edad) {
        return hoy.plusDays(diasHastaCumple).minusYears(edad);
    }

    private Socio crearSocioBase(String nombre, String apellido, String dni, Plan plan,
                                  LocalDate fechaAlta, LocalDate fechaNacimiento) {
        Socio socio = new Socio();
        socio.setNombre(nombre);
        socio.setApellido(apellido);
        socio.setDni(dni);
        socio.setTelefono("11-4000-" + dni.substring(dni.length() - 4));
        socio.setEmail((nombre + "." + apellido + "@demo.gym").toLowerCase());
        socio.setFechaNacimiento(fechaNacimiento);
        socio.setPlan(plan);
        socio.setFechaAlta(fechaAlta);
        socio.setFechaInicioPlan(fechaAlta);
        socio.setEstado(EstadoSocio.ACTIVO);
        return socioRepository.save(socio);
    }

    private Cuota crearCuota(Socio socio, LocalDate vencimiento, LocalDate pago, BigDecimal monto, EstadoCuota estado) {
        Cuota cuota = new Cuota();
        cuota.setSocio(socio);
        cuota.setFechaVencimiento(vencimiento);
        cuota.setFechaPago(pago);
        cuota.setMonto(monto);
        cuota.setEstado(estado);
        return cuotaRepository.save(cuota);
    }

    private void crearPago(Cuota cuota, LocalDate fechaPago, BigDecimal monto) {
        Pago pago = new Pago();
        pago.setCuota(cuota);
        pago.setFechaPago(fechaPago);
        pago.setMonto(monto);
        pago.setMedioPago("Efectivo");
        pago.setFechaRegistro(fechaPago.atTime(10, 30));
        pago.setRegistradoPor("admin");
        pagoRepository.save(pago);
    }

    private void alDia(String nombre, String apellido, String dni, Plan plan, LocalDate hoy,
                        int diasProximoVencimiento, int diasDesdeUltimoPago, LocalDate fechaNacimiento) {
        LocalDate fechaAlta = hoy.minusMonths(plan.getDuracionMeses()).minusDays(diasDesdeUltimoPago);
        Socio socio = crearSocioBase(nombre, apellido, dni, plan, fechaAlta, fechaNacimiento);

        LocalDate ultimoPago = hoy.minusDays(diasDesdeUltimoPago);
        Cuota pagada = crearCuota(socio, ultimoPago, ultimoPago, plan.getPrecio(), EstadoCuota.PAGADA);
        crearPago(pagada, ultimoPago, plan.getPrecio());

        LocalDate proximoVencimiento = hoy.plusDays(diasProximoVencimiento);
        crearCuota(socio, proximoVencimiento, null, plan.getPrecio(), EstadoCuota.PENDIENTE);

        socio.setFechaVencimientoPlan(proximoVencimiento);
        socioRepository.save(socio);
    }

    private void alDiaConLesion(String nombre, String apellido, String dni, Plan plan, LocalDate hoy,
                                 int diasProximoVencimiento, int diasDesdeUltimoPago, String detalleLesiones) {
        alDia(nombre, apellido, dni, plan, hoy, diasProximoVencimiento, diasDesdeUltimoPago, null);
        Socio socio = socioRepository.findAll().stream()
                .filter(s -> dni.equals(s.getDni()))
                .findFirst()
                .orElseThrow();
        socio.setTieneLesiones(true);
        socio.setDetalleLesiones(detalleLesiones);
        socioRepository.save(socio);
    }

    private void pagoHoy(String nombre, String apellido, String dni, Plan plan, LocalDate hoy) {
        alDia(nombre, apellido, dni, plan, hoy, 30, 0, null);
    }

    private void vencePronto(String nombre, String apellido, String dni, Plan plan, LocalDate hoy, int diasParaVencer) {
        LocalDate fechaAlta = hoy.minusMonths(plan.getDuracionMeses());
        Socio socio = crearSocioBase(nombre, apellido, dni, plan, fechaAlta, null);

        LocalDate pagoAnterior = hoy.minusMonths(plan.getDuracionMeses()).plusDays(2);
        Cuota pagada = crearCuota(socio, pagoAnterior, pagoAnterior, plan.getPrecio(), EstadoCuota.PAGADA);
        crearPago(pagada, pagoAnterior, plan.getPrecio());

        LocalDate proximoVencimiento = hoy.plusDays(diasParaVencer);
        crearCuota(socio, proximoVencimiento, null, plan.getPrecio(), EstadoCuota.PENDIENTE);

        socio.setFechaVencimientoPlan(proximoVencimiento);
        socioRepository.save(socio);
    }

    private void moroso(String nombre, String apellido, String dni, Plan plan, LocalDate hoy, int diasAtraso) {
        LocalDate vencimientoVencido = hoy.minusDays(diasAtraso);
        LocalDate fechaAlta = vencimientoVencido.minusMonths(plan.getDuracionMeses());
        Socio socio = crearSocioBase(nombre, apellido, dni, plan, fechaAlta, null);

        LocalDate pagoAnterior = fechaAlta.plusDays(2);
        Cuota pagada = crearCuota(socio, pagoAnterior, pagoAnterior, plan.getPrecio(), EstadoCuota.PAGADA);
        crearPago(pagada, pagoAnterior, plan.getPrecio());

        crearCuota(socio, vencimientoVencido, null, plan.getPrecio(), EstadoCuota.VENCIDA);

        socio.setEstado(diasAtraso >= 20 ? EstadoSocio.INACTIVO : EstadoSocio.MOROSO);
        socio.setFechaVencimientoPlan(vencimientoVencido);
        socioRepository.save(socio);
    }

    private void altaConPagoInicial(String nombre, String apellido, String dni, Plan plan, LocalDate hoy,
                                     LocalDate fechaNacimiento) {
        Socio socio = crearSocioBase(nombre, apellido, dni, plan, hoy, fechaNacimiento);

        Cuota inicial = crearCuota(socio, hoy, hoy, plan.getPrecio(), EstadoCuota.PAGADA);
        crearPago(inicial, hoy, plan.getPrecio());

        LocalDate proximoVencimiento = hoy.plusMonths(plan.getDuracionMeses());
        crearCuota(socio, proximoVencimiento, null, plan.getPrecio(), EstadoCuota.PENDIENTE);

        socio.setFechaVencimientoPlan(proximoVencimiento);
        socioRepository.save(socio);
    }
}
