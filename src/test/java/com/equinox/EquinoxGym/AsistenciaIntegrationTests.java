package com.equinox.EquinoxGym;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AsistenciaIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AsistenciaService asistenciaService;

    @Autowired
    private AsistenciaRepository asistenciaRepository;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private SocioService socioService;

    /** Proximo numero de turno por socio y fecha, para cargar ingresos de prueba. */
    private final Map<String, Integer> turnos = new HashMap<>();

    // ------------------------------------------------------------------
    // Registro de ingresos
    // ------------------------------------------------------------------

    @Test
    void registraElIngresoDeUnSocioAlDia() {
        Socio socio = guardarSocioAlDia("Martina");

        IngresoRegistradoDTO ingreso = asistenciaService.registrarIngreso(socio.getId(), false, "recepcion");

        assertThat(ingreso.getResultado()).isEqualTo(ResultadoIngreso.REGISTRADO);
        assertThat(ingreso.getEstado()).isEqualTo("ACTIVO");
        assertThat(ingreso.isConDeuda()).isFalse();
        assertThat(ingreso.getTono()).isEqualTo("ok");
        assertThat(ingreso.getTextoCuota()).isEqualTo("Faltan 12 días");
        assertThat(ingreso.getHora()).matches("\\d{2}:\\d{2}");

        List<Asistencia> guardados = asistenciaRepository
                .findBySocio_IdAndFechaOrderByNumeroTurnoAsc(socio.getId(), LocalDate.now());
        assertThat(guardados).hasSize(1);
        assertThat(guardados.get(0).getNumeroTurno()).isEqualTo(1);
        assertThat(guardados.get(0).isSegundoTurno()).isFalse();
        assertThat(guardados.get(0).getRegistradoPor()).isEqualTo("recepcion");
    }

    @Test
    void elSegundoIngresoDelDiaSoloSeGuardaSiSeFuerzaYElTerceroNunca() {
        Socio socio = guardarSocioAlDia("Doble turno");
        asistenciaService.registrarIngreso(socio.getId(), false, "recepcion");

        IngresoRegistradoDTO repetido = asistenciaService.registrarIngreso(socio.getId(), false, "recepcion");
        assertThat(repetido.getResultado()).isEqualTo(ResultadoIngreso.YA_REGISTRADO);
        assertThat(repetido.getTono()).isEqualTo("repetido");
        assertThat(repetido.getHoraIngresoAnterior()).matches("\\d{2}:\\d{2}");
        assertThat(ingresosDeHoy(socio)).hasSize(1);

        IngresoRegistradoDTO forzado = asistenciaService.registrarIngreso(socio.getId(), true, "recepcion");
        assertThat(forzado.getResultado()).isEqualTo(ResultadoIngreso.REGISTRADO);
        assertThat(forzado.isSegundoTurno()).isTrue();
        assertThat(ingresosDeHoy(socio)).extracting(Asistencia::getNumeroTurno).containsExactly(1, 2);

        IngresoRegistradoDTO tercero = asistenciaService.registrarIngreso(socio.getId(), true, "recepcion");
        assertThat(tercero.getResultado()).isEqualTo(ResultadoIngreso.LIMITE_DIARIO);
        assertThat(tercero.getMaximoPorDia()).isEqualTo(2);
        assertThat(ingresosDeHoy(socio)).hasSize(2);
    }

    @Test
    void alSocioConCuotaVencidaNoSeLeBloqueaLaEntrada() {
        Socio moroso = guardarSocio("Moroso", LocalDate.now().minusDays(5));

        IngresoRegistradoDTO ingreso = asistenciaService.registrarIngreso(moroso.getId(), false, "recepcion");

        assertThat(ingreso.getResultado()).isEqualTo(ResultadoIngreso.REGISTRADO);
        assertThat(ingreso.getEstado()).isEqualTo("MOROSO");
        assertThat(ingreso.isConDeuda()).isTrue();
        assertThat(ingreso.getTono()).isEqualTo("aviso");
        assertThat(ingreso.getTextoCuota()).isEqualTo("Vencida hace 5 días");
        assertThat(ingresosDeHoy(moroso)).hasSize(1);
    }

    @Test
    void elSocioDadoDeBajaIngresaYSeInformaLaBaja() {
        Socio socio = guardarSocioAlDia("Baja");
        socio.setBaja(true);
        socio.setFechaBaja(LocalDate.of(2026, 3, 9));
        socioRepository.save(socio);

        IngresoRegistradoDTO ingreso = asistenciaService.registrarIngreso(socio.getId(), false, "recepcion");

        assertThat(ingreso.getResultado()).isEqualTo(ResultadoIngreso.REGISTRADO);
        assertThat(ingreso.getEstado()).isEqualTo("INACTIVO");
        assertThat(ingreso.getTextoCuota()).isEqualTo("Dado de baja el 09/03/2026");
    }

    @Test
    void rechazaUnSocioInexistenteOSinElegir() {
        assertThatThrownBy(() -> asistenciaService.registrarIngreso(999_999_999L, false, "recepcion"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya no existe");
        assertThatThrownBy(() -> asistenciaService.registrarIngreso(null, false, "recepcion"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Elegí un socio");
    }

    @Test
    void quitarUnIngresoDeHoyLoBorraYReacomodaElSegundoTurno() {
        Socio socio = guardarSocioAlDia("Corregido");
        asistenciaService.registrarIngreso(socio.getId(), false, "recepcion");
        asistenciaService.registrarIngreso(socio.getId(), true, "recepcion");
        Asistencia primero = ingresosDeHoy(socio).get(0);

        String nombre = asistenciaService.quitarIngreso(primero.getId());

        assertThat(nombre).isEqualTo("Corregido Prueba");
        List<Asistencia> restantes = ingresosDeHoy(socio);
        assertThat(restantes).hasSize(1);
        assertThat(restantes.get(0).getNumeroTurno()).isEqualTo(1);

        // Con el lugar liberado, el socio puede volver a registrar un segundo turno.
        assertThat(asistenciaService.registrarIngreso(socio.getId(), true, "recepcion").getResultado())
                .isEqualTo(ResultadoIngreso.REGISTRADO);
    }

    @Test
    void noSePuedenQuitarIngresosDeDiasAnterioresNiInexistentes() {
        Socio socio = guardarSocioAlDia("Historico");
        Asistencia ayer = guardarIngreso(socio, LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> asistenciaService.quitarIngreso(ayer.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("día de hoy");
        assertThatThrownBy(() -> asistenciaService.quitarIngreso(999_999_999L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(asistenciaRepository.findById(ayer.getId())).isPresent();
    }

    @Test
    void laBaseRechazaDosIngresosConElMismoTurnoParaElMismoSocioYDia() {
        Socio socio = guardarSocioAlDia("Doble clic");
        LocalDateTime momento = LocalDateTime.of(2001, 5, 7, 19, 0);
        asistenciaRepository.saveAndFlush(nuevoIngreso(socio, momento, 1));

        assertThatThrownBy(() -> asistenciaRepository.saveAndFlush(nuevoIngreso(socio, momento.plusSeconds(1), 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // Solapa "Hoy"
    // ------------------------------------------------------------------

    @Test
    void elResumenDeHoyListaLosIngresosYCuentaLosQueDeben() {
        Socio alDia = guardarSocioAlDia("Al dia");
        Socio moroso = guardarSocio("Con deuda", LocalDate.now().minusDays(3));
        asistenciaService.registrarIngreso(alDia.getId(), false, "recepcion");
        asistenciaService.registrarIngreso(moroso.getId(), false, "recepcion");

        AsistenciaHoyDTO hoy = asistenciaService.resumenDeHoy();

        assertThat(hoy.getIngresos()).extracting(IngresoDelDiaDTO::getSocioId)
                .contains(alDia.getId(), moroso.getId());
        assertThat(hoy.getTotal()).isEqualTo(hoy.getIngresos().size());
        assertThat(hoy.getSociosConDeuda()).isGreaterThanOrEqualTo(1);
        assertThat(hoy.getFranjas()).isNotEmpty();
        assertThat(hoy.getFranjas()).anyMatch(FranjaHorariaDTO::isPico);
        assertThat(hoy.getFranjas().stream().mapToLong(FranjaHorariaDTO::getCantidad).sum())
                .isEqualTo(hoy.getTotal());
    }

    @Test
    void quienLlegaUnosMinutosAntesCuentaParaLaFranjaSiguiente() {
        // La anticipacion por defecto es de 15 minutos.
        assertThat(asistenciaService.franjaDe(LocalDateTime.of(2026, 5, 4, 18, 50))).isEqualTo(19);
        assertThat(asistenciaService.franjaDe(LocalDateTime.of(2026, 5, 4, 18, 45))).isEqualTo(19);
        assertThat(asistenciaService.franjaDe(LocalDateTime.of(2026, 5, 4, 18, 44))).isEqualTo(18);
        assertThat(asistenciaService.franjaDe(LocalDateTime.of(2026, 5, 4, 19, 30))).isEqualTo(19);
        // Cerca de la medianoche no se pasa al dia siguiente.
        assertThat(asistenciaService.franjaDe(LocalDateTime.of(2026, 5, 4, 23, 55))).isEqualTo(23);
    }

    // ------------------------------------------------------------------
    // Solapa "Historial": semana tipica
    // ------------------------------------------------------------------

    @Test
    void laSemanaTipicaPromediaSoloLosDiasConRegistros() {
        Socio socio = guardarSocioAlDia("Grilla");
        // 2001-01-01 fue lunes. Dos lunes con gente y uno sin registros.
        cargarIngresos(socio, LocalDateTime.of(2001, 1, 1, 19, 5), 3);
        cargarIngresos(socio, LocalDateTime.of(2001, 1, 8, 19, 10), 3);
        // Martes: dos ingresos a las 7 y uno a las 18:50, que cuenta para las 19.
        cargarIngresos(socio, LocalDateTime.of(2001, 1, 2, 7, 0), 2);
        cargarIngresos(socio, LocalDateTime.of(2001, 1, 2, 18, 50), 1);

        SemanaTipicaDTO semana = asistenciaService.semanaTipica(new PeriodoAsistencia(
                PeriodoAsistencia.RANGO, LocalDate.of(2001, 1, 1), LocalDate.of(2001, 1, 21), null));

        assertThat(semana.isVacia()).isFalse();
        assertThat(semana.getDias()).containsExactly("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb");
        assertThat(semana.getDiasConRegistros()).isEqualTo(3);
        assertThat(semana.getTotalIngresos()).isEqualTo(9);
        assertThat(semana.getFilas()).extracting(SemanaTipicaDTO.Fila::getHora).containsExactly("07:00", "19:00");

        SemanaTipicaDTO.Fila siete = semana.getFilas().get(0);
        assertThat(siete.getCeldas()).extracting(SemanaTipicaDTO.Celda::getTexto)
                .containsExactly("·", "2", "·", "·", "·", "·");

        // Lunes 19:00: (3 + 3) / 2 lunes con registros. El tercer lunes no baja el promedio.
        SemanaTipicaDTO.Fila diecinueve = semana.getFilas().get(1);
        assertThat(diecinueve.getCeldas()).extracting(SemanaTipicaDTO.Celda::getTexto)
                .containsExactly("3", "1", "·", "·", "·", "·");
        assertThat(diecinueve.getCeldas().get(0).getNivel()).isEqualTo(4);
        assertThat(diecinueve.getCeldas().get(2).getNivel()).isZero();

        assertThat(semana.getFranjaMasConcurrida()).isEqualTo("Lun 19:00");
        assertThat(semana.getPromedioMasConcurrida()).isEqualTo(3);
        assertThat(semana.getCupoReferencia()).isZero();
        assertThat(semana.getCantidadPicos()).isZero();
        assertThat(semana.getAviso()).isNull();
        assertThat(semana.getTendenciaPorcentaje()).isNull();
    }

    @Test
    void conCupoDeReferenciaLaGrillaMarcaLasFranjasQueLoSuperan() {
        Socio socio = guardarSocioAlDia("Cupo");
        cargarIngresos(socio, LocalDateTime.of(2001, 1, 1, 19, 0), 3);
        cargarIngresos(socio, LocalDateTime.of(2001, 1, 2, 19, 0), 2);
        cargarIngresos(socio, LocalDateTime.of(2001, 1, 7, 10, 0), 1); // domingo

        AsistenciaService conCupo = new AsistenciaService(
                asistenciaRepository, socioRepository, socioService, "2", "15", "Tropa Gym");
        SemanaTipicaDTO semana = conCupo.semanaTipica(new PeriodoAsistencia(
                PeriodoAsistencia.RANGO, LocalDate.of(2001, 1, 1), LocalDate.of(2001, 1, 7), null));

        assertThat(semana.getDias()).hasSize(7).endsWith("Dom");
        assertThat(semana.getCupoReferencia()).isEqualTo(2);
        assertThat(semana.getCantidadPicos()).isEqualTo(1);
        assertThat(semana.getPicos()).containsExactly("Lun 19:00 (3)");
        SemanaTipicaDTO.Fila diecinueve = semana.getFilas().get(1);
        assertThat(diecinueve.getCeldas().get(0).isSuperaCupo()).isTrue();
        assertThat(diecinueve.getCeldas().get(1).isSuperaCupo()).isFalse();
        assertThat(semana.getAviso()).isEqualTo(AsistenciaService.AVISO_UNA_SEMANA);
    }

    @Test
    void enPeriodosLargosInformaSiLaAsistenciaCambio() {
        Socio socio = guardarSocioAlDia("Tendencia");
        cargarIngresos(socio, LocalDateTime.of(2001, 2, 6, 19, 0), 2);
        cargarIngresos(socio, LocalDateTime.of(2001, 3, 6, 19, 0), 4);

        SemanaTipicaDTO semana = asistenciaService.semanaTipica(new PeriodoAsistencia(
                PeriodoAsistencia.RANGO, LocalDate.of(2001, 2, 5), LocalDate.of(2001, 4, 1), null));

        assertThat(semana.getAviso()).isEqualTo(AsistenciaService.AVISO_VENTANA_LARGA);
        assertThat(semana.getTendenciaPorcentaje()).isEqualTo(100);
    }

    @Test
    void unPeriodoSinIngresosDevuelveLaGrillaVacia() {
        SemanaTipicaDTO semana = asistenciaService.semanaTipica(new PeriodoAsistencia(
                PeriodoAsistencia.RANGO, LocalDate.of(1990, 1, 1), LocalDate.of(1990, 1, 28), null));

        assertThat(semana.isVacia()).isTrue();
        assertThat(semana.getFranjaMasConcurrida()).isNull();
        assertThat(semana.getTotalIngresos()).isZero();
    }

    @Test
    void elPeriodoSiempreTerminaAyerYToleraParametrosInvalidos() {
        LocalDate ayer = LocalDate.now().minusDays(1);

        PeriodoAsistencia porDefecto = asistenciaService.resolverPeriodo("cualquiera", null, null);
        assertThat(porDefecto.getTipo()).isEqualTo("4");
        assertThat(porDefecto.getHasta()).isEqualTo(ayer);
        assertThat(porDefecto.getCantidadDias()).isEqualTo(28);
        assertThat(porDefecto.getError()).isNull();

        assertThat(asistenciaService.resolverPeriodo("1", null, null).getCantidadDias()).isEqualTo(7);
        assertThat(asistenciaService.resolverPeriodo("8", null, null).getCantidadDias()).isEqualTo(56);

        // Fechas invertidas: se ordenan solas. Un "hasta" futuro se recorta a ayer.
        PeriodoAsistencia rango = asistenciaService.resolverPeriodo(
                "rango", LocalDate.now().plusDays(3).toString(), ayer.minusDays(9).toString());
        assertThat(rango.isRango()).isTrue();
        assertThat(rango.getDesde()).isEqualTo(ayer.minusDays(9));
        assertThat(rango.getHasta()).isEqualTo(ayer);
        assertThat(rango.getError()).isNull();

        assertThat(asistenciaService.resolverPeriodo("rango", "no-es-fecha", ayer.toString()).getError())
                .contains("dos fechas");
        assertThat(asistenciaService.resolverPeriodo("rango", "", "").getTipo()).isEqualTo("4");
        assertThat(asistenciaService.resolverPeriodo(
                "rango", LocalDate.now().toString(), LocalDate.now().plusDays(5).toString()).getError())
                .contains("antes de hoy");
        assertThat(asistenciaService.resolverPeriodo(
                "rango", ayer.minusYears(3).toString(), ayer.toString()).getError())
                .contains("un año");
    }

    // ------------------------------------------------------------------
    // Solapa "Historial": socios que dejaron de venir
    // ------------------------------------------------------------------

    @Test
    void listaALosSociosAlDiaQueDejaronDeVenir() {
        Socio ausente = guardarSocioAlDia("Ausente");
        Socio constante = guardarSocioAlDia("Constante");
        Socio moroso = guardarSocio("Moroso ausente", LocalDate.now().minusDays(4));
        socioService.actualizarEstadoSocio(moroso);
        socioRepository.save(moroso);
        guardarIngreso(ausente, LocalDateTime.now().minusDays(12));
        guardarIngreso(constante, LocalDateTime.now().minusDays(2));
        guardarIngreso(moroso, LocalDateTime.now().minusDays(30));

        List<SocioAusenteDTO> conDiez = asistenciaService.sociosAusentes(10);
        assertThat(conDiez).extracting(SocioAusenteDTO::getSocioId)
                .contains(ausente.getId())
                .doesNotContain(constante.getId(), moroso.getId());
        SocioAusenteDTO fila = conDiez.stream()
                .filter(a -> a.getSocioId().equals(ausente.getId())).findFirst().orElseThrow();
        assertThat(fila.getDiasSinVenir()).isEqualTo(12);
        assertThat(fila.getUltimaVisita()).isEqualTo(LocalDate.now().minusDays(12));
        assertThat(fila.getVencimientoPlan()).isEqualTo(LocalDate.now().plusDays(12));

        assertThat(asistenciaService.sociosAusentes(15)).extracting(SocioAusenteDTO::getSocioId)
                .doesNotContain(ausente.getId());
    }

    @Test
    void alQueNuncaVinoSeLoCuentaDesdeQueElGimnasioEmpezoARegistrar() {
        Socio veterano = guardarSocioAlDia("Veterano");
        veterano.setFechaAlta(LocalDate.now().minusYears(2));
        Socio nuncaVino = guardarSocioAlDia("Nunca vino");
        nuncaVino.setFechaAlta(LocalDate.now().minusYears(1));
        Socio recienLlegado = guardarSocioAlDia("Recien llegado");
        recienLlegado.setFechaAlta(LocalDate.now().minusDays(3));
        socioRepository.saveAll(List.of(veterano, nuncaVino, recienLlegado));
        // El gimnasio empezo a cargar ingresos hace 11 dias.
        guardarIngreso(veterano, LocalDateTime.now().minusDays(11));
        guardarIngreso(veterano, LocalDateTime.now().minusDays(1));

        List<SocioAusenteDTO> ausentes = asistenciaService.sociosAusentes(10);

        SocioAusenteDTO fila = ausentes.stream()
                .filter(a -> a.getSocioId().equals(nuncaVino.getId())).findFirst().orElseThrow();
        assertThat(fila.getUltimaVisita()).isNull();
        assertThat(fila.getDiasSinVenir()).isEqualTo(11);
        assertThat(ausentes).extracting(SocioAusenteDTO::getSocioId)
                .doesNotContain(veterano.getId(), recienLlegado.getId());
    }

    @Test
    void elUmbralSoloAceptaLosValoresOfrecidos() {
        assertThat(asistenciaService.normalizarUmbral("15")).isEqualTo(15);
        assertThat(asistenciaService.normalizarUmbral("20")).isEqualTo(20);
        assertThat(asistenciaService.normalizarUmbral("7")).isEqualTo(10);
        assertThat(asistenciaService.normalizarUmbral("abc")).isEqualTo(10);
        assertThat(asistenciaService.normalizarUmbral(null)).isEqualTo(10);
    }

    // ------------------------------------------------------------------
    // Ficha de asistencia
    // ------------------------------------------------------------------

    @Test
    void laFichaResumeUltimaVisitaConteosYSemanas() {
        Socio socio = guardarSocioAlDia("Ficha");
        LocalDate hoy = LocalDate.now();
        LocalDate lunesDeEstaSemana = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lunesAnterior = lunesDeEstaSemana.minusWeeks(1);
        guardarIngreso(socio, lunesAnterior.atTime(19, 0));
        guardarIngreso(socio, lunesAnterior.plusDays(2).atTime(19, 0));

        ResumenAsistenciaSocioDTO resumen = asistenciaService.resumenDeSocio(socio);

        assertThat(resumen.getUltimaVisita()).isEqualTo(lunesAnterior.plusDays(2));
        assertThat(resumen.getDiasSinVenir())
                .isEqualTo(java.time.temporal.ChronoUnit.DAYS.between(lunesAnterior.plusDays(2), hoy));
        assertThat(resumen.getIngresosEsteMes() + resumen.getIngresosMesPasado()).isEqualTo(2);
        assertThat(resumen.getSemanas()).hasSize(4);
        assertThat(resumen.getSemanas().get(3).getInicio()).isEqualTo(lunesDeEstaSemana);

        ResumenAsistenciaSocioDTO.Semana semanaAnterior = resumen.getSemanas().get(2);
        assertThat(semanaAnterior.getInicio()).isEqualTo(lunesAnterior);
        assertThat(semanaAnterior.getDias())
                .containsExactly("vino", "falto", "vino", "falto", "falto", "falto", "falto");
        // Los dias que todavia no llegaron no se marcan como faltas.
        assertThat(resumen.getSemanas().get(3).getDias())
                .hasSize(7)
                .doesNotContain("vino")
                .filteredOn("futuro"::equals)
                .hasSize(7 - hoy.getDayOfWeek().getValue());
    }

    @Test
    void unSocioSinIngresosTieneLaFichaEnBlanco() {
        Socio socio = guardarSocioAlDia("Sin ingresos");

        ResumenAsistenciaSocioDTO resumen = asistenciaService.resumenDeSocio(socio);

        assertThat(resumen.getUltimaVisita()).isNull();
        assertThat(resumen.getDiasSinVenir()).isNull();
        assertThat(resumen.getIngresosEsteMes()).isZero();
        assertThat(resumen.getIngresosMesPasado()).isZero();
        assertThat(resumen.getSemanas()).hasSize(4);
        assertThat(resumen.getWhatsappUrl()).isNull();
    }

    // ------------------------------------------------------------------
    // Pantallas
    // ------------------------------------------------------------------

    @Test
    void asistenciaRequiereInicioDeSesion() throws Exception {
        mockMvc.perform(get("/asistencia"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/asistencia/historial"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/asistencia/registrar").with(csrf()).param("socioId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "recepcion-asistencia", roles = "RECEPCIONISTA")
    void recepcionRegistraUnIngresoYLoVeEnLaListaDelDia() throws Exception {
        Socio socio = guardarSocioAlDia("Pantalla");

        mockMvc.perform(get("/asistencia"))
                .andExpect(status().isOk())
                .andExpect(view().name("asistencia"))
                .andExpect(content().string(containsString("Registrar ingreso")))
                .andExpect(content().string(containsString("Ingresos de hoy")));

        MvcResult registro = mockMvc.perform(post("/asistencia/registrar")
                        .with(csrf())
                        .param("socioId", socio.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/asistencia"))
                .andReturn();
        IngresoRegistradoDTO ingreso = (IngresoRegistradoDTO) registro.getFlashMap().get("ingreso");
        assertThat(ingreso.getResultado()).isEqualTo(ResultadoIngreso.REGISTRADO);
        assertThat(ingresosDeHoy(socio)).extracting(Asistencia::getRegistradoPor)
                .containsExactly("recepcion-asistencia");

        mockMvc.perform(get("/asistencia").flashAttr("ingreso", ingreso))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ingreso registrado")))
                .andExpect(content().string(containsString("Pantalla Prueba")))
                .andExpect(content().string(containsString("Quitar")))
                .andExpect(content().string(not(containsString("Cobrar ahora"))));
    }

    @Test
    @WithMockUser(username = "recepcion-asistencia", roles = "RECEPCIONISTA")
    void laPantallaAvisaLaDeudaYOfreceElSegundoTurno() throws Exception {
        Socio moroso = guardarSocio("Deudor", LocalDate.now().minusDays(6));

        MvcResult primero = mockMvc.perform(post("/asistencia/registrar")
                        .with(csrf()).param("socioId", moroso.getId().toString()))
                .andReturn();
        mockMvc.perform(get("/asistencia").flashAttr("ingreso", primero.getFlashMap().get("ingreso")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cobrar ahora")))
                .andExpect(content().string(containsString("Vencida hace 6 días")))
                .andExpect(content().string(containsString("1 con cuota vencida")));

        MvcResult repetido = mockMvc.perform(post("/asistencia/registrar")
                        .with(csrf()).param("socioId", moroso.getId().toString()))
                .andReturn();
        mockMvc.perform(get("/asistencia").flashAttr("ingreso", repetido.getFlashMap().get("ingreso")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya ingresó hoy")))
                .andExpect(content().string(containsString("Registrar segundo turno")));
        assertThat(ingresosDeHoy(moroso)).hasSize(1);

        mockMvc.perform(post("/asistencia/registrar")
                        .with(csrf())
                        .param("socioId", moroso.getId().toString())
                        .param("forzar", "true"))
                .andExpect(redirectedUrl("/asistencia"));
        assertThat(ingresosDeHoy(moroso)).hasSize(2);
    }

    @Test
    @WithMockUser(username = "recepcion-asistencia", roles = "RECEPCIONISTA")
    void quitarDesdeLaPantallaBorraElIngreso() throws Exception {
        Socio socio = guardarSocioAlDia("Quitado");
        asistenciaService.registrarIngreso(socio.getId(), false, "recepcion-asistencia");
        Long ingresoId = ingresosDeHoy(socio).get(0).getId();

        MvcResult resultado = mockMvc.perform(post("/asistencia/{id}/quitar", ingresoId).with(csrf()))
                .andExpect(redirectedUrl("/asistencia"))
                .andReturn();

        assertThat(resultado.getFlashMap().get("mensaje")).isEqualTo("Se quitó el ingreso de Quitado Prueba.");
        assertThat(ingresosDeHoy(socio)).isEmpty();

        MvcResult inexistente = mockMvc.perform(post("/asistencia/{id}/quitar", ingresoId).with(csrf()))
                .andExpect(redirectedUrl("/asistencia"))
                .andReturn();
        assertThat(inexistente.getFlashMap().get("error")).isEqualTo("Ese ingreso ya no existe.");
    }

    @Test
    @WithMockUser(username = "recepcion-asistencia", roles = "RECEPCIONISTA")
    void registrarSinSocioOSinTokenNoRompeNiGuardaNada() throws Exception {
        MvcResult sinSocio = mockMvc.perform(post("/asistencia/registrar").with(csrf()))
                .andExpect(redirectedUrl("/asistencia"))
                .andReturn();
        assertThat(sinSocio.getFlashMap().get("error")).asString().contains("Elegí un socio");

        mockMvc.perform(post("/asistencia/registrar").param("socioId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "recepcion-asistencia", roles = "RECEPCIONISTA")
    void elBuscadorDevuelveSociosEnJson() throws Exception {
        Socio socio = guardarSocioAlDia("Buscable");

        mockMvc.perform(get("/asistencia/buscar").param("q", "Buscable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(socio.getId().intValue()))
                .andExpect(jsonPath("$[0].nombreCompleto").value("Buscable Prueba"));
        mockMvc.perform(get("/asistencia/buscar"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @WithMockUser(username = "recepcion-asistencia", roles = "RECEPCIONISTA")
    void elHistorialMuestraLaGrillaLosAusentesYLaFicha() throws Exception {
        Socio ausente = guardarSocioAlDia("Lejano");
        ausente.setTelefono("3624111222");
        socioRepository.save(ausente);
        guardarIngreso(ausente, LocalDate.now().minusDays(14).atTime(19, 0));

        mockMvc.perform(get("/asistencia/historial").param("socio", ausente.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("asistencia-historial"))
                .andExpect(content().string(containsString("Semana típica")))
                .andExpect(content().string(containsString("No están viniendo")))
                .andExpect(content().string(containsString("Lejano Prueba")))
                .andExpect(content().string(containsString("14 días")))
                .andExpect(content().string(containsString("Ficha de asistencia")))
                .andExpect(content().string(containsString("Escribirle por WhatsApp")))
                .andExpect(content().string(containsString("La franja más concurrida es")));
    }

    @Test
    @WithMockUser(username = "recepcion-asistencia", roles = "RECEPCIONISTA")
    void elHistorialToleraParametrosRotosEnLaUrl() throws Exception {
        mockMvc.perform(get("/asistencia/historial")
                        .param("periodo", "<script>")
                        .param("desde", "ayer")
                        .param("hasta", "32/13/2026")
                        .param("dias", "muchos")
                        .param("page", "-7")
                        .param("socio", "x1"))
                .andExpect(status().isOk())
                .andExpect(view().name("asistencia-historial"));

        mockMvc.perform(get("/asistencia/historial")
                        .param("periodo", "rango")
                        .param("desde", "")
                        .param("hasta", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Elegí las dos fechas del período.")));

        mockMvc.perform(get("/asistencia/historial")
                        .param("periodo", "rango")
                        .param("desde", LocalDate.now().minusDays(20).toString())
                        .param("hasta", LocalDate.now().minusDays(1).toString())
                        .param("page", "999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"desde\"")));
    }

    @Test
    @WithMockUser(username = "admin-asistencia", roles = "ADMIN")
    void laFichaDelSocioIncluyeSuAsistencia() throws Exception {
        Socio socio = guardarSocioAlDia("Fichado");
        guardarIngreso(socio, LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/socios/ver/{id}", socio.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("detalle-socio"))
                .andExpect(content().string(containsString("Ingresos registrados en el mostrador")))
                .andExpect(content().string(containsString("Última visita")))
                .andExpect(content().string(containsString("1 día")));
    }

    @Test
    @WithMockUser(username = "admin-asistencia", roles = "ADMIN")
    void sePuedeEliminarUnSocioSinPagosAunqueTengaIngresos() throws Exception {
        Socio socio = guardarSocioAlDia("Borrable");
        guardarIngreso(socio, LocalDateTime.now().minusDays(1));
        Long socioId = socio.getId();

        MvcResult resultado = mockMvc.perform(post("/socios/eliminar/{id}", socioId).with(csrf()))
                .andExpect(redirectedUrl("/socios"))
                .andReturn();

        assertThat(resultado.getFlashMap().get("mensaje")).asString().contains("Se eliminó");
        assertThat(socioRepository.findById(socioId)).isEmpty();
        assertThat(asistenciaRepository.countBySocio_IdAndFechaBetween(
                socioId, LocalDate.now().minusDays(5), LocalDate.now())).isZero();
    }

    // ------------------------------------------------------------------
    // Datos de prueba
    // ------------------------------------------------------------------

    /** Socio activo, con la proxima cuota a 12 dias de vencer. */
    private Socio guardarSocioAlDia(String nombre) {
        return guardarSocio(nombre, LocalDate.now().plusDays(12));
    }

    private Socio guardarSocio(String nombre, LocalDate vencimientoCuota) {
        Socio socio = new Socio();
        socio.setNombre(nombre);
        socio.setApellido("Prueba");
        socio.setDni(Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits()).substring(0, 8));
        socio.setEstado(EstadoSocio.ACTIVO);
        socio.setFechaVencimientoPlan(vencimientoCuota);

        Cuota cuota = new Cuota();
        cuota.setSocio(socio);
        cuota.setFechaVencimiento(vencimientoCuota);
        cuota.setMonto(new BigDecimal("38000"));
        cuota.setEstado(EstadoCuota.PENDIENTE);
        socio.getCuotas().add(cuota);
        return socioRepository.save(socio);
    }

    private List<Asistencia> ingresosDeHoy(Socio socio) {
        return asistenciaRepository.findBySocio_IdAndFechaOrderByNumeroTurnoAsc(socio.getId(), LocalDate.now());
    }

    private Asistencia guardarIngreso(Socio socio, LocalDateTime momento) {
        int turno = turnos.merge(socio.getId() + "@" + momento.toLocalDate(), 1, Integer::sum);
        return asistenciaRepository.saveAndFlush(nuevoIngreso(socio, momento, turno));
    }

    private void cargarIngresos(Socio socio, LocalDateTime momento, int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            guardarIngreso(socio, momento.plusSeconds(i));
        }
    }

    private Asistencia nuevoIngreso(Socio socio, LocalDateTime momento, int turno) {
        Asistencia asistencia = new Asistencia();
        asistencia.setSocio(socio);
        asistencia.setFecha(momento.toLocalDate());
        asistencia.setFechaHora(momento);
        asistencia.setNumeroTurno(turno);
        asistencia.setRegistradoPor("prueba");
        return asistencia;
    }
}
