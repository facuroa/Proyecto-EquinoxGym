package com.equinox.EquinoxGym;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Socio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String apellido;
    private String dni;
    private String telefono;
    private String email;
    private LocalDate fechaNacimiento;

    @Column(length = 1000)
    private String observaciones;

    @Enumerated(EnumType.STRING)
    private EstadoSocio estado;

    @ManyToOne
    @JoinColumn(name = "plan_id")
    private Plan plan;

    private LocalDate fechaInicioPlan;
    private LocalDate fechaVencimientoPlan;

    @OneToMany(mappedBy = "socio", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Cuota> cuotas = new ArrayList<>();

    public Socio() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public EstadoSocio getEstado() { return estado; }
    public void setEstado(EstadoSocio estado) { this.estado = estado; }

    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }

    public LocalDate getFechaInicioPlan() { return fechaInicioPlan; }
    public void setFechaInicioPlan(LocalDate fechaInicioPlan) { this.fechaInicioPlan = fechaInicioPlan; }

    public LocalDate getFechaVencimientoPlan() { return fechaVencimientoPlan; }
    public void setFechaVencimientoPlan(LocalDate fechaVencimientoPlan) { this.fechaVencimientoPlan = fechaVencimientoPlan; }

    public List<Cuota> getCuotas() { return cuotas; }
    public void setCuotas(List<Cuota> cuotas) { this.cuotas = cuotas; }

    public String getNombreCompleto() {
        return (nombre != null ? nombre : "") + " " + (apellido != null ? apellido : "");
    }

    public boolean tieneCuotas() {
        return cuotas != null && !cuotas.isEmpty();
    }

    public Long getDiasRestantesPlan() {
        if (fechaVencimientoPlan == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), fechaVencimientoPlan);
    }

    public String getTextoVencimientoPlan() {
        Long dias = getDiasRestantesPlan();
        if (dias == null) {
            return "-";
        }
        if (dias == 0) {
            return "Vence hoy";
        }
        if (dias > 0) {
            return dias == 1 ? "Falta 1 día" : "Faltan " + dias + " días";
        }
        long atraso = Math.abs(dias);
        return atraso == 1 ? "Vencido hace 1 día" : "Vencido hace " + atraso + " días";
    }
}
