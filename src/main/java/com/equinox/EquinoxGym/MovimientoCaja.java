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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimientos_caja")
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "caja_id", nullable = false)
    private CajaSesion caja;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimientoCaja tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrigenMovimientoCaja origen;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 250)
    private String concepto;

    @Column(nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(nullable = false, length = 100)
    private String registradoPor;

    @ManyToOne
    @JoinColumn(name = "pago_id")
    private Pago pago;

    public Long getId() { return id; }
    public CajaSesion getCaja() { return caja; }
    public void setCaja(CajaSesion caja) { this.caja = caja; }
    public TipoMovimientoCaja getTipo() { return tipo; }
    public void setTipo(TipoMovimientoCaja tipo) { this.tipo = tipo; }
    public OrigenMovimientoCaja getOrigen() { return origen; }
    public void setOrigen(OrigenMovimientoCaja origen) { this.origen = origen; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }
    public Pago getPago() { return pago; }
    public void setPago(Pago pago) { this.pago = pago; }
}
