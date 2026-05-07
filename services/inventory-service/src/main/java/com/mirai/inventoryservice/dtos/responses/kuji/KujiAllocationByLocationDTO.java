package com.mirai.inventoryservice.dtos.responses.kuji;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One row per OPEN kuji box tier at a queried location with a non-null linked product.
 * Drives the "what's locked at this location" view in the location detail modal and
 * adjust/transfer "available" cap.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KujiAllocationByLocationDTO {
    private UUID boxId;
    private String boxLabel;

    private UUID tierId;
    private String tierLabel;
    private String tierLetter;

    private UUID linkedProductId;
    private String linkedProductName;
    private Integer count;

    /** Optional — set when the box is on a machine display. */
    private UUID machineDisplayId;
    /** Resolved location code of the machine the display is on. Used for the virtual "S1-Display" label. */
    private String machineCode;
}
