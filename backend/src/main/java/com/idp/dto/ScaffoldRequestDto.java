package com.idp.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScaffoldRequestDto {
    private String name;
    private String description;
    private String stackTemplate; // SPRING_BOOT, ANGULAR, GO, PYTHON
    private String ownerTeam;
    private boolean enableCiCd;
    private boolean enablePostgres;
}
