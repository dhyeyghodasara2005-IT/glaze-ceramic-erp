package com.ceramicerp.lab.dto;

import java.util.List;

/**
 * What the lab's form needs for one sample: the truck, the tests the material gets, and the result so far.
 *
 * @param test null until the lab has saved a result
 */
public record LabForm(LabSample sample, List<LabParameterResponse> parameters, LabTestResponse test) {
}
