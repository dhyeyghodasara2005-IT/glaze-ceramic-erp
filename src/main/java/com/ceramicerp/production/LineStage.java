package com.ceramicerp.production;

import com.ceramicerp.master.MachineType;
import com.ceramicerp.user.Permission;

/**
 * The stages that share the line_entries table. Each one knows its machine type, its document
 * number prefix and the permission its supervisor needs - so the service never has to write
 * "if GLAZE ... else if KILN ...".
 */
public enum LineStage {
    GLAZE(MachineType.GLAZE_LINE, ProductionDocType.GLAZE_ENTRY, Permission.GLAZE_ENTRY),
    KILN(MachineType.KILN, ProductionDocType.KILN_ENTRY, Permission.KILN_ENTRY);

    private final MachineType machineType;
    private final ProductionDocType docType;
    private final Permission permission;

    LineStage(MachineType machineType, ProductionDocType docType, Permission permission) {
        this.machineType = machineType;
        this.docType = docType;
        this.permission = permission;
    }

    public MachineType machineType() {
        return machineType;
    }

    public ProductionDocType docType() {
        return docType;
    }

    public Permission permission() {
        return permission;
    }
}
