package com.parkerjtech.backendserviceapi.support;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

public final class HttpTestClient {
    private final int port;
    private final CookieManager cookies;
    private final HttpClient client;
    private final JsonMapper mapper=JsonMapper.builder().build();
    public HttpTestClient(int port) { this(port,new CookieManager(null,CookiePolicy.ACCEPT_ALL)); }
    public HttpTestClient(int port,CookieManager cookies) {
        this.port=port; this.cookies=cookies;
        client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();
    }
    public CookieManager cookies() { return cookies; }
    public Reply get(String path) throws Exception { return send("GET",path,null,Map.of()); }
    public Reply post(String path,Object body,Map<String,Object> session) throws Exception {
        return send("POST",path,body,Map.of((String)session.get("csrfHeaderName"),(String)session.get("csrfToken")));
    }
    @SuppressWarnings("unchecked")
    public Reply send(String method,String path,Object body,Map<String,String> headers) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20));
        headers.forEach(request::header);
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if(body!=null) request.header("Content-Type","application/json");
        var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        Map<String,Object> json=response.body().stripLeading().startsWith("{")?mapper.readValue(response.body(),Map.class):Map.of();
        return new Reply(response.statusCode(),response.headers(),json,response.body());
    }
    public record Reply(int status,HttpHeaders headers,Map<String,Object> json,String body) {
        @SuppressWarnings("unchecked") public List<Map<String,Object>> items() { return (List<Map<String,Object>>)json.get("items"); }
    }
}
