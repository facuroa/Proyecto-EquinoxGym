package com.equinox.EquinoxGym;

import java.util.List;

/**
 * Grilla "semana tipica": promedio de ingresos por dia de la semana y franja
 * horaria dentro de un periodo. Cada celda se promedia sobre los dias de esa
 * columna que tuvieron al menos un ingreso cargado, asi un feriado o un dia
 * en que no se uso el sistema no tiran el promedio para abajo.
 */
public class SemanaTipicaDTO {

    private final List<String> dias;
    private final List<Fila> filas;
    private final int diasConRegistros;
    private final long totalIngresos;
    private final int cupoReferencia;
    private final List<String> picos;
    private final int cantidadPicos;
    private final String franjaMasConcurrida;
    private final long promedioMasConcurrida;
    private final String aviso;
    private final Integer tendenciaPorcentaje;

    public SemanaTipicaDTO(List<String> dias, List<Fila> filas, int diasConRegistros, long totalIngresos,
                           int cupoReferencia, List<String> picos, int cantidadPicos,
                           String franjaMasConcurrida, long promedioMasConcurrida,
                           String aviso, Integer tendenciaPorcentaje) {
        this.dias = dias;
        this.filas = filas;
        this.diasConRegistros = diasConRegistros;
        this.totalIngresos = totalIngresos;
        this.cupoReferencia = cupoReferencia;
        this.picos = picos;
        this.cantidadPicos = cantidadPicos;
        this.franjaMasConcurrida = franjaMasConcurrida;
        this.promedioMasConcurrida = promedioMasConcurrida;
        this.aviso = aviso;
        this.tendenciaPorcentaje = tendenciaPorcentaje;
    }

    public List<String> getDias() { return dias; }
    public List<Fila> getFilas() { return filas; }
    public int getDiasConRegistros() { return diasConRegistros; }
    public long getTotalIngresos() { return totalIngresos; }
    public int getCupoReferencia() { return cupoReferencia; }
    public List<String> getPicos() { return picos; }
    public int getCantidadPicos() { return cantidadPicos; }
    public String getFranjaMasConcurrida() { return franjaMasConcurrida; }
    public long getPromedioMasConcurrida() { return promedioMasConcurrida; }
    public String getAviso() { return aviso; }
    public Integer getTendenciaPorcentaje() { return tendenciaPorcentaje; }

    public boolean isVacia() {
        return filas.isEmpty();
    }

    /** Una franja horaria con una celda por cada dia de la semana. */
    public static class Fila {

        private final String hora;
        private final List<Celda> celdas;

        public Fila(String hora, List<Celda> celdas) {
            this.hora = hora;
            this.celdas = celdas;
        }

        public String getHora() { return hora; }
        public List<Celda> getCeldas() { return celdas; }
    }

    /** Promedio de una franja en un dia de la semana. */
    public static class Celda {

        private final String texto;
        private final int nivel;
        private final boolean superaCupo;

        public Celda(String texto, int nivel, boolean superaCupo) {
            this.texto = texto;
            this.nivel = nivel;
            this.superaCupo = superaCupo;
        }

        public String getTexto() { return texto; }
        /** Intensidad de 0 (sin gente) a 4 (lo mas concurrido de la grilla). */
        public int getNivel() { return nivel; }
        public boolean isSuperaCupo() { return superaCupo; }
    }
}
