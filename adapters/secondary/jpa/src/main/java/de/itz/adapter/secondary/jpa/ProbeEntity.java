package de.itz.adapter.secondary.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import org.jspecify.annotations.Nullable;

@Entity
public class ProbeEntity {
    @Id
    @GeneratedValue
    private @Nullable Long id;
    private @Nullable String value;

    protected ProbeEntity() {
    }
    public ProbeEntity(String value) {
        this.value = value;
    }
    public @Nullable Long id() {
        return id;
    }

    public @Nullable String value() {
        return value;
    }
}
