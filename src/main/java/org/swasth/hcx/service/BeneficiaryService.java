package org.swasth.hcx.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.swasth.hcx.exception.ClientException;
import org.swasth.hcx.exception.ErrorCodes;
import org.swasth.hcx.utils.Constants;
import org.swasth.hcx.utils.JSONUtils;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Service
public class BeneficiaryService {
    @Value("${postgres.table.payerData}")
    private String payorDataTable;
    @Autowired
    private PostgresService postgresService;
    @Value("${aws-url.bucketName}")
    private String bucketName;
    @Value("${postgres.table.beneficiary}")
    private String beneficiaryTable;
    @Value("${otp.expiry}")
    private int otpExpiry;
    @Value("${otp.send-per-minute}")
    private int otpSendPerMinute;
    @Autowired
    private CloudStorageClient cloudStorageClient;
    @Autowired
    private SMSService smsService;
    long lastOTPSendTime = 0;
    int otpSentThisMinute = 0;

    public void sendOTP(String mobile, String phoneContent) throws ClientException, SQLException {
        if (!isRateLimited()) {
            try {
                int otpCode = 100_000 + new Random().nextInt(900_000);
                String query = String.format("SELECT * FROM %s WHERE mobile = '%s'", beneficiaryTable, mobile);
                ResultSet resultSet = postgresService.executeQuery(query);
                if (!resultSet.next()) {
                    String beneficiaryReferenceId = String.valueOf(UUID.randomUUID());
                    String insertQuery = String.format("INSERT INTO %s (mobile, otp_code, mobile_verified, createdon, otp_expiry, bsp_reference_id) VALUES ('%s', %d, false, %d, %d, '%s')", beneficiaryTable, mobile, otpCode, System.currentTimeMillis(), System.currentTimeMillis() + otpExpiry, beneficiaryReferenceId);
                    postgresService.execute(insertQuery);
                    smsService.sendSMS(mobile, phoneContent + "\r\n" + otpCode);
                    System.out.println("OTP sent successfully for " + mobile);
                } else {
                    String updateQuery = String.format("UPDATE %s SET otp_code = %d, otp_expiry = %d , mobile_verified = %b WHERE mobile = '%s'", beneficiaryTable, otpCode, System.currentTimeMillis() + otpExpiry, false, mobile);
                    postgresService.execute(updateQuery);
                    smsService.sendSMS(mobile, phoneContent + "\r\n" + otpCode);
                    System.out.println("OTP sent successfully for " + mobile);
                }
                lastOTPSendTime = System.currentTimeMillis();
                otpSentThisMinute++;
            } catch (ClientException e) {
                throw new ClientException(e.getMessage());
            }
        }
    }

    private boolean isRateLimited() throws ClientException {
        if (System.currentTimeMillis() - lastOTPSendTime >= 60000) {
            otpSentThisMinute = 0; // Reset the count if a minute has passed
        }
        if (otpSentThisMinute >= otpSendPerMinute) {
            System.out.println("Rate limit exceeded. Try again later.");
            throw new ClientException(ErrorCodes.ERR_SENDING_OTP, "Rate limit exceeded. Try again later.");
        }
        return false;
    }

    public ResponseEntity<Object> verifyOTP(Map<String, Object> requestBody) {
        try {
            String mobile = (String) requestBody.get(Constants.MOBILE);
            int userEnteredOTP = Integer.parseInt((String) requestBody.get("otp_code"));
            String query = String.format("SELECT * FROM %s WHERE mobile = '%s'", beneficiaryTable, mobile);
            ResultSet resultSet = postgresService.executeQuery(query);
            if (resultSet.next()) {
                boolean isMobileVerified = resultSet.getBoolean("mobile_verified");
                int storedOTP = resultSet.getInt("otp_code");
                long otpExpire = resultSet.getLong("otp_expiry");
                if (isMobileVerified) {
                    return ResponseEntity.badRequest().body(response("Mobile Number already verified", mobile, "failed"));
                }
                if (userEnteredOTP != storedOTP || System.currentTimeMillis() > otpExpire) {
                    throw new io.hcxprotocol.exception.ClientException("Invalid OTP or OTP has expired");
                }
                // Update mobile_verified status
                String updateQuery = String.format("UPDATE %s SET mobile_verified = true WHERE mobile = '%s'", beneficiaryTable, mobile);
                postgresService.execute(updateQuery);
                return ResponseEntity.ok().body(response("verification is successful", mobile, "successful"));
            } else {
                return ResponseEntity.badRequest().body(response("Record does not exist in the database", mobile, "failed"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    public Map<String, Object> response(String message, String mobile, String verification) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("mobile", mobile);
        response.put("verification", verification);
        return response;
    }

    public Map<String, Object> getPayloadMap(String requestID) throws ClientException, SQLException {
        String searchQuery = String.format("SELECT * FROM %s WHERE request_id = '%s'", payorDataTable, requestID);
        ResultSet resultSet = postgresService.executeQuery(searchQuery);
        if (!resultSet.next()) {
            throw new ClientException("Request does not exist in the database");
        }
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("request_fhir", resultSet.getString("request_fhir"));
        payloadMap.put("correlation_id", resultSet.getString("correlation_id"));
        payloadMap.put("workflow_id", resultSet.getString("workflow_id"));
        return payloadMap;
    }


    public ResponseEntity<Object> getRequestListFromDatabase(Map<String, Object> requestBody) {
        String mobile = (String) requestBody.getOrDefault("mobile", "");
        String app = (String) requestBody.getOrDefault("app", "");
        Map<String, Object> resp = new HashMap<>();
        Map<String, List<Map<String, Object>>> groupedEntries = new HashMap<>();
        String searchQuery = String.format("SELECT * FROM %s WHERE mobile = '%s' AND app = '%s' ORDER BY created_on DESC", payorDataTable, mobile, app);
        try (ResultSet searchResultSet = postgresService.executeQuery(searchQuery)) {
            while (!searchResultSet.isClosed() && searchResultSet.next()) {
                String workflowId = searchResultSet.getString("workflow_id");
                if (!groupedEntries.containsKey(workflowId)) {
                    groupedEntries.put(workflowId, new ArrayList<>());
                }
                Map<String, Object> responseMap = new HashMap<>();
                String actionType = searchResultSet.getString("action");
                if (actionType.equalsIgnoreCase("claim") || actionType.equalsIgnoreCase("preauth")) {
                    String supportingDocuments = searchResultSet.getString("supporting_documents");
                    responseMap.put("supportingDocuments", JSONUtils.deserialize(supportingDocuments, Map.class));
                    responseMap.put("billAmount", searchResultSet.getString("bill_amount"));
                    responseMap.put("otpStatus", searchResultSet.getString("otp_verification"));
                    responseMap.put("bankStatus", searchResultSet.getString("bank_details"));
                    responseMap.put("additionalInfo", searchResultSet.getString("additional_info"));
                    responseMap.put("accountNumber", searchResultSet.getString("account_number"));
                    responseMap.put("ifscCode", searchResultSet.getString("ifsc_code"));
                }
                responseMap.put("type", actionType);
                responseMap.put("status", searchResultSet.getString("status"));
                responseMap.put("apiCallId", searchResultSet.getString("request_id"));
                responseMap.put("claimType", "OPD");
                responseMap.put("date", searchResultSet.getString("created_on"));
                responseMap.put("insurance_id", searchResultSet.getString("insurance_id"));
                responseMap.put("correlationId", searchResultSet.getString("correlation_id"));
                responseMap.put("sender_code", searchResultSet.getString("sender_code"));
                responseMap.put("recipient_code", searchResultSet.getString("recipient_code"));
                responseMap.put("workflow_id", workflowId);
                responseMap.put("mobile", searchResultSet.getString("mobile"));
                responseMap.put("patientName", searchResultSet.getString("patient_name"));
                groupedEntries.get(workflowId).add(responseMap);
                if (groupedEntries.size() >= 10) {
                    break;
                }
            }
            List<Map<String, Object>> entries = new ArrayList<>();
            for (String key : groupedEntries.keySet()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put(key, groupedEntries.get(key));
                entries.add(entry);
            }
            resp.put("entries", entries);
            resp.put("count", entries.size());
            return new ResponseEntity<>(resp, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace(); // Log the exception for debugging
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<Object> getRequestListFromSenderCode(Map<String, Object> requestBody) throws Exception {
        String senderCode = (String) requestBody.getOrDefault("sender_code", "");
        String app = (String) requestBody.getOrDefault("app", "");
        Map<String, Object> resp = new HashMap<>();
        Map<String, List<Map<String, Object>>> groupedEntries = new HashMap<>();
        String searchQuery = String.format("SELECT * FROM %s WHERE sender_code = '%s' AND app = '%s' ORDER BY created_on DESC", payorDataTable, senderCode, app);
        try (ResultSet searchResultSet = postgresService.executeQuery(searchQuery)) {
            while (!searchResultSet.isClosed() && searchResultSet.next()) {
                String workflowId = searchResultSet.getString("workflow_id");
                if (!groupedEntries.containsKey(workflowId)) {
                    groupedEntries.put(workflowId, new ArrayList<>());
                }
                Map<String, Object> responseMap = new HashMap<>();
                String actionType = searchResultSet.getString("action");
                if (actionType.equalsIgnoreCase("claim") || actionType.equalsIgnoreCase("preauth")) {
                    String supportingDocuments = searchResultSet.getString("supporting_documents");
                    responseMap.put("supportingDocuments", JSONUtils.deserialize(supportingDocuments, Map.class));
                    responseMap.put("billAmount", searchResultSet.getString("bill_amount"));
                    responseMap.put("otpStatus", searchResultSet.getString("otp_verification"));
                    responseMap.put("bankStatus", searchResultSet.getString("bank_details"));
                    responseMap.put("additionalInfo", searchResultSet.getString("additional_info"));
                    responseMap.put("accountNumber", searchResultSet.getString("account_number"));
                    responseMap.put("ifscCode", searchResultSet.getString("ifsc_code"));
                }
                responseMap.put("type", actionType);
                responseMap.put("status", searchResultSet.getString("status"));
                responseMap.put("apiCallId", searchResultSet.getString("request_id"));
                responseMap.put("claimType", "OPD");
                responseMap.put("date", searchResultSet.getString("created_on"));
                responseMap.put("insurance_id", searchResultSet.getString("insurance_id"));
                responseMap.put("correlationId", searchResultSet.getString("correlation_id"));
                responseMap.put("sender_code", searchResultSet.getString("sender_code"));
                responseMap.put("recipient_code", searchResultSet.getString("recipient_code"));
                responseMap.put("workflow_id", workflowId);
                responseMap.put("mobile", searchResultSet.getString("mobile"));
                responseMap.put("patientName", searchResultSet.getString("patient_name"));
                groupedEntries.get(workflowId).add(responseMap);
                if (groupedEntries.size() >= 10) {
                    break;
                }
            }
            List<Map<String, Object>> entries = new ArrayList<>();
            for (String key : groupedEntries.keySet()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put(key, groupedEntries.get(key));
                entries.add(entry);
            }
            resp.put("entries", entries);
            resp.put("count", entries.size());
            return new ResponseEntity<>(resp, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace(); // Log the exception for debugging
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<Object> getDataFromWorkflowId(Map<String, Object> requestBody) {
        String workflowId = (String) requestBody.getOrDefault("workflow_id", "");
        String app = (String) requestBody.getOrDefault("app", "");
        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Object> resp = new HashMap<>();
        String searchQuery = String.format("SELECT * FROM %s WHERE workflow_id = '%s' AND (action = 'claim' OR action = 'preauth') AND app = '%s' ORDER BY created_on ASC", payorDataTable, workflowId, app);
        try (ResultSet searchResultSet = postgresService.executeQuery(searchQuery)) {
            while (!searchResultSet.isClosed() && searchResultSet.next()) {
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("type", getType(searchResultSet.getString("action")));
                responseMap.put("status", searchResultSet.getString("status"));
                responseMap.put("apiCallId", searchResultSet.getString("request_id"));
                responseMap.put("claimType", "OPD");
                responseMap.put("date", searchResultSet.getString("created_on"));
                responseMap.put("correlationId", searchResultSet.getString("correlation_id"));
                responseMap.put("sender_code", searchResultSet.getString("sender_code"));
                responseMap.put("recipient_code", searchResultSet.getString("recipient_code"));
                responseMap.put("billAmount", searchResultSet.getString("bill_amount"));
                String supportingDocuments = searchResultSet.getString("supporting_documents");
                responseMap.put("supportingDocuments", JSONUtils.deserialize(supportingDocuments, Map.class));
                responseMap.put("mobile", searchResultSet.getString("mobile"));
                responseMap.put("patientName", searchResultSet.getString("patient_name"));
                responseMap.put("otpStatus", searchResultSet.getString("otp_verification"));
                responseMap.put("bankStatus", searchResultSet.getString("bank_details"));
                responseMap.put("additionalInfo", searchResultSet.getString("additional_info"));
                responseMap.put("accountNumber", searchResultSet.getString("account_number"));
                responseMap.put("ifscCode", searchResultSet.getString("ifsc_code"));
                entries.add(responseMap);
            }
            resp.put("entries", entries);
            return new ResponseEntity<>(resp, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace(); // Log the exception for debugging
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String getType(String action) {
        if (Constants.CLAIM.equalsIgnoreCase(action)) {
            return Constants.CLAIM;
        } else {
            return Constants.PRE_AUTH;
        }
    }

    public List<Map<String, Object>> getDocumentUrls(List<MultipartFile> files, String mobile) throws ClientException, SQLException, IOException {
        if (isRateLimited()) {
            throw new ClientException("Rate limit exceeded. Please try again later.");
        }
        String query = String.format("SELECT bsp_reference_id FROM %s WHERE mobile = '%s'", beneficiaryTable, mobile);
        String beneficiaryReferenceId = "";
        try (ResultSet resultSet = postgresService.executeQuery(query)) {
            while (resultSet.next()) {
                beneficiaryReferenceId = resultSet.getString("bsp_reference_id");
            }
        }
        List<Map<String, Object>> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            String pathToFile = String.format("beneficiary-app/%s/%s", beneficiaryReferenceId, fileName);
            cloudStorageClient.putObject(bucketName, pathToFile, file);
            Map<String, Object> response = new HashMap<>();
            response.put("url", cloudStorageClient.getUrl(bucketName, pathToFile).toString());
            response.put("reference_id", beneficiaryReferenceId);
            responses.add(response);
        }
        lastOTPSendTime = System.currentTimeMillis();
        otpSentThisMinute++;
        return responses;
    }

    public Map<String, Object> checkCommunicationRequest(Map<String, Object> requestBody) throws ClientException, SQLException {
        String requestId = (String) requestBody.get("request_id");
        String query = String.format("SELECT otp_verification,bank_details FROM %s WHERE request_id = '%s'", payorDataTable, requestId);
        ResultSet resultSet = postgresService.executeQuery(query);
        Map<String, Object> status = new HashMap<>();
        if (!resultSet.next()) {
           throw new ClientException("Claim Request Id Does not exist in the database");
        }
        status.put("otpStatus", resultSet.getString("otp_verification"));
        status.put("bankStatus", resultSet.getString("bank_details"));
        return status;
    }
}

