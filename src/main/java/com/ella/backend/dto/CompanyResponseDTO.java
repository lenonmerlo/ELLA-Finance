package com.ella.backend.dto;

import lombok.Data;

@Data
public class CompanyResponseDTO {

    private String id;
    private String name;
    private String document;
    private String description;
    private String ownerId;
    private String ownerName;
    private Object createdAt;
    private Object updatedAt;
}
