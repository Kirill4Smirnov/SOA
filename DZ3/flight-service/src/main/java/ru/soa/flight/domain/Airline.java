package ru.soa.flight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "airlines")
public class Airline {
    @Id
    @Column(length = 3)
    private String code;
    @Column(nullable = false, unique = true, length = 120)
    private String name;

    protected Airline() {}

    public String getCode() { return code; }
    public String getName() { return name; }
}

