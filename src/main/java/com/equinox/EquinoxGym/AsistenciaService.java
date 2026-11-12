package com.equinox.EquinoxGym;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
@Transactional
public class AsistenciaService {

    /** Un ingreso normal y, si el mostrador lo fuerza, un segundo turno. */
    static final int MAXIMO_INGRESOS_POR_DIA = 2;
    static final List<Integer> UMBRALES_AUSENCIA = List.of(10, 15, 20);

    static final String AVISO_UNA_SEMANA = "UNA_SEMANA";
    static final String AVISO_VENTANA_LARGA = "VENTANA_LARGA";

    private static final int MAXIMO_DIAS_RANGO = 366;
    private static final int DIAS_VENTANA_LARGA = 56;
    private static final int TENDENCIA_MINIMA = 15;
    private static final int SEMANAS_EN_FICHA = 4;
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> DIAS_CORTOS = List.of("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom");

    private final AsistenciaRepository asistenciaRepository;
    private final SocioRepository socioRepository;
    private final SocioService socioService;
    private final int cupoReferencia;
    private final int minutosAnticipacion;
    private final String gymName;

    public AsistenciaService(AsistenciaRepository asistenciaRepository,
                             SocioRepository socioRepository,
                             SocioService socioService,
                             @Value("${equinox.asistencia.cupo-referencia:0}") String cupoReferencia,
                             @Value("${equinox.asistencia.minutos-anticipacion:15}") String minutosAnticipacion,
                             @Value("${equinox.branding.gym-name:Keep Fit Gym}") String gymName) {
        this.asistenciaRepository = asistenciaRepository;
        this.socioRepository = socioRepository;
        this.socioService = socioService;
        // Se leen como texto: un valor mal tipeado en equinox.env no tiene
        // que impedir que el sistema arranque, alcanza con ignorarlo.
        this.cupoReferencia = Math.max(0, enteroSeguro(cupoReferencia, 0));
        this.minutosAnticipacion = Math.min(59, Math.max(0, enteroSeguro(minutosAnticipacion, 15)));
        this.gymName = gymName;
    }

    // ------------------------------------------------------------------
    // Registro de ingresos
    // ------------------------------------------------------------------

    /**
     * Registra el ingreso de un socio. Nunca bloquea por deuda: si el socio
     * esta moroso o inactivo el ingreso se guarda igual y el resultado lo
     * informa para que el mostrador pueda cobrarle.
     *
     * Si el socio ya ingreso hoy no se guarda nada salvo que venga
     * {@code forzarSegundoTurno}; el resultado avisa cual de los dos casos fue.
     */
    public IngresoRegistradoDTO registrarIngreso(Long socioId, boolean forzarSegundoTurno, String usuario) {
        if (socioId == null) {
            throw new IllegalArgumentException("Elegí un socio de la lista para registrar el ingreso.");
        }
        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new IllegalArgumentException("El socio seleccionado ya no existe."));
        // El estado se recalcula antes de mostrarlo: es justo el dato que el
        // mostrador necesita ver bien en este momento.
        socioService.actualizarEstadoSocio(socio);

        LocalDateTime ahora = LocalDateTime.now();
        LocalDate hoy = ahora.toLocalDate();
        List<Asistencia> ingresosDeHoy = asistenciaRepository
                .findBySocio_IdAndFechaOrderByNumeroTurnoAsc(socioId, hoy);

        IngresoRegistradoDTO respuesta = describir(socio);
        int turno = 1;
        if (!ingresosDeHoy.isEmpty()) {
            Asistencia ultimo = ingresosDeHoy.get(ingresosDeHoy.size() - 1);
            respuesta.setHoraIngresoAnterior(FORMATO_HORA.format(ultimo.getFechaHora()));
            if (ingresosDeHoy.size() >= MAXIMO_INGRESOS_POR_DIA) {
                respuesta.setResultado(ResultadoIngreso.LIMITE_DIARIO);
                return respuesta;
            }
            if (!forzarSegundoTurno) {
                respuesta.setResultado(ResultadoIngreso.YA_REGISTRADO);
                return respuesta;
            }
            turno = ultimo.getNumeroTurno() + 1;
        }

        Asistencia asistencia = new Asistencia();
        asistencia.setSocio(socio);
        asistencia.setFecha(hoy);
        asistencia.setFechaHora(ahora);
        asistencia.setNumeroTurno(turno);
        asistencia.setRegistradoPor(normalizarUsuario(usuario));
        // Con flush inmediato, si otra peticion gano la carrera la base lo
        // rechaza aca mismo y no al cerrar la transaccion.
        asistenciaRepository.saveAndFlush(asistencia);

        respuesta.setResultado(ResultadoIngreso.REGISTRADO);
        respuesta.setHora(FORMATO_HORA.format(ahora));
        respuesta.setSegundoTurno(asistencia.isSegundoTurno());
        return respuesta;
    }

    /**
     * Quita un ingreso cargado por error. Solo vale para los de hoy: el
     * historial de dias anteriores no se toca.
     *
     * @return el nombre del socio, para confirmar en pantalla
     */
    public String quitarIngreso(Long asistenciaId) {
        Asistencia asistencia = asistenciaId == null ? null
                : asistenciaRepository.findById(asistenciaId).orElse(null);
        if (asistencia == null) {
            throw new IllegalArgumentException("Ese ingreso ya no existe.");
        }
        LocalDate fecha = asistencia.getFecha();
        if (!LocalDate.now().equals(fecha)) {
            throw new IllegalStateException("Solo se pueden quitar ingresos del día de hoy.");
        }

        Long socioId = asistencia.getSocio().getId();
        String nombre = nombreDe(asistencia.getSocio());
        asistenciaRepository.delete(asistencia);
        asistenciaRepository.flush();

        // Si quedo un segundo turno sin su primero, pasa a ser el primero.
        int turno = 1;
        for (Asistencia restante : asistenciaRepository
                .findBySocio_IdAndFechaOrderByNumeroTurnoAsc(socioId, fecha)) {
            if (restante.getNumeroTurno() != turno) {
                restante.setNumeroTurno(turno);
            }
            turno++;
        }
        return nombre;
    }

    // ------------------------------------------------------------------
    // Solapa "Hoy"
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AsistenciaHoyDTO resumenDeHoy() {
        List<Asistencia> registros = asistenciaRepository
                .findByFechaOrderByFechaHoraDescIdDesc(LocalDate.now());

        List<IngresoDelDiaDTO> ingresos = new ArrayList<>();
        Set<Long> sociosConDeuda = new HashSet<>();
        int[] porFranja = new int[24];
        for (Asistencia registro : registros) {
            Socio socio = registro.getSocio();
            int franja = franjaDe(registro.getFechaHora());
            porFranja[franja]++;

            IngresoDelDiaDTO fila = new IngresoDelDiaDTO(
                    registro.getId(),
                    FORMATO_HORA.format(registro.getFechaHora()),
                    etiquetaFranja(franja),
                    socio.getId(),
                    nombreDe(socio),
                    socio.getDni(),
                    estadoDe(socio),
                    registro.isSegundoTurno(),
                    registro.getRegistradoPor());
            if (fila.isConDeuda()) {
                sociosConDeuda.add(socio.getId());
            }
            ingresos.add(fila);
        }

        int maximo = 0;
        for (int cantidad : porFranja) {
            maximo = Math.max(maximo, cantidad);
        }
        List<FranjaHorariaDTO> franjas = new ArrayList<>();
        for (int hora = 0; hora < porFranja.length; hora++) {
            int cantidad = porFranja[hora];
            if (cantidad > 0) {
                franjas.add(new FranjaHorariaDTO(etiquetaFranja(hora), cantidad,
                        Math.round(cantidad * 100f / maximo), cantidad == maximo));
            }
        }
        return new AsistenciaHoyDTO(ingresos, franjas, sociosConDeuda.size());
    }

    // ------------------------------------------------------------------
    // Solapa "Historial": semana tipica
    // ------------------------------------------------------------------

    /**
     * Traduce lo que llega por la URL a un periodo valido. Los periodos
     * siempre terminan ayer: el dia de hoy todavia no termino y contarlo
     * bajaria el promedio de las franjas de la tarde.
     */
    public PeriodoAsistencia resolverPeriodo(String tipo, String desdeTexto, String hastaTexto) {
        LocalDate ayer = LocalDate.now().minusDays(1);
        if (!PeriodoAsistencia.RANGO.equals(tipo)) {
            return periodoEnSemanas(tipo, ayer, null);
        }

        LocalDate desde = fechaSegura(desdeTexto);
        LocalDate hasta = fechaSegura(hastaTexto);
        if (desde == null || hasta == null) {
            return periodoEnSemanas("4", ayer, "Elegí las dos fechas del período.");
        }
        if (desde.isAfter(hasta)) {
            LocalDate intercambio = desde;
            desde = hasta;
            hasta = intercambio;
        }
        if (desde.isAfter(ayer)) {
            return periodoEnSemanas("4", ayer, "El período tiene que empezar antes de hoy.");
        }
        if (hasta.isAfter(ayer)) {
            hasta = ayer;
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS_RANGO) {
            return periodoEnSemanas("4", ayer, "El período no puede superar un año.");
        }
        return new PeriodoAsistencia(PeriodoAsistencia.RANGO, desde, hasta, null);
    }

    @Transactional(readOnly = true)
    public SemanaTipicaDTO semanaTipica(PeriodoAsistencia periodo) {
        List<LocalDateTime> horarios = asistenciaRepository
                .horariosEntre(periodo.getDesde(), periodo.getHasta());

        int[][] conteo = new int[7][24];
        List<Set<LocalDate>> diasAbiertos = new ArrayList<>();
        for (int dia = 0; dia < 7; dia++) {
            diasAbiertos.add(new HashSet<>());
        }
        Map<LocalDate, Integer> ingresosPorFecha = new TreeMap<>();
        for (LocalDateTime horario : horarios) {
            LocalDate fecha = horario.toLocalDate();
            int dia = fecha.getDayOfWeek().getValue() - 1;
            conteo[dia][franjaDe(horario)]++;
            diasAbiertos.get(dia).add(fecha);
            ingresosPorFecha.merge(fecha, 1, Integer::sum);
        }

        // El domingo solo ocupa una columna si alguna vez hubo movimiento.
        int columnas = diasAbiertos.get(DayOfWeek.SUNDAY.getValue() - 1).isEmpty() ? 6 : 7;

        double[][] promedios = new double[7][24];
        double maximo = 0;
        for (int dia = 0; dia < columnas; dia++) {
            int abiertos = diasAbiertos.get(dia).size();
            for (int hora = 0; hora < 24; hora++) {
                promedios[dia][hora] = abiertos == 0 ? 0 : (double) conteo[dia][hora] / abiertos;
                maximo = Math.max(maximo, promedios[dia][hora]);
            }
        }

        List<SemanaTipicaDTO.Fila> filas = new ArrayList<>();
        List<Pico> franjasOrdenadas = new ArrayList<>();
        for (int hora = 0; hora < 24; hora++) {
            boolean hayDatos = false;
            for (int dia = 0; dia < columnas; dia++) {
                hayDatos |= conteo[dia][hora] > 0;
            }
            if (!hayDatos) {
                continue;
            }
            List<SemanaTipicaDTO.Celda> celdas = new ArrayList<>();
            for (int dia = 0; dia < columnas; dia++) {
                double promedio = promedios[dia][hora];
                long redondeado = Math.round(promedio);
                boolean superaCupo = cupoReferencia > 0 && redondeado > cupoReferencia;
                celdas.add(new SemanaTipicaDTO.Celda(
                        textoPromedio(promedio), nivelDe(promedio, maximo), superaCupo));
                if (promedio > 0) {
                    franjasOrdenadas.add(new Pico(DIAS_CORTOS.get(dia), etiquetaFranja(hora), promedio));
                }
            }
            filas.add(new SemanaTipicaDTO.Fila(etiquetaFranja(hora), celdas));
        }

        franjasOrdenadas.sort(Comparator.comparingDouble((Pico pico) -> pico.promedio).reversed());
        List<String> picos = new ArrayList<>();
        int cantidadPicos = 0;
        for (Pico pico : franjasOrdenadas) {
            if (cupoReferencia > 0 && Math.round(pico.promedio) > cupoReferencia) {
                cantidadPicos++;
                if (picos.size() < 3) {
                    picos.add(pico.dia + " " + pico.hora + " (" + Math.round(pico.promedio) + ")");
                }
            }
        }
        Pico masConcurrida = franjasOrdenadas.isEmpty() ? null : franjasOrdenadas.get(0);

        return new SemanaTipicaDTO(
                DIAS_CORTOS.subList(0, columnas),
                filas,
                ingresosPorFecha.size(),
                horarios.size(),
                cupoReferencia,
                picos,
                cantidadPicos,
                masConcurrida == null ? null : masConcurrida.dia + " " + masConcurrida.hora,
                masConcurrida == null ? 0 : Math.max(1, Math.round(masConcurrida.promedio)),
                avisoPara(periodo),
                tendencia(periodo, ingresosPorFecha));
    }

    // ------------------------------------------------------------------
    // Solapa "Historial": socios que dejaron de venir
    // ------------------------------------------------------------------

    public int normalizarUmbral(String dias) {
        int umbral = enteroSeguro(dias, UMBRALES_AUSENCIA.get(0));
        return UMBRALES_AUSENCIA.contains(umbral) ? umbral : UMBRALES_AUSENCIA.get(0);
    }

    @Transactional(readOnly = true)
    public boolean hayIngresosRegistrados() {
        return asistenciaRepository.primeraFechaRegistrada() != null;
    }

    /**
     * Socios con la cuota al dia que llevan {@code umbralDias} o mas sin
     * registrar un ingreso. A los que nunca vinieron se les cuenta desde su
     * alta o desde el primer dia en que el gimnasio empezo a cargar ingresos,
     * lo que sea mas reciente: antes de eso no habia como saber si venian.
     */
    @Transactional(readOnly = true)
    public List<SocioAusenteDTO> sociosAusentes(int umbralDias) {
        LocalDate primerRegistro = asistenciaRepository.primeraFechaRegistrada();
        if (primerRegistro == null) {
            return List.of();
        }

        Map<Long, LocalDate> ultimaPorSocio = new HashMap<>();
        for (Object[] fila : asistenciaRepository.ultimaFechaPorSocio()) {
            ultimaPorSocio.put((Long) fila[0], (LocalDate) fila[1]);
        }

        LocalDate hoy = LocalDate.now();
        List<SocioAusenteDTO> ausentes = new ArrayList<>();
        for (Socio socio : socioRepository.findByEstadoAndBajaFalse(EstadoSocio.ACTIVO)) {
            if (!socio.tieneCuotas()) {
                continue; // alta incompleta: todavia no esta pagando nada
            }
            LocalDate ultimaVisita = ultimaPorSocio.get(socio.getId());
            LocalDate referencia = ultimaVisita != null ? ultimaVisita
                    : (socio.getFechaAlta() != null && socio.getFechaAlta().isAfter(primerRegistro)
                            ? socio.getFechaAlta() : primerRegistro);
            long diasSinVenir = ChronoUnit.DAYS.between(referencia, hoy);
            if (diasSinVenir >= umbralDias) {
                ausentes.add(new SocioAusenteDTO(socio.getId(), nombreDe(socio), socio.getDni(),
                        ultimaVisita, diasSinVenir, socio.getFechaVencimientoPlan()));
            }
        }
        ausentes.sort(Comparator.comparingLong(SocioAusenteDTO::getDiasSinVenir).reversed()
                .thenComparing(SocioAusenteDTO::getNombreCompleto, String.CASE_INSENSITIVE_ORDER));
        return ausentes;
    }

    // ------------------------------------------------------------------
    // Ficha de asistencia de un socio
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ResumenAsistenciaSocioDTO resumenDeSocio(Socio socio) {
        LocalDate hoy = LocalDate.now();
        Long socioId = socio.getId();

        LocalDate ultimaVisita = asistenciaRepository.ultimaFechaDeSocio(socioId);
        Long diasSinVenir = ultimaVisita == null ? null : ChronoUnit.DAYS.between(ultimaVisita, hoy);

        LocalDate inicioMes = hoy.withDayOfMonth(1);
        long esteMes = asistenciaRepository.countBySocio_IdAndFechaBetween(socioId, inicioMes, hoy);
        long mesPasado = asistenciaRepository.countBySocio_IdAndFechaBetween(
                socioId, inicioMes.minusMonths(1), inicioMes.minusDays(1));

        LocalDate primerLunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(SEMANAS_EN_FICHA - 1L);
        Set<LocalDate> fechasConIngreso = new HashSet<>(
                asistenciaRepository.fechasDeSocioDesde(socioId, primerLunes));
        List<ResumenAsistenciaSocioDTO.Semana> semanas = new ArrayList<>();
        for (int semana = 0; semana < SEMANAS_EN_FICHA; semana++) {
            LocalDate lunes = primerLunes.plusWeeks(semana);
            List<String> dias = new ArrayList<>();
            for (int dia = 0; dia < 7; dia++) {
                LocalDate fecha = lunes.plusDays(dia);
                dias.add(fecha.isAfter(hoy) ? "futuro"
                        : (fechasConIngreso.contains(fecha) ? "vino" : "falto"));
            }
            semanas.add(new ResumenAsistenciaSocioDTO.Semana(lunes, dias));
        }

        return new ResumenAsistenciaSocioDTO(socioId, nombreDe(socio), socio.getDni(), socio.getTelefono(),
                ultimaVisita, diasSinVenir, esteMes, mesPasado, semanas,
                WhatsAppLinkBuilder.construirUrl(socio.getTelefono(), mensajeDeReencuentro(socio)));
    }

    public int getMinutosAnticipacion() {
        return minutosAnticipacion;
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /**
     * Franja horaria a la que se imputa un ingreso. Quien llega unos minutos
     * antes de la hora en punto viene a la clase que arranca a esa hora, no
     * a la anterior: por eso las llegadas dentro de la anticipacion
     * configurada cuentan para la franja siguiente.
     */
    int franjaDe(LocalDateTime momento) {
        LocalDateTime ajustado = momento.plusMinutes(minutosAnticipacion);
        return ajustado.toLocalDate().equals(momento.toLocalDate())
                ? ajustado.getHour() : momento.getHour();
    }

    private IngresoRegistradoDTO describir(Socio socio) {
        IngresoRegistradoDTO respuesta = new IngresoRegistradoDTO();
        respuesta.setSocioId(socio.getId());
        respuesta.setNombreCompleto(nombreDe(socio));
        respuesta.setDni(socio.getDni());
        respuesta.setEstado(estadoDe(socio));
        respuesta.setTextoCuota(textoCuota(socio));
        respuesta.setMaximoPorDia(MAXIMO_INGRESOS_POR_DIA);
        return respuesta;
    }

    private String textoCuota(Socio socio) {
        if (socio.isBaja()) {
            return socio.getFechaBaja() == null ? "Dado de baja"
                    : "Dado de baja el " + FORMATO_FECHA.format(socio.getFechaBaja());
        }
        Cuota pendiente = socio.getCuotas().stream()
                .filter(cuota -> cuota.getFechaPago() == null && cuota.getFechaVencimiento() != null)
                .min(Comparator.comparing(Cuota::getFechaVencimiento))
                .orElse(null);
        if (pendiente != null) {
            return pendiente.getTextoVencimiento();
        }
        if (socio.getPlan() == null) {
            return "Sin plan asignado";
        }
        if (!socio.tieneCuotas()) {
            return "Sin cuota inicial generada";
        }
        return "Al día";
    }

    private String mensajeDeReencuentro(Socio socio) {
        String marca = (gymName == null || gymName.isBlank()) ? "el gimnasio" : gymName;
        String nombre = (socio.getNombre() == null || socio.getNombre().isBlank())
                ? "" : (" " + socio.getNombre().trim());
        return "Hola" + nombre + ", hace unos días que no te vemos por " + marca
                + ". ¿Está todo bien? Cuando quieras retomar, te esperamos.";
    }

    private PeriodoAsistencia periodoEnSemanas(String tipo, LocalDate ayer, String error) {
        int semanas = "1".equals(tipo) ? 1 : ("8".equals(tipo) ? 8 : 4);
        return new PeriodoAsistencia(String.valueOf(semanas),
                ayer.minusDays(semanas * 7L - 1), ayer, error);
    }

    private String avisoPara(PeriodoAsistencia periodo) {
        long dias = periodo.getCantidadDias();
        if (dias <= 7) {
            return AVISO_UNA_SEMANA;
        }
        return dias >= DIAS_VENTANA_LARGA ? AVISO_VENTANA_LARGA : null;
    }

    /**
     * En ventanas largas compara el promedio diario de la primera mitad del
     * periodo con el de la segunda. Devuelve la variacion en porcentaje, o
     * null si no hay datos en las dos mitades o el cambio no es relevante.
     */
    private Integer tendencia(PeriodoAsistencia periodo, Map<LocalDate, Integer> ingresosPorFecha) {
        if (periodo.getCantidadDias() < DIAS_VENTANA_LARGA) {
            return null;
        }
        LocalDate mitad = periodo.getDesde().plusDays(periodo.getCantidadDias() / 2);
        long ingresosAntes = 0;
        long ingresosDespues = 0;
        int diasAntes = 0;
        int diasDespues = 0;
        for (Map.Entry<LocalDate, Integer> dia : ingresosPorFecha.entrySet()) {
            if (dia.getKey().isBefore(mitad)) {
                ingresosAntes += dia.getValue();
                diasAntes++;
            } else {
                ingresosDespues += dia.getValue();
                diasDespues++;
            }
        }
        if (diasAntes == 0 || diasDespues == 0) {
            return null;
        }
        double promedioAntes = (double) ingresosAntes / diasAntes;
        double promedioDespues = (double) ingresosDespues / diasDespues;
        int variacion = (int) Math.round((promedioDespues / promedioAntes - 1) * 100);
        return Math.abs(variacion) >= TENDENCIA_MINIMA ? variacion : null;
    }

    private static String textoPromedio(double promedio) {
        if (promedio <= 0) {
            return "·";
        }
        return promedio < 0.5 ? "<1" : String.valueOf(Math.round(promedio));
    }

    private static int nivelDe(double promedio, double maximo) {
        if (promedio <= 0 || maximo <= 0) {
            return 0;
        }
        double proporcion = promedio / maximo;
        if (proporcion > 0.88) {
            return 4;
        }
        if (proporcion > 0.62) {
            return 3;
        }
        return proporcion > 0.35 ? 2 : 1;
    }

    private static String etiquetaFranja(int hora) {
        return String.format("%02d:00", hora);
    }

    private static String nombreDe(Socio socio) {
        return socio.getNombreCompleto().trim();
    }

    private static String estadoDe(Socio socio) {
        return socio.getEstado() == null ? "-" : socio.getEstado().name();
    }

    private static String normalizarUsuario(String usuario) {
        return usuario == null || usuario.isBlank() ? "sistema" : usuario.trim();
    }

    private static LocalDate fechaSegura(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static int enteroSeguro(String texto, int porDefecto) {
        if (texto == null || texto.isBlank()) {
            return porDefecto;
        }
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    /** Franja de la grilla con su promedio, para ordenar las mas concurridas. */
    private static final class Pico {
        private final String dia;
        private final String hora;
        private final double promedio;

        private Pico(String dia, String hora, double promedio) {
            this.dia = dia;
            this.hora = hora;
            this.promedio = promedio;
        }
    }
}
