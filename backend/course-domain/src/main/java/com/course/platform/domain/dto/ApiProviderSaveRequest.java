package com.course.platform.domain.dto;

import com.course.platform.domain.entity.ApiProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Explicit write allowlist: verification metadata, balances and timestamps are server-managed. */
@Data
public class ApiProviderSaveRequest {
    private Long id;
    @NotBlank(message = "请输入接口名称")
    @Size(max = 100, message = "接口名称不能超过100个字符")
    private String name;
    @NotBlank(message = "请选择接口类型")
    @Size(max = 50)
    private String providerType;
    @NotBlank(message = "请输入API地址")
    @Size(max = 2048, message = "API地址过长")
    private String apiUrl;
    @Size(max = 100)
    private String username;
    @Size(max = 2048)
    private String password;
    @Size(max = 2048)
    private String apiKey;
    @Size(max = 4096)
    private String token;
    @Size(max = 8192)
    private String cookie;
    @Min(0) @Max(2)
    private Integer status;

    public ApiProvider toProvider() {
        ApiProvider provider = new ApiProvider();
        provider.setId(id);
        provider.setName(name);
        provider.setProviderType(providerType);
        provider.setApiUrl(apiUrl);
        provider.setUsername(username);
        provider.setPassword(password);
        provider.setApiKey(apiKey);
        provider.setToken(token);
        provider.setCookie(cookie);
        provider.setStatus(status);
        return provider;
    }
}
