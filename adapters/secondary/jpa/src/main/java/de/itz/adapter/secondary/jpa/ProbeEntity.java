package de.itz.adapter.secondary.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class ProbeEntity {
    @Id
    @GeneratedValue
    private Long id;
    private String value;

    protected ProbeEntity() {
    }
    public ProbeEntity(String value) {
        this.value = value;
    }
    public Long id() {
        return id;
    }
    public String value() {
        return value;
    }
}
