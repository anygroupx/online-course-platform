package com.course.platform.infra.docking;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.course.platform.domain.exception.ProviderRequestException;

/** Do not propagate a JSON parser exception: it may quote credentials from an upstream body. */
public final class ProviderResponseParser {
    private ProviderResponseParser() {}

    public static JSONArray requireArray(JSONObject object, String key) {
        if (!(object.get(key) instanceof JSONArray array)) {
            throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
        }
        return array;
    }

    public static JSONObject parseObject(String response) {
        try {
            if (response == null || !JSONUtil.isTypeJSONObject(response)) {
                throw new IllegalArgumentException();
            }
            JSONObject result = JSONUtil.parseObj(response);
            // All current provider protocols require an application status code. An HTML login
            // page or a JSON object unrelated to the protocol must never count as a successful probe.
            if (result.get("code") == null || !String.valueOf(result.get("code")).matches("-?[0-9]{1,10}")
                    || result.getInt("code") == null) {
                throw new IllegalArgumentException();
            }
            return result;
        } catch (RuntimeException ex) {
            throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
        }
    }
}
