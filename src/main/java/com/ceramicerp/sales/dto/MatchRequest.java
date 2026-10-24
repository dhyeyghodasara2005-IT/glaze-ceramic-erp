package com.ceramicerp.sales.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MatchRequest(@NotEmpty @Size(max = 200) List<@Size(max = 200) String> descriptions) {
}
