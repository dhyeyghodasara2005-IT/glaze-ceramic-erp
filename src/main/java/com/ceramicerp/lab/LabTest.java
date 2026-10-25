package com.ceramicerp.lab;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Material;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.store.StoreDocument;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The sample of one material from one truck (GRN): what the lab found, and whether it is accepted. */
@Getter
@Setter
@Entity
@Table(name = "lab_tests")
public class LabTest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(nullable = false, length = 40)
    private String testNo;

    /** The GRN the truck came in on. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private StoreDocument document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(nullable = false)
    private LocalDate testedOn;

    @Column(length = 150)
    private String testedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private LabVerdict verdict;

    /** How many results fell outside their range. */
    @Column(nullable = false)
    private int outOfRange;

    @Column(length = 500)
    private String remarks;

    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<LabResult> results = new ArrayList<>();
}
