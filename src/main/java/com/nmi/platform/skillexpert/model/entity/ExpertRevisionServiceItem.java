package com.nmi.platform.skillexpert.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "expert_revision_services")
@Getter
@Setter
public class ExpertRevisionServiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private ExpertProfileRevision revision;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "price", length = 64)
    private String price;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
