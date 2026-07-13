package com.equinox.EquinoxGym;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CobroServiceIntegrationTests {

    @Autowired
    private CobroService cobroService;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private CuotaRepository cuotaRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Test
    void altaConPlanGeneraUnaCuotaConElVencimientoCorrecto() {
        Plan plan = guardarPlan("Mensual alta", 1, "25000");
        LocalDate inicio = LocalDate.now().minusDays(3);

        Socio socio = cobroService.altaRapidaConPlan(
                "Ana", "Pérez", "30111222", null, null, null, plan, inicio);

        List<Cuota> cuotas = cuotasDe(socio);
        assertThat(cuotas).hasSize(1);
        assertThat(cuotas.get(0).getFechaVencimiento()).isEqualTo(inicio);
        assertThat(cuotas.get(0).getEstado()).isEqualTo(EstadoCuota.PENDIENTE);
    }

    @Test
    void altaCobradaHoyVenceAlFinalDeUnSoloPeriodo() {
        Plan plan = guardarPlan("Mensual alta cobrada", 1, "27000");
        LocalDate hoy = LocalDate.now();

        Socio socio = cobroService.altaRapidaConPlanYCobro(
                "Martina", "Gómez", "32111222", "3624000000", "martina@email.com",
                "Av. Central 123", hoy, true, "Lesión leve de rodilla",
                plan, hoy, true, plan.getPrecio(), "Efectivo");

        assertThat(socio.getFechaInicioPlan()).isEqualTo(hoy);
        assertThat(socio.getFechaVencimientoPlan()).isEqualTo(hoy.plusMonths(1));
        assertThat(socio.getDiasRestantesPlan()).isBetween(28L, 31L);
        assertThat(socio.getDomicilioActual()).isEqualTo("Av. Central 123");
        assertThat(socio.isTieneLesiones()).isTrue();
        assertThat(socio.getDetalleLesiones()).contains("rodilla");
        assertThat(cuotasDe(socio)).extracting(Cuota::getFechaVencimiento)
                .containsExactlyInAnyOrder(hoy, hoy.plusMonths(1));
    }

    @Test
    void pagoAnticipadoConservaElCicloOriginalDeRenovacion() {
        Plan plan = guardarPlan("Mensual anticipado", 1, "30000");
        Socio socio = guardarSocio("Carlos", "40111222");
        LocalDate vencimientoOriginal = LocalDate.now().plusDays(5);
        Cuota cuota = guardarCuota(socio, vencimientoOriginal, plan.getPrecio());
        socio.setPlan(plan);
        socioRepository.save(socio);

        cobroService.registrarPago(cuota, plan.getPrecio(), "Efectivo");

        assertThat(cuota.getFechaPago()).isEqualTo(LocalDate.now());
        assertThat(cuota.getEstado()).isEqualTo(EstadoCuota.PAGADA);
        assertThat(cuotasDe(socio)).extracting(Cuota::getFechaVencimiento)
                .containsExactlyInAnyOrder(vencimientoOriginal, vencimientoOriginal.plusMonths(1));
        assertThat(socio.getFechaInicioPlan()).isEqualTo(vencimientoOriginal);
        assertThat(socio.getFechaVencimientoPlan()).isEqualTo(vencimientoOriginal.plusMonths(1));
    }

    @Test
    void pagoVencidoReiniciaLaVigenciaDesdeLaFechaRealDePago() {
        Plan plan = guardarPlan("Mensual vencido", 1, "32000");
        Socio socio = guardarSocio("Laura", "50111222");
        Cuota cuota = guardarCuota(socio, LocalDate.now().minusDays(10), plan.getPrecio());
        socio.setPlan(plan);
        socioRepository.save(socio);

        cobroService.registrarPago(cuota, plan.getPrecio(), "Transferencia");

        assertThat(cuotasDe(socio)).extracting(Cuota::getFechaVencimiento)
                .contains(LocalDate.now().plusMonths(1));
        assertThat(socio.getFechaInicioPlan()).isEqualTo(LocalDate.now());
        assertThat(socio.getFechaVencimientoPlan()).isEqualTo(LocalDate.now().plusMonths(1));
    }

    @Test
    void unaCuotaNoPuedeCobrarseDosVeces() {
        Plan plan = guardarPlan("Mensual pago unico", 1, "28000");
        Socio socio = guardarSocio("Mario", "60111222");
        Cuota cuota = guardarCuota(socio, LocalDate.now(), plan.getPrecio());
        socio.setPlan(plan);
        socioRepository.save(socio);

        cobroService.registrarPago(cuota, plan.getPrecio(), "Tarjeta");

        assertThatThrownBy(() -> cobroService.registrarPago(cuota, plan.getPrecio(), "Efectivo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya fue pagada");
        assertThat(pagoRepository.findByCuota_Socio_Id(socio.getId())).hasSize(1);
    }

    @Test
    void noPermiteAsignarUnPlanInactivo() {
        Plan plan = guardarPlan("Plan discontinuado", 1, "20000");
        plan.setActivo(false);
        planRepository.save(plan);
        Socio socio = guardarSocio("Sofía", "70111222");

        assertThatThrownBy(() -> cobroService.asignarPlanAExistente(socio, plan, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no está activo");
    }

    @Test
    void anularPagoConservaAuditoriaYRevierteCuotaYRenovacion() {
        Plan plan = guardarPlan("Mensual anulación", 1, "35000");
        Socio socio = guardarSocio("Lucía", "80111222");
        Cuota cuota = guardarCuota(socio, LocalDate.now().plusDays(2), plan.getPrecio());
        socio.setPlan(plan);
        socioRepository.save(socio);

        Pago pago = cobroService.registrarPago(cuota, plan.getPrecio(), "Efectivo");
        Long renovacionId = pago.getCuotaRenovacionGenerada().getId();

        cobroService.anularPago(pago.getId(), "Carga realizada por error");

        Pago pagoAnulado = pagoRepository.findById(pago.getId()).orElseThrow();
        assertThat(pagoAnulado.isAnulado()).isTrue();
        assertThat(pagoAnulado.getMotivoAnulacion()).isEqualTo("Carga realizada por error");
        assertThat(pagoAnulado.getAnuladoPor()).isEqualTo("sistema");
        assertThat(pagoAnulado.getFechaAnulacion()).isNotNull();
        assertThat(cuotaRepository.findById(cuota.getId()).orElseThrow().getFechaPago()).isNull();
        assertThat(cuotaRepository.findById(renovacionId)).isEmpty();
        assertThat(pagoRepository.obtenerTotalRecaudadoDelMes(
                LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void noPermiteAnularSiLaRenovacionYaFueCobrada() {
        Plan plan = guardarPlan("Mensual cadena", 1, "36000");
        Socio socio = guardarSocio("Diego", "90111222");
        Cuota cuota = guardarCuota(socio, LocalDate.now(), plan.getPrecio());
        socio.setPlan(plan);
        socioRepository.save(socio);

        Pago primerPago = cobroService.registrarPago(cuota, plan.getPrecio(), "Efectivo");
        Cuota renovacion = primerPago.getCuotaRenovacionGenerada();
        cobroService.registrarPago(renovacion, plan.getPrecio(), "Transferencia");

        assertThatThrownBy(() -> cobroService.anularPago(primerPago.getId(), "Intento de reversión tardía"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("renovación siguiente ya tiene un pago");
    }

    @Test
    void pagoHistoricoSinAuditoriaRequiereRevisionManual() {
        Socio socio = guardarSocio("Elena", "91111222");
        Cuota cuota = guardarCuota(socio, LocalDate.now(), new BigDecimal("15000"));
        cuota.setFechaPago(LocalDate.now());
        cuota.setEstado(EstadoCuota.PAGADA);
        cuotaRepository.save(cuota);

        Pago historico = new Pago();
        historico.setCuota(cuota);
        historico.setFechaPago(LocalDate.now());
        historico.setMonto(new BigDecimal("15000"));
        historico.setMedioPago("Efectivo");
        pagoRepository.save(historico);

        assertThatThrownBy(() -> cobroService.anularPago(historico.getId(), "Corrección de registro antiguo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revisión manual");
    }

    private Plan guardarPlan(String nombre, int meses, String precio) {
        Plan plan = new Plan();
        plan.setNombre(nombre);
        plan.setDuracionMeses(meses);
        plan.setPrecio(new BigDecimal(precio));
        plan.setActivo(true);
        return planRepository.save(plan);
    }

    private Socio guardarSocio(String nombre, String dni) {
        Socio socio = new Socio();
        socio.setNombre(nombre);
        socio.setApellido("Prueba");
        socio.setDni(dni);
        socio.setEstado(EstadoSocio.ACTIVO);
        return socioRepository.save(socio);
    }

    private Cuota guardarCuota(Socio socio, LocalDate vencimiento, BigDecimal monto) {
        Cuota cuota = new Cuota();
        cuota.setSocio(socio);
        cuota.setFechaVencimiento(vencimiento);
        cuota.setMonto(monto);
        cuota.setEstado(EstadoCuota.PENDIENTE);
        return cuotaRepository.save(cuota);
    }

    private List<Cuota> cuotasDe(Socio socio) {
        return cuotaRepository.findAll().stream()
                .filter(c -> c.getSocio() != null && c.getSocio().getId().equals(socio.getId()))
                .toList();
    }
}
