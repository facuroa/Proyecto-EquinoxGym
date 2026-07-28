package com.equinox.EquinoxGym;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cajas")
public class CajaSesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String usuarioApertura;

    @Column(nullable = false)
    private LocalDateTime fechaApertura;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montoInicial;

    @Column(length = 500)
    private String observacionesApertura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCaja estado;

    private LocalDateTime fechaCierre;

    @Column(length = 100)
    private String usuarioCierre;

    @Column(precision = 15, scale = 2)
    private BigDecimal montoEsperadoCierre;

    @Column(precision = 15, scale = 2)
    private BigDecimal montoDeclaradoCierre;

    @Column(precision = 15, scale = 2)
    private BigDecimal diferenciaCierre;

    @Column(length = 500)
    private String observacionesCierre;

    @Version
    private Long version;

    public Long getId() { return id; }
    public String getUsuarioApertura() { return usuarioApertura; }
    public void setUsuarioApertura(String usuarioApertura) { this.usuarioApertura = usuarioApertura; }
    public LocalDateTime getFechaApertura() { return fechaApertura; }
    public void setFechaApertura(LocalDateTime fechaApertura) { this.fechaApertura = fechaApertura; }
    public BigDecimal getMontoInicial() { return montoInicial; }
    public void setMontoInicial(BigDecimal montoInicial) { this.montoInicial = montoInicial; }
    public String getObservacionesApertura() { return observacionesApertura; }
    public void setObservacionesApertura(String observacionesApertura) { this.observacionesApertura = observacionesApertura; }
    public EstadoCaja getEstado() { return estado; }
    public void setEstado(EstadoCaja estado) { this.estado = estado; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }
    public String getUsuarioCierre() { return usuarioCierre; }
    public void setUsuarioCierre(String usuarioCierre) { this.usuarioCierre = usuarioCierre; }
    public BigDecimal getMontoEsperadoCierre() { return montoEsperadoCierre; }
    public void setMontoEsperadoCierre(BigDecimal montoEsperadoCierre) { this.montoEsperadoCierre = montoEsperadoCierre; }
    public BigDecimal getMontoDeclaradoCierre() { return montoDeclaradoCierre; }
    public void setMontoDeclaradoCierre(BigDecimal montoDeclaradoCierre) { this.montoDeclaradoCierre = montoDeclaradoCierre; }
    public BigDecimal getDiferenciaCierre() { return diferenciaCierre; }
    public void setDiferenciaCierre(BigDecimal diferenciaCierre) { this.diferenciaCierre = diferenciaCierre; }
    public String getObservacionesCierre() { return observacionesCierre; }
    public void setObservacionesCierre(String observacionesCierre) { this.observacionesCierre = observacionesCierre; }
    public Long getVersion() { return version; }
}
