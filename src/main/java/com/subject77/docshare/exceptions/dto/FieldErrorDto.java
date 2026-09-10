package com.subject77.docshare.exceptions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldErrorDto {
    private String field;
    private String message;
    private Object rejectedValue;
}
