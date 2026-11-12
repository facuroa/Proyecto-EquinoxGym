package com.equinox.EquinoxGym;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un ingreso de un socio al gimnasio.
 *
 * La fecha se guarda aparte de la fecha y hora para poder consultar "los
 * ingresos de un dia" con un indice simple. El numero de turno arranca en 1
 * y sube cuando el mostrador fuerza un segundo ingreso el mismo dia: la
 * restriccion unica (socio, fecha, turno) hace que un doble clic no pueda
 * dejar dos filas iguales aunque las dos peticiones lleguen a la vez.
 */
@Entity
@Table(name = "asistencias",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_asistencias_socio_fecha_turno",
                columnNames = {"socio_id", "fecha", "numero_turno"}),
        indexes = @Index(name = "idx_asistencias_fecha", columnList = "fecha"))
public class Asistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "socio_id", nullable = false)
    private Socio socio;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "numero_turno", nullable = false)
    private int numeroTurno = 1;

    @Column(name = "registrado_por", nullable = false, length = 100)
    private String registradoPor;

    public Long getId() { return id; }

    public Socio getSocio() { return socio; }
    public void setSocio(Socio socio) { this.socio = socio; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public int getNumeroTurno() { return numeroTurno; }
    public void setNumeroTurno(int numeroTurno) { this.numeroTurno = numeroTurno; }

    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }

    public boolean isSegundoTurno() {
        return numeroTurno > 1;
    }
}
