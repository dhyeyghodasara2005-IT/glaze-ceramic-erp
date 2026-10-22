package com.ceramicerp.store.dto;

/**
 * A small item for a dropdown on the store screens.
 *
 * @param extra   material: unit code ("KG"); others: null
 * @param decimals material: decimal places its unit allows; others: 0
 */
public record StoreOption(Long id, String code, String name, String extra, int decimals) {
}
