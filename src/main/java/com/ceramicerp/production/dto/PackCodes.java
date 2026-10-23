package com.ceramicerp.production.dto;

import java.util.List;

/** Shade and caliber codes a lot has used so far: the pack entry suggests them first. */
public record PackCodes(List<String> shades, List<String> calibers) {
}
