package com.mirai.inventoryservice.dtos.responses.kuji;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One row per OPEN kuji box tier referencing a queried product.
 * Drives "where is this product locked?" in the product modal — virtual sibling rows
 * labeled "[machineCode]-Display" when the box is on a machine, else "(in kuji box: <label>) at <locationCode>".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KujiAllocationByProductDTO {
    private UUID boxId;
    private String boxLabel;

    private UUID locationId;
    private String locationCode;

    /** Optional — set when the box is on a machine display. */
    private UUID machineDisplayId;
    /** Resolved location code of the machine the display is on. Used for the virtual "S1-Display" label. */
    private String machineCode;

    private UUID tierId;
    private String tierLabel;
    private String tierLetter;
    private Integer count;
}
