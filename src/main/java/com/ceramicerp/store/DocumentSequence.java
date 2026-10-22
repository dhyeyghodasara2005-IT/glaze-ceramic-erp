package com.ceramicerp.store;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Last number used for (plant, document type, financial year). */
@Getter
@Setter
@Entity
@Table(name = "document_sequences")
public class DocumentSequence extends BaseEntity {

    @Column(nullable = false)
    private Long plantId;

    @Column(nullable = false, length = 20)
    private String docType;

    @Column(nullable = false, length = 5)
    private String finYear;

    @Column(nullable = false)
    private int lastNumber;
}
