package com.ceramicerp.organization;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A firm / legal entity with its own GSTIN. Morbi groups often run several firms,
 * so one installation supports many companies, each with many plants.
 */
@Getter
@Setter
@Entity
@Table(name = "companies")
public class Company extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 200)
    private String legalName;

    @Column(length = 15)
    private String gstin;

    @Column(length = 10)
    private String pan;

    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 6)
    private String pincode;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(nullable = false)
    private boolean active = true;
}
