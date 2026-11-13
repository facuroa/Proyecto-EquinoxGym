package com.equinox.EquinoxGym;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Errores encontrados en la primera semana de uso en Tropa Gym y la
 * eliminacion definitiva que se agrego para corregir cargas equivocadas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CorreccionesYEliminacionTests {

    private static final String ADMIN = "admin-correcciones";

    @Autowired private MockMvc mockMvc;
    @Autowired private SocioRepository socioRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private CuotaRepository cuotaRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private MovimientoCajaRepository movimientoRepository;
    @Autowired private AsistenciaRepository asistenciaRepository;
    @Autowired private EventoAuditoriaRepository eventoRepository;
    @Autowired private SocioService socioService;
    @Autowired private CobroService cobroService;
    @Autowired private CajaService cajaService;
    @Autowired private AsistenciaService asistenciaService;
    @Autowired private EliminacionService eliminacionService;
    @Autowired private IndiceDniUnico indiceDniUnico;

    // ------------------------------------------------------------------
    // Socios con el mismo DNI (el error 500 al guardar)
    // ------------------------------------------------------------------

    @Test
    void laBaseNoAceptaDosSociosConElMismoDni() {
        String dni = dniNuevo();
        socioRepository.saveAndFlush(socio("Primero", dni));

        assertThatThrownBy(() -> socioRepository.saveAndFlush(socio("Segundo", dni)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void alArrancarQuedaActivoElControlDeDniEnLaBase() {
        assertThat(indiceDniUnico.asegurar()).isTrue();
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void elFormularioAvisaElDniRepetidoEnVezDeFallar() throws Exception {
        Socio existente = socioRepository.save(socio("Existente", dniNuevo()));
        Socio otro = socioRepository.save(socio("Otro", dniNuevo()));

        mockMvc.perform(post("/socios/guardar").with(csrf())
                        .param("nombre", "Nuevo").param("apellido", "Repetido").param("dni", existente.getDni()))
                .andExpect(status().isOk())
                .andExpect(view().name("nuevo-socio"))
                .andExpect(content().string(containsString("Ya existe un socio con ese DNI")));

        // Editar a otro socio poniendole un DNI ya usado tampoco rompe.
        mockMvc.perform(post("/socios/guardar").with(csrf())
                        .param("id", otro.getId().toString())
                        .param("nombre", "Otro").param("apellido", "Prueba").param("dni", existente.getDni()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya existe otro socio con ese DNI")))
                .andExpect(content().string(containsString("eliminar el repetido desde su ficha")));
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void unaFechaImposibleSeAvisaEnCastellano() throws Exception {
        mockMvc.perform(post("/socios/guardar").with(csrf())
                        .param("nombre", "Fecha").param("apellido", "Mala").param("dni", dniNuevo())
                        .param("fechaNacimiento", "2026-13-45"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ingresá una fecha válida.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Failed to convert"))));
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void guardarUnSocioSinCambiosVuelveAlListadoConAviso() throws Exception {
        Socio socio = socioRepository.save(socio("Sin", dniNuevo()));

        MvcResult resultado = mockMvc.perform(post("/socios/guardar").with(csrf())
                        .param("id", socio.getId().toString())
                        .param("nombre", "Sin").param("apellido", "Prueba").param("dni", socio.getDni()))
                .andExpect(redirectedUrl("/socios"))
                .andReturn();

        assertThat(resultado.getFlashMap().get("mensaje")).isEqualTo("Se guardaron los datos de Sin Prueba.");
    }

    // ------------------------------------------------------------------
    // "Vence plan" y cuota pendiente siempre de acuerdo
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void cambiarLaFechaDeInicioMueveLaCuotaPendiente() throws Exception {
        Plan plan = plan("Planificación Atletas", "55000");
        LocalDate inicioViejo = LocalDate.now().minusMonths(1).minusDays(9);
        // Como Rocio Pineda: una cuota impaga que vencio hace 9 dias.
        Socio socio = socioConCuota("Rocio", plan, inicioViejo, inicioViejo.plusMonths(1));
        socioService.actualizarEstadoSocio(socio);
        assertThat(socio.getEstado()).isEqualTo(EstadoSocio.MOROSO);

        LocalDate inicioNuevo = LocalDate.now().minusDays(9);
        mockMvc.perform(post("/socios/guardar").with(csrf())
                        .param("id", socio.getId().toString())
                        .param("nombre", "Rocio").param("apellido", "Prueba").param("dni", socio.getDni())
                        .param("planId", plan.getId().toString())
                        .param("fechaInicioPlan", inicioNuevo.toString()))
                .andExpect(redirectedUrl("/socios"));

        Socio actualizado = socioRepository.findById(socio.getId()).orElseThrow();
        Cuota pendiente = actualizado.getCuotas().get(0);
        assertThat(pendiente.getFechaVencimiento()).isEqualTo(inicioNuevo.plusMonths(1));
        assertThat(pendiente.getEstado()).isEqualTo(EstadoCuota.PENDIENTE);
        assertThat(actualizado.getFechaVencimientoPlan()).isEqualTo(pendiente.getFechaVencimiento());
        assertThat(actualizado.getFechaInicioPlan()).isEqualTo(inicioNuevo);
        assertThat(actualizado.getEstado()).isEqualTo(EstadoSocio.ACTIVO);
        assertThat(ultimoEvento().getCategoria()).isEqualTo("Cuota reprogramada");
        assertThat(ultimoEvento().getDetalle()).contains("→");
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void conVariasCuotasImpagasNoMueveNadaYAvisa() throws Exception {
        Plan plan = plan("Crossfit", "50000");
        Socio socio = socioConCuota("Varias", plan, LocalDate.now().minusMonths(2), LocalDate.now().minusMonths(1));
        Cuota segunda = cuotaRepository.saveAndFlush(cuota(socio, LocalDate.now().minusDays(1)));
        socio.getCuotas().add(segunda);

        MvcResult resultado = mockMvc.perform(post("/socios/guardar").with(csrf())
                        .param("id", socio.getId().toString())
                        .param("nombre", "Varias").param("apellido", "Prueba").param("dni", socio.getDni())
                        .param("planId", plan.getId().toString())
                        .param("fechaInicioPlan", LocalDate.now().toString()))
                .andExpect(redirectedUrl("/socios"))
                .andReturn();

        assertThat(resultado.getFlashMap().get("error")).asString().contains("2 cuotas impagas");
        assertThat(cuotaRepository.findById(segunda.getId()).orElseThrow().getFechaVencimiento())
                .isEqualTo(LocalDate.now().minusDays(1));
    }

    @Test
    void venceplanSeCorrigeSoloConLaCuotaPendiente() {
        Plan plan = plan("Mensual", "40000");
        LocalDate vencimiento = LocalDate.now().minusDays(9);
        Socio socio = socioConCuota("Desalineado", plan, vencimiento.minusMonths(1), vencimiento);
        // Datos como los que quedaron en el gimnasio: dos fechas distintas.
        socio.setFechaVencimientoPlan(LocalDate.now().plusDays(21));
        socio.setFechaInicioPlan(LocalDate.now().minusDays(9));

        assertThat(socioService.actualizarEstadoSocio(socio)).isTrue();
        assertThat(socio.getFechaVencimientoPlan()).isEqualTo(vencimiento);
        assertThat(socio.getFechaInicioPlan()).isEqualTo(vencimiento.minusMonths(1));
        assertThat(socio.getEstado()).isEqualTo(EstadoSocio.MOROSO);
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void editarLaFechaDeUnaCuotaActualizaVencePlan() throws Exception {
        Plan plan = plan("Mensual edit", "40000");
        Socio socio = socioConCuota("Edita", plan, LocalDate.now(), LocalDate.now().plusMonths(1));
        Cuota cuota = socio.getCuotas().get(0);
        LocalDate nueva = LocalDate.now().plusDays(5);

        mockMvc.perform(post("/cuotas/actualizar/{id}", cuota.getId()).with(csrf())
                        .param("socioId", socio.getId().toString())
                        .param("monto", "40000")
                        .param("fechaVencimiento", nueva.toString()))
                .andExpect(redirectedUrl("/cuotas"));

        assertThat(socioRepository.findById(socio.getId()).orElseThrow().getFechaVencimientoPlan()).isEqualTo(nueva);
    }

    // ------------------------------------------------------------------
    // Cuotas: eliminar elimina, y las inexistentes no rompen
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = "recepcion-correcciones", roles = "RECEPCIONISTA")
    void eliminarUnaCuotaSinPagosLaBorraDeVerdad() throws Exception {
        Plan plan = plan("Mensual borra", "40000");
        Socio socio = socioConCuota("Borra", plan, LocalDate.now(), LocalDate.now().plusMonths(1));
        Long cuotaId = socio.getCuotas().get(0).getId();

        MvcResult resultado = mockMvc.perform(post("/cuotas/eliminar/{id}", cuotaId).with(csrf()))
                .andExpect(redirectedUrl("/cuotas"))
                .andReturn();

        assertThat(resultado.getFlashMap().get("mensaje")).asString().startsWith("Se eliminó: Cuota de Borra Prueba");
        assertThat(cuotaRepository.findById(cuotaId)).isEmpty();
        assertThat(socioRepository.findById(socio.getId()).orElseThrow().getCuotas()).isEmpty();
        assertThat(ultimoEvento().getCategoria()).isEqualTo("Cuota eliminada");
        assertThat(ultimoEvento().getUsuario()).isEqualTo("recepcion-correcciones");
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void unaCuotaInexistenteNoDaError500() throws Exception {
        mockMvc.perform(get("/cuotas/editar/{id}", 999_999_999L)).andExpect(redirectedUrl("/cuotas"));
        mockMvc.perform(post("/cuotas/actualizar/{id}", 999_999_999L).with(csrf())
                        .param("socioId", "1").param("monto", "1").param("fechaVencimiento", "2026-01-01"))
                .andExpect(redirectedUrl("/cuotas"));
        mockMvc.perform(post("/cuotas/eliminar/{id}", 999_999_999L).with(csrf()))
                .andExpect(redirectedUrl("/cuotas"));
    }

    // ------------------------------------------------------------------
    // Cobro doble
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void unaCuotaNoSePuedeCobrarDosVeces() {
        cajaService.abrirCaja(ADMIN, BigDecimal.ZERO, null);
        Plan plan = plan("Mensual doble", "40000");
        Socio socio = socioConCuota("Doble", plan, LocalDate.now(), LocalDate.now());
        Cuota cuota = socio.getCuotas().get(0);

        cobroService.registrarPago(cuota, new BigDecimal("40000"), "Efectivo");

        assertThatThrownBy(() -> cobroService.registrarPagoPorId(cuota.getId(), new BigDecimal("40000"), "Efectivo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya fue pagada");
        assertThat(pagoRepository.findByCuota_Id(cuota.getId())).hasSize(1);
    }

    // ------------------------------------------------------------------
    // Eliminacion definitiva
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = "recepcion-correcciones", roles = "RECEPCIONISTA")
    void laEliminacionDefinitivaEsSoloParaAdministradores() throws Exception {
        Socio socio = socioRepository.save(socio("Protegido", dniNuevo()));

        mockMvc.perform(post("/eliminar-definitivo/socio/{id}", socio.getId()).with(csrf())
                        .param("confirmacion", "ELIMINAR").param("motivo", "intento de recepcion"))
                .andExpect(status().isForbidden());
        assertThat(socioRepository.findById(socio.getId())).isPresent();
    }

    @Test
    void sinLaPalabraOElMotivoNoSeEliminaNada() {
        Socio socio = socioRepository.save(socio("Confirma", dniNuevo()));

        assertThatThrownBy(() -> eliminacionService.eliminarSocio(socio.getId(), "si", "motivo valido", ADMIN))
                .hasMessageContaining("escribí ELIMINAR");
        assertThatThrownBy(() -> eliminacionService.eliminarSocio(socio.getId(), "eliminar", "ok", ADMIN))
                .hasMessageContaining("por qué se elimina");
        assertThat(socioRepository.findById(socio.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void eliminarUnPagoDeLaCajaAbiertaDejaLaCuotaPendienteYCorrigeLaCaja() {
        CajaSesion caja = cajaService.abrirCaja(ADMIN, new BigDecimal("1000"), null);
        Plan plan = plan("Mensual pago", "40000");
        Socio socio = socioConCuota("Pago", plan, LocalDate.now(), LocalDate.now());
        Cuota cuota = socio.getCuotas().get(0);
        Pago pago = cobroService.registrarPago(cuota, new BigDecimal("40000"), "Efectivo");
        Long renovacionId = pago.getCuotaRenovacionGenerada().getId();
        assertThat(cajaService.obtenerResumen(ADMIN, true).getSaldoEsperado()).isEqualByComparingTo("41000");

        String numero = eliminacionService.eliminarPago(pago.getId(), "ELIMINAR", "cobro cargado por error", ADMIN);

        assertThat(numero).isEqualTo(pago.getNumeroComprobante());
        assertThat(pagoRepository.findById(pago.getId())).isEmpty();
        assertThat(cuotaRepository.findById(cuota.getId()).orElseThrow().getFechaPago()).isNull();
        assertThat(cuotaRepository.findById(renovacionId)).isEmpty();
        assertThat(movimientoRepository.findByCaja_IdOrderByFechaRegistroDescIdDesc(caja.getId())).isEmpty();
        assertThat(cajaService.obtenerResumen(ADMIN, true).getSaldoEsperado()).isEqualByComparingTo("1000");
        assertThat(socioRepository.findById(socio.getId()).orElseThrow().getFechaVencimientoPlan())
                .isEqualTo(cuota.getFechaVencimiento());

        EventoAuditoria evento = ultimoEvento();
        assertThat(evento.getCategoria()).isEqualTo("Pago eliminado (definitivo)");
        assertThat(evento.getUsuario()).isEqualTo(ADMIN);
        assertThat(evento.getDetalle()).contains(numero, "$ 40000", "1 movimiento quitado de la caja abierta",
                "motivo: cobro cargado por error");
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void eliminarUnPagoDeUnaCajaCerradaNoAlteraElArqueo() {
        CajaSesion caja = cajaService.abrirCaja(ADMIN, BigDecimal.ZERO, null);
        Plan plan = plan("Mensual cerrada", "40000");
        Socio socio = socioConCuota("Cerrada", plan, LocalDate.now(), LocalDate.now());
        Pago pago = cobroService.registrarPago(socio.getCuotas().get(0), new BigDecimal("40000"), "Efectivo");
        cajaService.cerrarCaja(ADMIN, caja.getId(), new BigDecimal("40000"), null);

        eliminacionService.eliminarPago(pago.getId(), "ELIMINAR", "prueba en caja cerrada", ADMIN);

        List<MovimientoCaja> movimientos = movimientoRepository.findByCaja_IdOrderByFechaRegistroDescIdDesc(caja.getId());
        assertThat(movimientos).hasSize(1);
        assertThat(movimientos.get(0).getPago()).isNull();
        assertThat(movimientos.get(0).getMonto()).isEqualByComparingTo("40000");
        assertThat(ultimoEvento().getDetalle()).contains("1 movimiento conservado en cajas cerradas");
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void noSeEliminaUnPagoSiLaRenovacionQueGeneroYaSeCobro() {
        cajaService.abrirCaja(ADMIN, BigDecimal.ZERO, null);
        Plan plan = plan("Mensual cadena", "40000");
        Socio socio = socioConCuota("Cadena", plan, LocalDate.now(), LocalDate.now());
        Pago primero = cobroService.registrarPago(socio.getCuotas().get(0), new BigDecimal("40000"), "Efectivo");
        cobroService.registrarPago(primero.getCuotaRenovacionGenerada(), new BigDecimal("40000"), "Efectivo");

        assertThatThrownBy(() -> eliminacionService.eliminarPago(primero.getId(), "ELIMINAR", "orden equivocado", ADMIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Eliminá primero ese pago");
        assertThat(pagoRepository.findById(primero.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void eliminarUnaCuotaPagadaBorraSusPagosYConservaLaRenovacion() {
        cajaService.abrirCaja(ADMIN, BigDecimal.ZERO, null);
        Plan plan = plan("Mensual cuota", "40000");
        Socio socio = socioConCuota("Cuota", plan, LocalDate.now(), LocalDate.now());
        Cuota cuota = socio.getCuotas().get(0);
        Pago pago = cobroService.registrarPago(cuota, new BigDecimal("40000"), "Efectivo");
        Long renovacionId = pago.getCuotaRenovacionGenerada().getId();

        eliminacionService.eliminarCuota(cuota.getId(), "ELIMINAR", "cuota duplicada", ADMIN);

        assertThat(cuotaRepository.findById(cuota.getId())).isEmpty();
        assertThat(pagoRepository.findById(pago.getId())).isEmpty();
        assertThat(cuotaRepository.findById(renovacionId)).isPresent();
        assertThat(ultimoEvento().getCategoria()).isEqualTo("Cuota eliminada (definitivo)");
        assertThat(ultimoEvento().getDetalle()).contains("1 pago eliminado por $ 40000");
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void eliminarUnSocioBorraTodoSuHistorialYLoDejaAuditado() throws Exception {
        cajaService.abrirCaja(ADMIN, BigDecimal.ZERO, null);
        Plan plan = plan("Mensual socio", "40000");
        Socio socio = socioConCuota("Duplicado", plan, LocalDate.now(), LocalDate.now());
        Pago pago = cobroService.registrarPago(socio.getCuotas().get(0), new BigDecimal("40000"), "Efectivo");
        asistenciaService.registrarIngreso(socio.getId(), false, ADMIN);
        Long socioId = socio.getId();

        mockMvc.perform(get("/socios/ver/{id}", socioId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Eliminar socio definitivamente")))
                .andExpect(content().string(containsString("2 cuotas, 1 pago por $ 40000 y 1 ingreso.")));

        MvcResult resultado = mockMvc.perform(post("/eliminar-definitivo/socio/{id}", socioId).with(csrf())
                        .param("confirmacion", " eliminar ").param("motivo", "socio cargado dos veces"))
                .andExpect(redirectedUrl("/socios"))
                .andReturn();

        assertThat(resultado.getFlashMap().get("mensaje")).asString().contains("Duplicado Prueba");
        assertThat(socioRepository.findById(socioId)).isEmpty();
        assertThat(pagoRepository.findById(pago.getId())).isEmpty();
        assertThat(asistenciaRepository.countBySocio_Id(socioId)).isZero();
        EventoAuditoria evento = ultimoEvento();
        assertThat(evento.getCategoria()).isEqualTo("Socio eliminado (definitivo)");
        assertThat(evento.getDetalle()).contains("Duplicado Prueba", "2 cuotas", "1 pago por $ 40000",
                "1 ingreso", "motivo: socio cargado dos veces");
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void unErrorDeEliminacionVuelveALaFichaConElMotivo() throws Exception {
        Socio socio = socioRepository.save(socio("Vuelve", dniNuevo()));

        MvcResult resultado = mockMvc.perform(post("/eliminar-definitivo/socio/{id}", socio.getId()).with(csrf())
                        .param("confirmacion", "BORRAR").param("motivo", "motivo valido"))
                .andExpect(redirectedUrl("/socios/ver/" + socio.getId()))
                .andReturn();
        assertThat(resultado.getFlashMap().get("error")).asString().contains("escribí ELIMINAR");
    }

    @Test
    @WithMockUser(username = "recepcion-correcciones", roles = "RECEPCIONISTA")
    void laRecepcionNoVeLosBotonesDeEliminacionDefinitiva() throws Exception {
        Socio socio = socioRepository.save(socio("Recep", dniNuevo()));
        mockMvc.perform(get("/socios/ver/{id}", socio.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Eliminar socio definitivamente"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("eliminarDefinitivoModal"))));
    }

    // ------------------------------------------------------------------
    // Pagina de error
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void laPaginaDeErrorEsLaDelSistema() throws Exception {
        mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .requestAttr("jakarta.servlet.error.status_code", 404)
                        .requestAttr("jakarta.servlet.error.request_uri", "/no-existe"))
                .andExpect(content().string(containsString("No encontramos esa página")))
                .andExpect(content().string(containsString("Ir al inicio")));
        mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .requestAttr("jakarta.servlet.error.status_code", 500))
                .andExpect(content().string(containsString("Algo salió mal")))
                .andExpect(content().string(containsString("no se guardó nada a medias")));
    }

    // ------------------------------------------------------------------
    // Datos de prueba
    // ------------------------------------------------------------------

    private EventoAuditoria ultimoEvento() {
        return eventoRepository.findTop100ByOrderByFechaRegistroDescIdDesc().get(0);
    }

    private Plan plan(String nombre, String precio) {
        Plan plan = new Plan();
        plan.setNombre(nombre + " " + UUID.randomUUID().toString().substring(0, 6));
        plan.setPrecio(new BigDecimal(precio));
        plan.setDuracionMeses(1);
        return planRepository.save(plan);
    }

    private Socio socio(String nombre, String dni) {
        Socio socio = new Socio();
        socio.setNombre(nombre);
        socio.setApellido("Prueba");
        socio.setDni(dni);
        socio.setEstado(EstadoSocio.ACTIVO);
        return socio;
    }

    private Socio socioConCuota(String nombre, Plan plan, LocalDate inicio, LocalDate vencimientoCuota) {
        Socio socio = socio(nombre, dniNuevo());
        socio.setPlan(plan);
        socio.setFechaInicioPlan(inicio);
        socio.setFechaVencimientoPlan(inicio.plusMonths(plan.getDuracionMeses()));
        socio.getCuotas().add(cuota(socio, vencimientoCuota));
        return socioRepository.saveAndFlush(socio);
    }

    private Cuota cuota(Socio socio, LocalDate vencimiento) {
        Cuota cuota = new Cuota();
        cuota.setSocio(socio);
        cuota.setFechaVencimiento(vencimiento);
        cuota.setMonto(socio.getPlan() != null ? socio.getPlan().getPrecio() : new BigDecimal("1000"));
        cuota.setEstado(EstadoCuota.PENDIENTE);
        return cuota;
    }

    private static String dniNuevo() {
        return Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits()).substring(0, 9);
    }
}
