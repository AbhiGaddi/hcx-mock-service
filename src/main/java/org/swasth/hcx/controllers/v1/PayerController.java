package org.swasth.hcx.controllers.v1;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import io.hcxprotocol.utils.Operations;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.swasth.hcx.controllers.BaseController;
import org.swasth.hcx.dto.Response;
import org.swasth.hcx.exception.ClientException;
import org.swasth.hcx.service.PostgresService;
import org.swasth.hcx.utils.Constants;
import org.swasth.hcx.utils.JSONUtils;

import java.sql.ResultSet;
import java.util.*;

import static org.swasth.hcx.utils.Constants.*;

@RestController
public class PayerController extends BaseController {

    @Value("${postgres.table.payerData}")
    private String table;

    @Autowired
    private PostgresService postgres;

    @Value("${request_list.default_limit}")
    private int listLimit;

    @Value("${request_list.default_latest_data_by_day}")
    private int dayLimit;

    @PostMapping(value = "/payer/request/list")
    public ResponseEntity<Object> requestList(@RequestBody Map<String, Object> requestBody) {
        try {
            String type = (String) requestBody.getOrDefault("type", "");
            long days = System.currentTimeMillis()-(int) requestBody.getOrDefault("days", dayLimit)*24*60*60*1000;
            int limit = (int) requestBody.getOrDefault("limit", listLimit);
            int offset = (int) requestBody.getOrDefault("offset", 0);
            String senderCode = (String) requestBody.getOrDefault("sender_code", "");
            String recipientCode = (String) requestBody.getOrDefault("recipient_code", "");
            validateStr("type", type);
            List<Object> result = new ArrayList<>();
            StringBuilder countQuery = new StringBuilder("SELECT count(*) FROM " + table + " WHERE action = '" + type + "' AND created_on > " + days);
            addToQuery(countQuery, senderCode, "sender_code");
            addToQuery(countQuery, recipientCode, "recipient_code");
            ResultSet resultSet1 = postgres.executeQuery(countQuery.toString());
            Map<String, Object> resp = new HashMap<>();
            while (resultSet1.next()) {
                resp.put("count", resultSet1.getInt("count"));
            }
            String query = countQuery.toString().replace("count(*)", "count(*) over(),*") + " ORDER BY created_on DESC LIMIT " + limit + " OFFSET " + offset;
            ResultSet resultSet = postgres.executeQuery(query);
            while (resultSet.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("sender_code", resultSet.getString("sender_code"));
                map.put("recipient_code", resultSet.getString("recipient_code"));
                map.put("request_id", resultSet.getString("request_id"));
                map.put("response_fhir", JSONUtils.deserialize(resultSet.getString("response_fhir"), Map.class));
                map.put("status", resultSet.getString("status"));
                map.put("additional_info", JSONUtils.deserialize(resultSet.getString("additional_info"), Map.class));
                map.put("payload", JSONUtils.deserialize(resultSet.getString("request_fhir"), Map.class));
                map.put("otp_verification", resultSet.getString("otp_verification"));
                map.put("account_number", resultSet.getString("account_number"));
                map.put("ifsc_code", resultSet.getString("ifsc_code"));
                result.add(map);
            }
            resp.put(type, result);
            return new ResponseEntity<>(resp, HttpStatus.OK);
        } catch (Exception e) {
            return exceptionHandler(new Response(), e);
        }
    }

    private void addToQuery(StringBuilder query, String code, String field){
        if(!StringUtils.isEmpty(code)) {
            query.append(" AND " + field + " = '" + code + "'");
        }
    }

    @PostMapping(value = "/payer/coverageeligibility/approve")
    public ResponseEntity<Object> coverageEligibilityApprove(@RequestBody Map<String, Object> requestBody) {
        return review(requestBody,"coverageeligibility", APPROVED);
    }

    @PostMapping(value = "/payer/coverageeligibility/reject")
    public ResponseEntity<Object> coverageEligibilityReject(@RequestBody Map<String, Object> requestBody) {
        return review(requestBody,"coverageeligibility", REJECTED);
    }

    @PostMapping(value = "/payer/preauth/approve")
    public ResponseEntity<Object> preauthApprove(@RequestBody Map<String, Object> requestBody) {
        return review(requestBody,"preauth", APPROVED);
    }

    @PostMapping(value = "/payer/preauth/reject")
    public ResponseEntity<Object> preauthReject(@RequestBody Map<String, Object> requestBody) {
        return review(requestBody,"preauth", REJECTED);
    }

    @PostMapping(value = "/payer/claim/approve")
    public ResponseEntity<Object> claimApprove(@RequestBody Map<String, Object> requestBody) {
        return review(requestBody,"claim", APPROVED);
    }

    @PostMapping(value = "/payer/claim/reject")
    public ResponseEntity<Object> claimReject(@RequestBody Map<String, Object> requestBody) {
        return review(requestBody,"claim", REJECTED);
    }

    @PostMapping(value = "/payer/response/update")
    public ResponseEntity<Object> updateResponse(@RequestBody Map<String, Object> requestBody) throws ClientException {
        updateDB((String) requestBody.get("request_id"), (String) requestBody.get("response_fhir"), "response.complete");
        return new ResponseEntity<>(new Response(), HttpStatus.OK);
    }

    public void updateDB(String requestId, String respfhir, String onActionStatus) throws ClientException {
        String query = String.format("UPDATE %s SET response_fhir = '%s', on_action_status= '%s' WHERE request_id ='%s'", table, respfhir, onActionStatus, requestId);
        postgres.execute(query);
    }


    public ResponseEntity<Object> review(Map<String, Object> requestBody, String entity, String status){
        try {
            System.out.println("Review: " + status + " :: entity: " + entity + " :: request body: " + requestBody);
            String id = (String) requestBody.getOrDefault("request_id", "");
            validateStr("request_id", id);
            validateStr("recipient_code", (String) requestBody.getOrDefault("recipient_code", ""));
            Map<String,Object> output = new HashMap<>();
            if (StringUtils.equals(entity, "coverageeligibility")) {
                String updateQuery = String.format("UPDATE %s SET status='%s',updated_on=%d WHERE request_id='%s' RETURNING %s,%s",
                        table, status, System.currentTimeMillis(), id, "raw_payload", "response_fhir");
                ResultSet resultset = postgres.executeQuery(updateQuery);
                String respfhir = "";
                String actionJwe = "";
                while(resultset.next()){
                    respfhir = resultset.getString("response_fhir");
                    actionJwe = resultset.getString("raw_payload");
                }
                if(status.equals(APPROVED)){
                    onActionCall.sendOnAction((String) requestBody.get("recipient_code"), respfhir, Operations.COVERAGE_ELIGIBILITY_ON_CHECK, actionJwe, "response.complete", output);
                } else {
                    String bundleString = getCoverageRejectedBundle(respfhir);
                    System.out.println("Rejected Response bundle: " + bundleString);
                    onActionCall.sendOnAction((String) requestBody.get("recipient_code"), bundleString, Operations.COVERAGE_ELIGIBILITY_ON_CHECK, actionJwe, "response.complete", output);
                }
            } else {
                String type = (String) requestBody.getOrDefault("type", "");
                validateStr("type", type);
                if (!Constants.PAYOR_APPROVAL_TYPES.contains(type))
                    throw new ClientException("Invalid type, allowed types are: " + Constants.PAYOR_APPROVAL_TYPES);
                Map<String, Object> info = new HashMap<>();
                info.put("status", status);
                info.put("remarks", requestBody.getOrDefault("remarks", ""));
                if(StringUtils.equals(APPROVED, status)) {
                    if(!requestBody.containsKey("approved_amount") || !(requestBody.get("approved_amount") instanceof Integer))
                        throw new ClientException("Approved amount is mandatory field and should be a number");
                    info.put("approved_amount", requestBody.getOrDefault("approved_amount", 0));
                    info.put("account_number", requestBody.getOrDefault("account_number", 0));
                    info.put("ifsc_code", requestBody.getOrDefault("ifsc_code", ""));
                }
                String query = String.format("UPDATE %s SET additional_info = jsonb_set(additional_info::jsonb, '{%s}', '%s'),updated_on = %d WHERE request_id = '%s' RETURNING %s,%s,%s,%s,%s",
                        table, type, JSONUtils.serialize(info), System.currentTimeMillis(), id, "additional_info", "status", "raw_payload", "response_fhir", "action");
                ResultSet resultSet = postgres.executeQuery(query);
                Map<String, Object> addInfo = new HashMap<>();
                String existingStatus = PENDING;
                String respfhir = "";
                String actionJwe = "";
                String action = "";
                while (resultSet.next()) {
                    addInfo.putAll(JSONUtils.deserialize(resultSet.getString("additional_info"), Map.class));
                    existingStatus = resultSet.getString("status");
                    respfhir = resultSet.getString("response_fhir");
                    actionJwe = resultSet.getString("raw_payload");
                    action = resultSet.getString("action");
                }

                if (!addInfo.isEmpty()) {
                    String overallStatus = getStatus(addInfo);
                    if (!StringUtils.equalsIgnoreCase(overallStatus, existingStatus)) {
                        String updateQuery = String.format("UPDATE %s SET status = '%s',updated_on = %d WHERE request_id = '%s'"
                                , table, overallStatus, System.currentTimeMillis(), id);
                        postgres.execute(updateQuery);
                    }
                    if (overallStatus.equals(APPROVED)) {
                        String bundleString = getApprovedClaimBundle(requestBody, entity, respfhir);
                        System.out.println("Approved Response bundle: " + bundleString);
                        onActionCall.sendOnAction((String) requestBody.get("recipient_code"), bundleString, action.contains("preauth") ? Operations.PRE_AUTH_ON_SUBMIT : Operations.CLAIM_ON_SUBMIT, actionJwe, "response.complete", output);
                    } else if (overallStatus.equals(REJECTED)){
                        String bundleString = getRejectedClaimBundle(entity, type, respfhir);
                        System.out.println("Rejected Response bundle: " + bundleString);
                        onActionCall.sendOnAction((String) requestBody.get("recipient_code"), bundleString, action.contains("preauth") ? Operations.PRE_AUTH_ON_SUBMIT : Operations.CLAIM_ON_SUBMIT, actionJwe, "response.complete", output);
                    }
                }
            }
            Map<String,Object> resp = new HashMap<>();
            resp.put("timestamp", System.currentTimeMillis());
            resp.put("status", Constants.SUCCESSFUL);
            resp.put("reason", "");
            System.out.println("Process completed :: request id :" + id);
            return new ResponseEntity<>(resp, HttpStatus.OK);
        } catch (Exception e) {
            return exceptionHandler(new Response(), e);
        }
    }

    private static String getCoverageRejectedBundle(String respfhir) {
        IParser p = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
        Bundle newBundle = p.parseResource(Bundle.class, respfhir);
        for(int i=0; i < newBundle.getEntry().size(); i++){
            Bundle.BundleEntryComponent par = newBundle.getEntry().get(i);
            DomainResource dm = (DomainResource) par.getResource();
            System.out.println("type of dm" + dm);
            if(dm.getClass() == CoverageEligibilityResponse.class){
                System.out.println("index " + i);
                ((CoverageEligibilityResponse) dm).getError().add(new CoverageEligibilityResponse.ErrorsComponent(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/adjudication-error").setCode("a001").setDisplay("Coverage Eligibility Request has been rejected"))));
            }
        }
        return p.encodeResourceToString(newBundle);
    }

    private static String getApprovedClaimBundle(Map<String, Object> requestBody, String entity, String respfhir) {
        IParser p = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
        Bundle newBundle = p.parseResource(Bundle.class, respfhir);
        for(int i=0; i < newBundle.getEntry().size(); i++){
            Bundle.BundleEntryComponent par = newBundle.getEntry().get(i);
            DomainResource dm = (DomainResource) par.getResource();
            System.out.println("type of dm" + dm);
            if(dm.getClass() == ClaimResponse.class){
                System.out.println("index " + i);
                if(entity.equals("preauth")){
                    ((ClaimResponse) dm).setUse(ClaimResponse.Use.PREAUTHORIZATION);
                }
                ((ClaimResponse) dm).getTotal().set(0,new ClaimResponse.TotalComponent().setCategory(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/adjudication").setCode("benefit"))).setAmount(new Money().setValue((int) requestBody.getOrDefault("approved_amount", 0)).setCurrency("INR")));
            }
        }
        return p.encodeResourceToString(newBundle);
    }

    private static String getRejectedClaimBundle(String entity, String type, String respfhir) {
        IParser p = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
        Bundle newBundle = p.parseResource(Bundle.class, respfhir);
        for(int i=0; i < newBundle.getEntry().size(); i++){
            Bundle.BundleEntryComponent par = newBundle.getEntry().get(i);
            DomainResource dm = (DomainResource) par.getResource();
            System.out.println("type of dm" + dm);
            if(dm.getClass() == ClaimResponse.class){
                System.out.println("index " + i);
                if(entity.equals("preauth")){
                    ((ClaimResponse) dm).setUse(ClaimResponse.Use.PREAUTHORIZATION);
                }
                ((ClaimResponse) dm).getError().add(new ClaimResponse.ErrorComponent(new CodeableConcept(new Coding().setSystem("http://hcxprotocol.io/codes/claim-error-codes").setCode("AUTH-001").setDisplay(StringUtils.capitalize(type) + " adjudication failed"))));
            }
        }
        return p.encodeResourceToString(newBundle);
    }

    private String getStatus(Map<String,Object> addInfo){
        String status = PENDING;
        Set<String> statuses = new HashSet<>();
        for (Map.Entry<String, Object> entry : addInfo.entrySet()) {
            String objStatus = ((Map<String, Object>) entry.getValue()).getOrDefault("status","").toString();
            statuses.add(objStatus);
        }
        if(statuses.contains(REJECTED)) {
            status = REJECTED;
        } else if (statuses.contains(PENDING)) {
            status = PENDING;
        } else if (statuses.contains(APPROVED)) {
            status = APPROVED;
        }
        return status;
    }

}