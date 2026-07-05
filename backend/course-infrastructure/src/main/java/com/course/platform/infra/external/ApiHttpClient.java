package com.course.platform.infra.external;

import cn.hutool.core.util.StrUtil;
import com.course.platform.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 通用API调用客户端
 */
@Slf4j
@Component
public class ApiHttpClient {

    private final RestTemplate restTemplate;

    public ApiHttpClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 发送POST请求（表单格式）
     *
     * @param url    请求地址
     * @param params 请求参数
     * @return 响应字符串
     */
    public String postForString(String url, Map<String, Object> params) {
        return postForString(url, params, null);
    }

    /**
     * 发送POST请求（表单格式），带Header
     *
     * @param url     请求地址
     * @param params  请求参数
     * @param headers 请求头
     * @return 响应字符串
     */
    public String postForString(String url, Map<String, Object> params, HttpHeaders headers) {
        try {
            if (headers == null) {
                headers = new HttpHeaders();
            }
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
            if (params != null) {
                map.setAll(params);
            }

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(map, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                log.error("API请求失败: url={}, status={}", url, response.getStatusCode());
                throw new BusinessException("第三方API请求失败: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("API请求异常: url={}, error={}", url, e.getMessage(), e);
            throw new BusinessException("第三方API请求异常: " + e.getMessage());
        }
    }

    /**
     * 发送GET请求
     *
     * @param url    请求地址
     * @param params 请求参数
     * @return 响应字符串
     */
    public String getForString(String url, Map<String, Object> params) {
        try {
            StringBuilder urlBuilder = new StringBuilder(url);
            if (params != null && !params.isEmpty()) {
                if (!url.contains("?")) {
                    urlBuilder.append("?");
                } else {
                    urlBuilder.append("&");
                }
                params.forEach((k, v) -> {
                    if (v != null) {
                        urlBuilder.append(k).append("=").append(v).append("&");
                    }
                });
                // 移除最后一个&
                if (urlBuilder.charAt(urlBuilder.length() - 1) == '&') {
                    urlBuilder.deleteCharAt(urlBuilder.length() - 1);
                }
            }

            ResponseEntity<String> response = restTemplate.getForEntity(urlBuilder.toString(), String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                log.error("API请求失败: url={}, status={}", url, response.getStatusCode());
                throw new BusinessException("第三方API请求失败: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("API请求异常: url={}, error={}", url, e.getMessage(), e);
            throw new BusinessException("第三方API请求异常: " + e.getMessage());
        }
    }
}
