package org.swasth.hcx.controllers.v1;

import io.hcxprotocol.utils.Operations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.swasth.hcx.controllers.BaseController;
import org.swasth.hcx.exception.ClientException;
import org.swasth.hcx.service.BeneficiaryService;
import org.swasth.hcx.service.CloudStorageClient;
import org.swasth.hcx.service.GenerateOutgoingRequest;
import org.swasth.hcx.utils.Constants;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.swasth.hcx.utils.Constants.*;

@RestController
@RequestMapping(Constants.VERSION_PREFIX)
public class BeneficiaryController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(BeneficiaryController.class);
    @Autowired
    private GenerateOutgoingRequest outgoingRequest;

    @Autowired
    private CloudStorageClient cloudStorageClient;
    @Autowired
    private BeneficiaryService beneficiaryService;
    @Value("${phone.beneficiary-register}")
    private String beneficiaryRegisterContent;

    @PostMapping(CREATE_COVERAGEELIGIBILITY_REQUEST)
    public ResponseEntity<Object> createCoverageEligibility(@RequestHeader HttpHeaders headers, @RequestBody Map<String, Object> requestBody) throws Exception {
        return outgoingRequest.createCoverageEligibilityRequest(requestBody,Operations.COVERAGE_ELIGIBILITY_CHECK);
    }

    @PostMapping(CREATE_CLAIM_SUBMIT)
    public ResponseEntity<Object> createClaimSubmit(@RequestBody Map<String, Object> requestBody) {
        return outgoingRequest.createClaimRequest(requestBody, Operations.CLAIM_SUBMIT);
    }

    @PostMapping(CREATE_PRE_AUTH_SUBMIT)
    public ResponseEntity<Object> createPreAuthSubmit(@RequestBody Map<String, Object> requestBody) {
        return outgoingRequest.createClaimRequest(requestBody, Operations.PRE_AUTH_SUBMIT);
    }

    @PostMapping(CREATE_COMMUNICATION_REQUEST)
    public ResponseEntity<Object> createCommunication(@RequestBody Map<String, Object> requestBody) {
        return outgoingRequest.createCommunicationRequest(requestBody, Operations.COMMUNICATION_REQUEST);
    }

    @PostMapping(CREATE_COMMUNICATION_ON_REQUEST)
    public ResponseEntity<Object> createOnCommunication(@RequestBody Map<String, Object> requestBody) throws ClientException {
        return outgoingRequest.createCommunicationOnRequest(requestBody);
    }

    @PostMapping(BSP_REQUEST_LIST)
    public ResponseEntity<Object> requestList(@RequestBody Map<String,Object>  requestBody) throws Exception {
        if(requestBody.containsKey("mobile")){
          return beneficiaryService.getRequestListFromDatabase(requestBody);
        } else {
            return beneficiaryService.getDataFromWorkflowId(requestBody);
        }
    }

    @PostMapping(SEND_OTP)
    public ResponseEntity<Object> sendOTP(@RequestBody Map<String, Object> requestBody) {
        try {
            String mobile = (String) requestBody.get(MOBILE);
            beneficiaryService.sendOTP(mobile, beneficiaryRegisterContent);
            return ResponseEntity.ok(Map.of("message", "OTP sent successfully", "mobile", mobile));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping(VERIFY_OTP)
    public ResponseEntity<Object> verifyOTP(@RequestBody Map<String, Object> requestBody) {
        try {
            return beneficiaryService.verifyOTP(requestBody);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping(UPLOAD_DOCUMENTS)
    public ResponseEntity<Object> uploadDocuments(@RequestParam("file") List<MultipartFile> files, @RequestParam("mobile") String mobile) {
        try {
            List<Map<String, Object>> responses = beneficiaryService.getDocumentUrls(files, mobile);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/check/communication/request")
    public ResponseEntity<Object> checkCommunicationRequest(@RequestBody Map<String,Object> requestBody) throws ClientException, SQLException {
        System.out.println("-------------- request body ------------------" + requestBody);
        if (requestBody.isEmpty()) {
            throw new ClientException("Request body cannot be empty");
        }
        boolean isCommunicationInitiated = beneficiaryService.checkCommunicationRequest(requestBody);
        if (isCommunicationInitiated) {
            return ResponseEntity.ok("Communication is initiated");
        } else {
            return ResponseEntity.badRequest().body("Communication is not initiated");
        }
    }

}
