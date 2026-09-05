package com.course.platform.infra.http;

import cn.hutool.json.JSONUtil;
import okhttp3.HttpUrl;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;

/** Optional trusted DNS for deployments whose OS resolver returns proxy/Fake-IP addresses.
 * The resolver endpoint and bootstrap IPs are fixed, TLS remains verified, and all returned
 * addresses are still checked by SsrfGuard. No fallback to untrusted system DNS on failure.
 */
final class CloudflareDnsResolver implements SsrfGuard.HostResolver {
    private static final String HOST = "cloudflare-dns.com";
    private static final OutboundRequestPolicy POLICY = new OutboundRequestPolicy("dns-over-https",
            Set.of(HOST), Set.of(), Set.of(), 65536,
            Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(3));
    private final SafeHttpClient transport;

    CloudflareDnsResolver() {
        try {
            transport = new SafeHttpClient(new SsrfGuard(Map.of(HOST, List.of(
                    InetAddress.getByAddress(new byte[]{1,1,1,1}),
                    InetAddress.getByAddress(new byte[]{1,0,0,1})))));
        } catch (UnknownHostException ex) { throw new IllegalStateException("Invalid DNS bootstrap constants"); }
    }

    CloudflareDnsResolver(SafeHttpClient transport) { this.transport = transport; }

    @Override public List<InetAddress> resolve(String host) throws UnknownHostException {
        try {
            List<InetAddress> addresses = new ArrayList<>();
            for (int type : new int[]{1,28}) {
                var uri = new HttpUrl.Builder().scheme("https").host(HOST).addPathSegment("dns-query")
                        .addQueryParameter("name",host).addQueryParameter("type",String.valueOf(type)).build().uri();
                var response = transport.get(uri,Map.of("Accept","application/dns-json"),POLICY);
                if (!response.isSuccessful()) throw failed();
                addresses.addAll(parse(response.body(),host,type));
            }
            if (addresses.isEmpty()) throw failed();
            return List.copyOf(new LinkedHashSet<>(addresses));
        } catch (RuntimeException ex) { throw failed(); }
    }

    static List<InetAddress> parse(String body, String host, int type) throws UnknownHostException {
        try {
            var json = JSONUtil.parseObj(body);
            if (json.getInt("Status",-1) != 0 || json.getBool("TC",false)) throw failed();
            var questions = json.getJSONArray("Question");
            if (questions == null || questions.size()!=1) throw failed();
            var question = questions.getJSONObject(0);
            String queried = question.getStr("name","");
            if (queried.endsWith(".")) queried=queried.substring(0,queried.length()-1);
            if (!host.equalsIgnoreCase(queried) || question.getInt("type",-1)!=type) throw failed();
            List<InetAddress> result = new ArrayList<>();
            var answers=json.getJSONArray("Answer");
            if (answers==null) return result; // NODATA for one family is valid.
            for (int i=0;i<answers.size();i++) {
                var answer=answers.getJSONObject(i);
                if (answer.getInt("type",-1)!=type) continue; // CNAMEs are resolved by the trusted resolver.
                String literal=answer.getStr("data","");
                // Never resolve names found in a DNS response via the OS resolver.
                if (type==1 ? !literal.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")
                        : !literal.matches("[0-9a-fA-F]*:[0-9a-fA-F:]+")) throw failed();
                InetAddress address=InetAddress.getByName(literal);
                if (type==1 && address.getAddress().length!=4) throw failed();
                result.add(address);
            }
            return result;
        } catch (RuntimeException ex) { throw failed(); }
    }
    private static UnknownHostException failed() { return new UnknownHostException("Trusted DNS resolution failed"); }
}
