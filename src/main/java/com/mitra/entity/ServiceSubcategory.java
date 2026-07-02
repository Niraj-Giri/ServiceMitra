package com.mitra.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Entity
@Table(name = "service_subcategories")
@Data
public class ServiceSubcategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private ServiceCategory category;

    @OneToMany(mappedBy = "subcategory")
    private List<Service> services;
}
