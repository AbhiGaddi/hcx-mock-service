package org.swasth.hcx.controllers;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import io.hcxprotocol.init.HCXIntegrator;
import io.hcxprotocol.utils.Operations;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.swasth.hcx.dto.Request;
import org.swasth.hcx.dto.Response;
import org.swasth.hcx.dto.ResponseError;
import org.swasth.hcx.exception.ClientException;
import org.swasth.hcx.exception.ErrorCodes;
import org.swasth.hcx.exception.ServerException;
import org.swasth.hcx.exception.ServiceUnavailbleException;
import org.swasth.hcx.fhirexamples.OnActionFhirExamples;
import org.swasth.hcx.service.*;
import org.swasth.hcx.utils.JSONUtils;
import org.swasth.hcx.utils.OnActionCall;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.*;

import static org.swasth.hcx.utils.Constants.*;

public class BaseController {
    @Autowired
    protected OnActionCall onActionCall;
    @Autowired
    private PostgresService postgresService;
    @Autowired
    protected Environment env;
    @Autowired
    protected HeaderAuditService auditService;
    @Autowired
    protected NotificationService notificationService;
    @Autowired
    protected HcxIntegratorService hcxIntegratorService;
    @Autowired
    private PayerService payerService;
    @Autowired
    private RedisService redisService;
    @Autowired
    private PostgresService postgres;
    @Value("${autoresponse}")
    private Boolean autoResponse;
    @Value("${postgres.table.payerData}")
    private String table;
    @Value("${beneficiary.participant-code}")
    private String beneficiaryParticipantCode;
    @Value("${beneficiary.username}")
    private String beneficiaryUsername;
    @Value("${beneficiary.password}")
    private String beneficiaryPassword;
    @Value("${beneficiary.recipient-code}")
    private String mockRecipientCode;
    @Value("${redis.expires}")
    private int redisExpires;
    IParser parser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);

    protected void processAndValidate(String onApiAction, Request request, Map<String, Object> requestBody, String apiAction) throws Exception {
        String mid = UUID.randomUUID().toString();
        String serviceMode = env.getProperty(SERVICE_MODE);
        System.out.println("\n" + "Mode: " + serviceMode + " :: mid: " + mid + " :: Event: " + onApiAction);
        if (StringUtils.equalsIgnoreCase(serviceMode, GATEWAY)) {
            Map<String, String> payload = new HashMap<>();
            System.out.println("payload received " + requestBody);
            payload.put("payload", String.valueOf(requestBody.get("payload")));
            Map<String, Object> output = new HashMap<>();
            Map<String, Object> outputOfOnAction = new HashMap<>();
            Bundle bundle = new Bundle();
            Request req = new Request(requestBody, apiAction);
            HCXIntegrator hcxIntegrator = hcxIntegratorService.getHCXIntegrator(req.getRecipientCode());
            switch (apiAction) {
                case COVERAGE_ELIGIBILITY_CHECK:
                    coverageEligibilityProcessing(requestBody, apiAction, payload, output, outputOfOnAction, hcxIntegrator);
                    break;
                case CLAIM_SUBMIT:
                case PRE_AUTH_SUBMIT:
                    claimOrPreAuthProcessing(requestBody, apiAction, payload, output, outputOfOnAction, hcxIntegrator);
                    break;
                case COMMUNICATION_REQUEST:
                    communicationProcessing(request, requestBody, apiAction, payload, output, outputOfOnAction, bundle);
                    break;
                case NOTIFICATION_NOTIFY:
                    notificationProcessing(request, output, hcxIntegrator);
                    break;
                default:
                    throw new ClientException("Action " + apiAction + " does not exist.");
            }
        }
    }

    private void notificationProcessing(Request request, Map<String, Object> output, HCXIntegrator hcxIntegrator) throws Exception {
        hcxIntegrator.receiveNotification(request.getNotificationRequest(), output);
        String topicCode = request.getTopicCode();
        Map<String, Object> notificationHeaders = request.getNotificationHeaders();
        String recipientType = (String) notificationHeaders.getOrDefault("recipient_type", "");
        List<String> recipients = (List<String>) notificationHeaders.getOrDefault("recipients", "");
        for (String recipient : recipients) {
            String key = recipient + ":" + topicCode;
            if (StringUtils.equalsIgnoreCase(recipientType, "participant_role") || StringUtils.equalsIgnoreCase(recipientType, "participant_code")) {
                redisService.set(key, notificationService.notificationResponse(output), redisExpires);
            }
        }
    }

    private void communicationProcessing(Request request, Map<String, Object> requestBody, String apiAction, Map<String, String> pay, Map<String, Object> output, Map<String, Object> outputOfOnAction, Bundle bundle) throws Exception {
        HCXIntegrator hcxIntegrator1 = HCXIntegrator.getInstance(initializingConfigMap());
        boolean result = hcxIntegrator1.processIncoming(JSONUtils.serialize(pay), Operations.COMMUNICATION_REQUEST, output);
        if (!result) {
            System.out.println("Error while processing incoming request: " + output);
        }
        System.out.println("output map after decryption communication" + output);
        System.out.println("decryption is successful");
        String selectQuery = String.format("SELECT otp_verification from %s WHERE action = 'claim' AND correlation_id = '%s'", table, request.getCorrelationId());
        ResultSet resultSet = postgresService.executeQuery(selectQuery);
        String otpVerification = "";
        while (resultSet.next()) {
            otpVerification = resultSet.getString("otp_verification");
        }
        if (StringUtils.equalsIgnoreCase(otpVerification, "successful")) {
            String query1 = String.format("UPDATE %s SET bank_details = '%s' WHERE correlation_id = '%s'", table, "initiated", request.getCorrelationId());
            postgresService.execute(query1);
        } else if (StringUtils.equalsIgnoreCase(otpVerification, "Pending")) {
            String query = String.format("UPDATE %s SET otp_verification = '%s' WHERE correlation_id ='%s'", table, "initiated", request.getCorrelationId());
            postgresService.execute(query);
        }
        sendResponse(apiAction, parser.encodeResourceToString(bundle), (String) output.get("fhirPayload"), Operations.COMMUNICATION_ON_REQUEST, String.valueOf(requestBody.get("payload")), "response.complete", outputOfOnAction);
    }

    private void coverageEligibilityProcessing(Map<String, Object> requestBody, String apiAction, Map<String, String> pay, Map<String, Object> output, Map<String, Object> outputOfOnAction, HCXIntegrator hcxIntegrator) throws Exception {
        try {
            Bundle bundle;
            boolean result = hcxIntegrator.processIncoming(JSONUtils.serialize(pay), Operations.COVERAGE_ELIGIBILITY_CHECK, output);
            if (!result) {
                System.out.println("Error while processing incoming coverage eligibility request: " + output);
            }
            System.out.println("output map after decryption  coverageEligibility" + output.get("fhirPayload"));
            System.out.println("decryption successful");
            bundle = parser.parseResource(Bundle.class, (String) output.get("fhirPayload"));
            CoverageEligibilityResponse covRes = OnActionFhirExamples.coverageEligibilityResponseExample();
            covRes.setPatient(new Reference("Patient/RVH1003"));
            replaceResourceInBundleEntry(bundle, "https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-CoverageEligibilityResponseBundle.html", CoverageEligibilityRequest.class, new Bundle.BundleEntryComponent().setFullUrl(covRes.getResourceType() + "/" + covRes.getId().toString().replace("#", "")).setResource(covRes));
            System.out.println("bundle reply " + parser.encodeResourceToString(bundle));
            //sending the onAction call
            sendResponse(apiAction, parser.encodeResourceToString(bundle), (String) output.get("fhirPayload"), Operations.COVERAGE_ELIGIBILITY_ON_CHECK, String.valueOf(requestBody.get("payload")), "response.complete", outputOfOnAction);
        } catch (Exception e) {
            System.out.println("Error while processing the coverage eligibility request --- " + e.getMessage());
            throw new ClientException("Error while processing the coverage eligibility request");
        }
    }

    private void claimOrPreAuthProcessing(Map<String, Object> requestBody, String apiAction, Map<String, String> pay, Map<String, Object> output, Map<String, Object> outputOfOnAction, HCXIntegrator hcxIntegrator) throws Exception {
        Bundle bundle;
        boolean result = hcxIntegrator.processIncoming(JSONUtils.serialize(pay), Operations.CLAIM_SUBMIT, output);
        if (!result) {
            System.out.println("Error while processing incoming claim request: " + output);
        }
        System.out.println("output map after decryption claim " + output);
        System.out.println("decryption successful");
        //processing the decrypted incoming bundle
        bundle = parser.parseResource(Bundle.class, (String) output.get("fhirPayload"));
        ClaimResponse claimRes = OnActionFhirExamples.claimResponseExample();
        claimRes.setPatient(new Reference("Patient/RVH1003"));
        replaceResourceInBundleEntry(bundle, "https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-ClaimResponseBundle.html", Claim.class, new Bundle.BundleEntryComponent().setFullUrl(claimRes.getResourceType() + "/" + claimRes.getId().replace("#", "")).setResource(claimRes));
        System.out.println("bundle reply " + parser.encodeResourceToString(bundle));
        sendResponse(apiAction, parser.encodeResourceToString(bundle), (String) output.get("fhirPayload"), Operations.CLAIM_ON_SUBMIT, String.valueOf(requestBody.get("payload")), "response.complete", outputOfOnAction);
    }

    private void sendResponse(String apiAction, String respFhir, String reqFhir, Operations operation, String actionJwe, String onActionStatus, Map<String, Object> output) throws Exception {
        Request request = new Request(Collections.singletonMap("payload", actionJwe), apiAction);
        if (autoResponse || StringUtils.equalsIgnoreCase(request.getRecipientCode(), env.getProperty("mock_payer.participant_code"))) {
            onActionCall.sendOnAction(request.getRecipientCode(), respFhir, operation, actionJwe, onActionStatus, output);
        } else {
            payerService.process(request, reqFhir, respFhir);
            if (request.getAction().equalsIgnoreCase("/v0.7/coverageeligibility/check") && request.getRecipientCode().equalsIgnoreCase(mockRecipientCode)) {
                Thread.sleep(1000);
                onActionCall.sendOnAction(request.getRecipientCode(), respFhir, Operations.COVERAGE_ELIGIBILITY_ON_CHECK, actionJwe, "response.complete", output);
                String updateQuery = String.format("UPDATE %s SET status='%s',updated_on=%d WHERE request_id='%s' RETURNING %s,%s",
                        table, "Approved", System.currentTimeMillis(), request.getApiCallId(), "raw_payload", "response_fhir");
                postgres.execute(updateQuery);
            }
        }
    }

    public ResponseEntity<Object> processRequest(Map<String, Object> requestBody, String apiAction, String onApiAction, String kafkaTopic) {
        Response response = new Response();
        try {
            Request request = new Request(requestBody, apiAction);
            setResponseParams(request, response);
            processAndValidate(onApiAction, request, requestBody, apiAction);
            System.out.println("http respond sent");
            return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error   " + e);
            return exceptionHandler(response, e);
        }
    }

    public void processAndValidateRequest(String onApiAction, Request request, Map<String, Object> requestBody, String apiAction) throws Exception {
        String mid = UUID.randomUUID().toString();
        String serviceMode = env.getProperty(SERVICE_MODE);
        System.out.println("\n" + "Mode: " + serviceMode + " :: mid: " + mid + " :: Event: " + onApiAction);
        if (StringUtils.equalsIgnoreCase(serviceMode, GATEWAY)) {
            Map<String, String> pay = new HashMap<>();
            System.out.println("payload received " + requestBody);
            pay.put("payload", String.valueOf(requestBody.get("payload")));
            Map<String, Object> output = new HashMap<>();
            System.out.println("create the oncheck payload");
            Bundle bundle;
            HCXIntegrator hcxIntegrator = HCXIntegrator.getInstance(initializingConfigMap());
            if (COVERAGE_ELIGIBILITY_ONCHECK.equalsIgnoreCase(onApiAction)) {
                boolean result = hcxIntegrator.processIncoming(JSONUtils.serialize(pay), Operations.COVERAGE_ELIGIBILITY_ON_CHECK, output);
                if (!result) {
                    System.out.println("Error while processing incoming request: " + output);
                }
                System.out.println("output map after decryption  coverageEligibility" + output.get("fhirPayload"));
                System.out.println("decryption successful");
                //processing the decrypted incoming bundle
                bundle = parser.parseResource(Bundle.class, (String) output.get("fhirPayload"));
                CoverageEligibilityResponse covRes = OnActionFhirExamples.coverageEligibilityResponseExample();
                covRes.setPatient(new Reference("Patient/RVH1003"));
                replaceResourceInBundleEntry(bundle, "https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-CoverageEligibilityResponseBundle.html", CoverageEligibilityRequest.class, new Bundle.BundleEntryComponent().setFullUrl(covRes.getResourceType() + "/" + covRes.getId().toString().replace("#", "")).setResource(covRes));
                System.out.println("bundle reply " + parser.encodeResourceToString(bundle));
            } else if (COMMUNICATION_ONREQUEST.equalsIgnoreCase(onApiAction)) {
                HCXIntegrator hcxIntegrator1 = hcxIntegratorService.getHCXIntegrator(request.getRecipientCode());
                boolean result = hcxIntegrator1.processIncoming(JSONUtils.serialize(pay), Operations.COMMUNICATION_ON_REQUEST, output);
                if (!result) {
                    System.out.println("Error while processing incoming request: " + output);
                }
                System.out.println("output map after decryption communication" + output);
                System.out.println("decryption successful");
            }
        }
    }

    public ResponseEntity<Object> processAndValidateRequest(Map<String, Object> requestBody, String apiAction, String onApiAction, String kafkaTopic) {
        Response response = new Response();
        try {
            Request request = new Request(requestBody, apiAction);
            setResponseParams(request, response);
            processAndValidateRequest(onApiAction, request, requestBody, apiAction);
            System.out.println("http respond sent");
            return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error   " + e);
            return exceptionHandler(response, e);
        }
    }

    protected void setResponseParams(Request request, Response response) {
        response.setCorrelationId(request.getCorrelationId());
        response.setApiCallId(request.getApiCallId());
    }

    protected ResponseEntity<Object> exceptionHandler(Response response, Exception e) {
        e.printStackTrace();
        if (e instanceof ClientException) {
            return new ResponseEntity<>(errorResponse(response, ((ClientException) e).getErrCode(), e), HttpStatus.BAD_REQUEST);
        } else if (e instanceof ServiceUnavailbleException) {
            return new ResponseEntity<>(errorResponse(response, ((ServiceUnavailbleException) e).getErrCode(), e), HttpStatus.SERVICE_UNAVAILABLE);
        } else if (e instanceof ServerException) {
            return new ResponseEntity<>(errorResponse(response, ((ServerException) e).getErrCode(), e), HttpStatus.INTERNAL_SERVER_ERROR);
        } else {
            return new ResponseEntity<>(errorResponse(response, ErrorCodes.INTERNAL_SERVER_ERROR, e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    protected void validateStr(String field, String value) throws ClientException {
        if (StringUtils.isEmpty(value))
            throw new ClientException("Missing required field " + field);
    }

    protected void validateMap(String field, Map<String, Object> value) throws ClientException {
        if (MapUtils.isEmpty(value))
            throw new ClientException("Missing required field " + field);
    }

    public Map<String, Object> initializingConfigMap() throws IOException {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("protocolBasePath", "https://dev-hcx.swasth.app/api/v0.8");
        configMap.put("participantCode", beneficiaryParticipantCode);
        configMap.put("username", beneficiaryUsername);
        configMap.put("password", beneficiaryPassword);
        String keyUrl = "https://raw.githubusercontent.com/Swasth-Digital-Health-Foundation/hcx-platform/main/hcx-apis/src/test/resources/examples/test-keys/private-key.pem";
        String certificate = IOUtils.toString(new URL(keyUrl), StandardCharsets.UTF_8);
        configMap.put("encryptionPrivateKey", certificate);
        configMap.put("signingPrivateKey",  certificate);
        return configMap;
    }

    protected Response errorResponse(Response response, ErrorCodes code, java.lang.Exception e) {
        ResponseError error = new ResponseError(code, e.getMessage(), e.getCause());
        response.setError(error);
        return response;
    }

    protected void replaceResourceInBundleEntry(Bundle bundle, String bundleURL, Class matchClass, Bundle.BundleEntryComponent bundleEntry) {
        //updating the meta
        Meta meta = new Meta();
        meta.getProfile().add(new CanonicalType(bundleURL));
        meta.setLastUpdated(new Date());
        bundle.setMeta(meta);
        for (int i = 0; i < bundle.getEntry().size(); i++) {
            System.out.println("in the loop " + i);
            Bundle.BundleEntryComponent par = bundle.getEntry().get(i);
            DomainResource dm = (DomainResource) par.getResource();
            if (dm.getClass() == matchClass) {
                bundle.getEntry().set(i, bundleEntry);
            }
        }
    }
}
