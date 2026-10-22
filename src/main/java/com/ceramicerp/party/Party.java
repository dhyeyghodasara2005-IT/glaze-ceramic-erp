package com.ceramicerp.party;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/** A dealer, supplier or transporter. See V4__parties.sql for what each column is for. */
@Getter
@Setter
@Entity
@Table(name = "parties")
public class Party extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "party_type", nullable = false, length = 20)
    private PartyType partyType;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String contactPerson;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private GstRegistration gstRegistration;

    @Column(length = 15)
    private String gstin;

    @Column(length = 10)
    private String pan;

    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 2)
    private String stateCode;

    @Column(length = 10)
    private String pincode;

    @Column(nullable = false, length = 60)
    private String country = "India";

    @Column(length = 20)
    private String phone;

    @Column(length = 20)
    private String mobile;

    @Column(length = 150)
    private String email;

    @Column(length = 60)
    private String region;

    @Column(precision = 14, scale = 2)
    private BigDecimal creditLimit;

    private Integer creditDays;

    @Column(length = 100)
    private String bankName;

    @Column(length = 30)
    private String bankAccountNo;

    @Column(length = 11)
    private String bankIfsc;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;
}
