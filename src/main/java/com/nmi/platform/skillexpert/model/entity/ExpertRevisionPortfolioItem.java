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
@Table(name = "expert_revision_portfolio_items")
@Getter
@Setter
public class ExpertRevisionPortfolioItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private ExpertProfileRevision revision;

    @Column(name = "media_uri", nullable = false, columnDefinition = "TEXT")
    private String mediaUri;

    @Column(name = "caption", length = 500)
    private String caption;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
