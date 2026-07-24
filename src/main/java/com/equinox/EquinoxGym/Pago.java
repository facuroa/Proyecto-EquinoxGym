package com.equinox.EquinoxGym;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "pagos")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cuota_id", nullable = false)
    private Cuota cuota;

    @OneToOne
    @JoinColumn(name = "cuota_renovacion_generada_id")
    private Cuota cuotaRenovacionGenerada;

    @Transient
    private Long cuotaId;

    @NotNull(message = "La fecha de pago es obligatoria")
    private LocalDate fechaPago;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    @NotNull(message = "El medio de pago es obligatorio")
    private String medioPago;

    private LocalDateTime fechaRegistro;
    private String registradoPor;
    private boolean anulado;
    private LocalDateTime fechaAnulacion;
    private String anuladoPor;

    @Column(length = 500)
    private String motivoAnulacion;

    public Pago() {
    }

    public Long getId() {
        return id;
    }

    public Cuota getCuota() {
        return cuota;
    }

    public void setCuota(Cuota cuota) {
        this.cuota = cuota;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCuotaId() {
        if (cuota != null) {
            return cuota.getId();
        }
        return cuotaId;
    }

    public void setCuotaId(Long cuotaId) {
        this.cuotaId = cuotaId;
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

    public String getMedioPago() {
        return medioPago;
    }

    public void setMedioPago(String medioPago) {
        this.medioPago = medioPago;
    }

    public Cuota getCuotaRenovacionGenerada() { return cuotaRenovacionGenerada; }
    public void setCuotaRenovacionGenerada(Cuota cuotaRenovacionGenerada) { this.cuotaRenovacionGenerada = cuotaRenovacionGenerada; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }
    public boolean isAnulado() { return anulado; }
    public void setAnulado(boolean anulado) { this.anulado = anulado; }
    public LocalDateTime getFechaAnulacion() { return fechaAnulacion; }
    public void setFechaAnulacion(LocalDateTime fechaAnulacion) { this.fechaAnulacion = fechaAnulacion; }
    public String getAnuladoPor() { return anuladoPor; }
    public void setAnuladoPor(String anuladoPor) { this.anuladoPor = anuladoPor; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public void setMotivoAnulacion(String motivoAnulacion) { this.motivoAnulacion = motivoAnulacion; }

    public String getNumeroComprobante() {
        return id == null ? "EQX-PENDIENTE" : String.format("EQX-%08d", id);
    }
}
