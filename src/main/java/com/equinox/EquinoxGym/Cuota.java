package com.equinox.EquinoxGym;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class Cuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate fechaVencimiento;
    private LocalDate fechaPago;

    @Column(nullable = false)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    private EstadoCuota estado;

    @ManyToOne
    @JoinColumn(name = "socio_id")
    private Socio socio;

    @Transient
    private Long socioId;

    public Cuota() {
    }

    public Long getId() {
        return id;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(LocalDate fechaPago) {
        this.fechaPago = fechaPago;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public EstadoCuota getEstado() {
        return estado;
    }

    public void setEstado(EstadoCuota estado) {
        this.estado = estado;
    }

    public Socio getSocio() {
        return socio;
    }

    public void setSocio(Socio socio) {
        this.socio = socio;
    }

    public Long getSocioId() {
        if (socio != null) {
            return socio.getId();
        }
        return socioId;
    }

    public void setSocioId(Long socioId) {
        this.socioId = socioId;
    }

    public boolean estaPagada() {
        return fechaPago != null;
    }
}