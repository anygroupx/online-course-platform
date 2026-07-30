package com.course.platform.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaSetupVO {
    private String setupToken;
    private String otpauthUrl;
    private String secretMasked;
    private List<String> backupCodes;
}
