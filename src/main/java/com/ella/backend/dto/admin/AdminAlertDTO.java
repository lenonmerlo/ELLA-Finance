package com.ella.backend.dto.admin;

import com.ella.backend.enums.AdminAlertSeverity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminAlertDTO {
    private String code;
    private AdminAlertSeverity severity;
    private String title;
    private String message;
}
