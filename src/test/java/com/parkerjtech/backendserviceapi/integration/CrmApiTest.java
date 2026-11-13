package com.parkerjtech.backendserviceapi.integration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import com.parkerjtech.backendserviceapi.support.*;
import com.parkerjtech.backendserviceapi.repository.LeadRepository;
import com.parkerjtech.backendserviceapi.service.LeadService;
import com.parkerjtech.backendserviceapi.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Import(TestTimeConfiguration.class)
class CrmApiTest {
    @LocalServerPort int port;
    @Autowired MutableClock clock;
    @Autowired LeadService service;
    @MockitoSpyBean LeadRepository leads;
    @BeforeEach void resetTime() { clock.setInstant(TestTimeConfiguration.NOW); }
    static Map<String,Object> valid() {
        var data=new LinkedHashMap<String,Object>();
        data.put("name"," Fictional Sam ");data.put("company"," Demo Co ");
        data.put("serviceInterest","BUSINESS_AUTOMATION");data.put("source","REFERRAL");data.put("estimatedValue",5000);
        return data;
    }
    @Test void createsLeadWithLocationAndAutomation() throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();
        var created=client.post("/api/demo/leads",valid(),session);
        assertThat(created.status()).isEqualTo(201);
        var id=created.json().get("id");
        assertThat(created.headers().firstValue("location")).contains("/api/demo/leads/"+id);
        assertThat(created.json()).containsEntry("name","Fictional Sam").containsEntry("company","Demo Co").containsEntry("stage","NEW").containsEntry("priority","HIGH").doesNotContainKey("workspaceId");
        assertThat(created.json().get("estimatedValue")).isInstanceOf(Number.class);
        assertThat(created.json().get("createdAt")).isEqualTo("2026-11-11T12:00:00Z");
        var fetched=client.get("/api/demo/leads/"+id);
        assertThat(fetched.status()).isEqualTo(200);
        assertThat(fetched.json()).isEqualTo(created.json());
        assertThat(client.get("/api/demo/leads").items()).hasSize(1);
        var tasks=client.get("/api/demo/tasks").items();
        assertThat(tasks).singleElement().satisfies(task -> assertThat(task).containsEntry("leadId",id).containsEntry("status","OPEN").containsEntry("dueAt","2026-11-12T12:00:00Z").doesNotContainKey("workspaceId"));
        var activity=client.get("/api/demo/activity").items();
        assertThat(activity).extracting(event -> event.get("type")).containsExactly("LEAD_CREATED","PRIORITY_ASSIGNED","FOLLOW_UP_CREATED");
        assertThat(activity).allSatisfy(event -> assertThat(event).containsEntry("leadId",id).doesNotContainKey("workspaceId"));
        assertNoStore(created);
    }
    @Test void otherVisitorCannotRetrieveLead() throws Exception {
        var a=new HttpTestClient(port);var sa=a.get("/api/demo/session").json();var created=a.post("/api/demo/leads",valid(),sa);
        var b=new HttpTestClient(port);b.get("/api/demo/session");
        var result=b.get("/api/demo/leads/"+created.json().get("id"));
        assertThat(result.status()).isEqualTo(404);assertThat(result.json()).containsEntry("code","LEAD_NOT_FOUND");
        for(var path:List.of("leads","tasks","activity")) assertThat(b.get("/api/demo/"+path+"?workspaceId="+sa.get("workspaceId")).items()).isEmpty();
        assertNoStore(result);
    }
    @ParameterizedTest @ValueSource(strings={"workspaceId","id","stage","priority","createdAt","updatedAt","lastActivityAt","unexpected"})
    void rejectsOwnershipOverride(String field) throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();var data=valid();data.put(field,"untrusted");
        var result=client.post("/api/demo/leads",data,session);
        assertThat(result.status()).isEqualTo(400);assertThat(result.json()).containsEntry("code","INVALID_REQUEST");assertNoStore(result);
    }
    @Test void requiresBootstrapForReads() throws Exception {
        var client=new HttpTestClient(port);
        for(var path:List.of("leads","tasks","activity","leads/"+UUID.randomUUID())) {
            var result=client.get("/api/demo/"+path);
            assertThat(result.status()).isEqualTo(401);assertThat(result.json()).containsEntry("code","WORKSPACE_REQUIRED");assertNoStore(result);
        }
    }
    @Test void rejectsMutationWithoutCsrf() throws Exception {
        var client=new HttpTestClient(port);client.get("/api/demo/session");
        for(var headers:List.of(Map.<String,String>of(),Map.of("X-CSRF-TOKEN","wrong"))) {
            var result=client.send("POST","/api/demo/leads",valid(),headers);
            assertThat(result.status()).isEqualTo(403);assertThat(result.json()).containsEntry("code","CSRF_INVALID");assertNoStore(result);
        }
    }
    @Test void rejectsExpiredWorkspaceWithoutInserting() throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();
        clock.setInstant(Instant.parse((String)session.get("expiresAt")));
        var result=client.post("/api/demo/leads",valid(),session);
        assertThat(result.status()).isEqualTo(401);
        assertThat(leads.count(UUID.fromString((String)session.get("workspaceId")))).isZero();
        assertThat(client.get("/api/demo/leads").status()).isEqualTo(401);
        var fresh=client.get("/api/demo/session").json();
        assertThat(fresh.get("workspaceId")).isNotEqualTo(session.get("workspaceId"));
        assertThat(client.get("/api/demo/leads").items()).isEmpty();
    }
    @Test void fabricatedCookieCannotMutateExistingWorkspace() throws Exception {
        var a=new HttpTestClient(port);var session=a.get("/api/demo/session").json();
        var b=new HttpTestClient(port);
        var result=b.send("POST","/api/demo/leads",valid(),Map.of("Cookie","SESSION=bogus",(String)session.get("csrfHeaderName"),(String)session.get("csrfToken")));
        assertThat(result.status()).isEqualTo(403);
        assertThat(leads.count(UUID.fromString((String)session.get("workspaceId")))).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"serviceInterest","source","contactPreference"})
    void rejectsUnknownAndNumericEnums(String field) throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();
        for(Object value:List.of("UNRECOGNIZED",0)) {
            var data=valid();data.put(field,value);
            assertThat(client.post("/api/demo/leads",data,session).status()).isEqualTo(400);
        }
    }
    @Test void returnsFieldErrorsForInvalidValues() throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();
        var data=valid();data.put("name"," ");data.put("estimatedValue",new BigDecimal("1.001"));
        var result=client.post("/api/demo/leads",data,session);
        assertThat(result.status()).isEqualTo(400);assertThat(result.json()).containsEntry("code","INVALID_REQUEST");
        assertThat(((Map<?,?>)result.json().get("fieldErrors")).containsKey("name")).isTrue();
        assertThat(((Map<?,?>)result.json().get("fieldErrors")).containsKey("estimatedValue")).isTrue();
    }
    @Test void reportsCapacityAsConflict() throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();var id=UUID.fromString((String)session.get("workspaceId"));
        var command=new CreateLeadCommand("Demo","Fictional Co",Lead.ServiceInterest.WEBSITE,Lead.Source.OTHER,BigDecimal.ZERO,null);
        for(int i=0;i<100;i++)service.create(id,command);
        var result=client.post("/api/demo/leads",valid(),session);
        assertThat(result.status()).isEqualTo(409);assertThat(result.json()).containsEntry("code","WORKSPACE_LIMIT_REACHED");assertNoStore(result);
    }
    @Test void hidesDatabaseFailureDetails() throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();var id=UUID.fromString((String)session.get("workspaceId"));
        doThrow(new DataAccessResourceFailureException("SELECT secret FROM private_table; session=private")).when(leads).list(id);
        var result=client.get("/api/demo/leads");
        assertThat(result.status()).isEqualTo(500);assertThat(result.json()).containsEntry("code","INTERNAL_ERROR");
        assertThat(result.body()).doesNotContain("SELECT","private_table","session=","stackTrace");assertNoStore(result);
    }
    @Test void returnsDeterministicOrderingForEqualTimestamps() throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();var ids=new ArrayList<String>();
        for(int i=0;i<3;i++)ids.add((String)client.post("/api/demo/leads",valid(),session).json().get("id"));
        ids.sort(Comparator.naturalOrder());
        assertThat(client.get("/api/demo/leads").items()).extracting(row -> (String)row.get("id")).containsExactlyElementsOf(ids);
    }
    @Test void rejectsMalformedIdentifierAndBody() throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();
        assertThat(client.get("/api/demo/leads/not-a-uuid").status()).isEqualTo(400);
        assertThat(client.post("/api/demo/leads","wrong-body-shape",session).status()).isEqualTo(400);
    }
    @Test void doesNotAllowCredentialedCrossOriginRequests() throws Exception {
        var result=new HttpTestClient(port).send("OPTIONS","/api/demo/leads",null,Map.of("Origin","https://untrusted.invalid","Access-Control-Request-Method","POST"));
        assertThat(result.headers().firstValue("access-control-allow-origin")).isEmpty();
        assertThat(result.headers().firstValue("access-control-allow-credentials")).isEmpty();
    }
    @Test void preservesHttpStatusForUnknownRoutesAndUnsupportedMethods() throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();
        var missing=client.get("/api/demo/not-an-endpoint");
        assertThat(missing.status()).isEqualTo(404);assertNoStore(missing);
        var unsupported=client.send("PUT","/api/demo/leads",valid(),Map.of((String)session.get("csrfHeaderName"),(String)session.get("csrfToken")));
        assertThat(unsupported.status()).isEqualTo(405);assertNoStore(unsupported);
        assertThat(unsupported.headers().firstValue("allow").orElse("")).contains("GET","POST");
    }
    private void assertNoStore(HttpTestClient.Reply reply) {
        assertThat(reply.headers().firstValue("cache-control").orElse("")).contains("no-store");
    }
}
