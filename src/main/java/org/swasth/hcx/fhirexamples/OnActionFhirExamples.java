package org.swasth.hcx.fhirexamples;

import org.hl7.fhir.r4.model.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class OnActionFhirExamples {

    public static CoverageEligibilityResponse coverageEligibilityResponseExample() {
        //Creating the coverage eligibility response
        CoverageEligibilityResponse covelires = new CoverageEligibilityResponse();
        covelires.setId(UUID.randomUUID().toString());
        Meta metaResponse = new Meta();
        metaResponse.getProfile().add(new CanonicalType("https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-CoverageEligibilityResponse.html"));
        metaResponse.setLastUpdated(new Date());
        covelires.setMeta(metaResponse);
        covelires.addIdentifier(new Identifier().setSystem("http://identifiersystem.com").setValue("IdentifierValue"));
        covelires.setStatus(CoverageEligibilityResponse.EligibilityResponseStatus.ACTIVE);
        covelires.setPatient(new Reference("Patient/RVH1003"));
        covelires.setCreated(new Date());
        covelires.setInsurer(new Reference("Organization/GICOFINDIA"));
        covelires.setRequest(new Reference("CoverageEligibilityRequest/dc82673b-8c71-48c2-8a17-16dcb3b035f6"));
        covelires.setRequestor(new Reference("Organization/WeMeanWell01"));
        covelires.setOutcome(Enumerations.RemittanceOutcome.COMPLETE);
        EnumFactory<CoverageEligibilityResponse.EligibilityResponsePurpose> fact = new CoverageEligibilityResponse.EligibilityResponsePurposeEnumFactory();
        covelires.setPurpose(List.of((Enumeration) new Enumeration<>(fact).setValue(CoverageEligibilityResponse.EligibilityResponsePurpose.BENEFITS)));
        return covelires;
    }

    public static ClaimResponse claimResponseExample(){
        //Creating Claim response
        ClaimResponse claimRes = new ClaimResponse();
        claimRes.setId(UUID.randomUUID().toString());
        Meta metaClaimRes = new Meta();
        metaClaimRes.getProfile().add(new CanonicalType("https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-ClaimResponse.html"));
        metaClaimRes.setLastUpdated(new Date());
        claimRes.setMeta(metaClaimRes);
        claimRes.setStatus(ClaimResponse.ClaimResponseStatus.ACTIVE);
        claimRes.addIdentifier(new Identifier().setSystem("http://identifiersystem.com").setValue(UUID.randomUUID().toString()));
        claimRes.setType(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/claim-type").setCode("institutional")));
        claimRes.setUse(ClaimResponse.Use.CLAIM);
        claimRes.setPatient(new Reference("Patient/RVH1003"));
        claimRes.setCreated(new Date());
        claimRes.setInsurer(new Reference("Organization/GICOFINDIA"));
        claimRes.setRequestor(new Reference("Organization/WeMeanWell01"));
        claimRes.setRequest(new Reference("Claim/CLAIM1"));
        claimRes.setOutcome(ClaimResponse.RemittanceOutcome.COMPLETE);
        claimRes.getTotal().add(new ClaimResponse.TotalComponent().setCategory(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/adjudication").setCode("benefit"))).setAmount(new Money().setValue(80000).setCurrency("INR")));
        //claimRes.getError().add(new ClaimResponse.ErrorComponent(new CodeableConcept(new Coding().setSystem("http://hcxprotocol.io/codes/claim-error-codes").setCode("AUTH-005").setDisplay("\tClaim information is inconsistent with pre-certified/authorized services"))));
        return claimRes;
    }

    public static CoverageEligibilityRequest coverageEligibilityRequestExample() {

        //Creating coverage eligibility request
        CoverageEligibilityRequest ce = new CoverageEligibilityRequest();
        ce.setId("dc82673b-8c71-48c2-8a17-16dcb3b035f6");
        Meta meta = new Meta();
        meta.getProfile().add(new CanonicalType("https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-CoverageEligibilityRequest.html"));
        ce.setMeta(meta);
        ce.getIdentifier().add(new Identifier().setValue("req_70e02576-f5f5-424f-b115-b5f1029704d4"));
        ce.setStatus(CoverageEligibilityRequest.EligibilityRequestStatus.ACTIVE);
        ce.setPriority(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/processpriority").setCode("normal")));
        String date_string = "2022-06-21";
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
        Date date;
        try {
            date = formatter.parse(date_string);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        ce.setServiced(new DateType().setValue(date));
        EnumFactory<CoverageEligibilityRequest.EligibilityRequestPurpose> fact = new CoverageEligibilityRequest.EligibilityRequestPurposeEnumFactory();
        ce.setPurpose(List.of((Enumeration) new Enumeration<>(fact).setValue(CoverageEligibilityRequest.EligibilityRequestPurpose.BENEFITS)));
        CoverageEligibilityRequest.DetailsComponent details = new CoverageEligibilityRequest.DetailsComponent();
        details.getDiagnosis().add(new CoverageEligibilityRequest.DiagnosisComponent().setDiagnosis(new CodeableConcept(new Coding().setSystem("https://irdai.gov.in/package-code").setCode("E906184").setDisplay("SINGLE INCISION LAPAROSCOPIC APPENDECTOMY")).setText("SINGLE INCISION LAPAROSCOPIC APPENDECTOMY")));
        details.setProductOrService(new CodeableConcept(new Coding().setCode("E101021").setSystem("https://irdai.gov.in/package-code").setDisplay("Twin Sharing Ac")).setText(" twin sharing basis room package"));
        ce.getItem().add(details);
        ce.setPatient(new Reference("Patient/RVH1003"));
        //ce.getServicedPeriod().setStart(new Date(System.currentTimeMillis())).setEnd(new Date(System.currentTimeMillis()));
        ce.setCreated(new Date(System.currentTimeMillis()));
        ce.setEnterer(new Reference("Practitioner/PractitionerViswasKar"));
        ce.setProvider(new Reference("Organization/GICOFINDIA"));
        ce.setInsurer(new Reference( "Organization/WeMeanWell01"));
        ce.setFacility(ce.getFacility().setReference("http://sgh.com.sa/Location/4461281"));
        ce.getInsurance().add(new CoverageEligibilityRequest.InsuranceComponent(new Reference("Coverage/COVERAGE1")));
        return ce;
    }

    public static Organization providerOrganizationExample(){
        //making the hospital org resource
        Organization hos = new Organization();
        hos.setId("WeMeanWell01");
        Meta metaorg1 = new Meta();
        metaorg1.getProfile().add(new CanonicalType("https://nrces.in/ndhm/fhir/r4/StructureDefinition/Organization"));
        hos.setMeta(metaorg1);
//        hos.setName("WeMeanWell Hospital");
        hos.getAddress().add(new Address().setText(" Bannerghatta Road, Bengaluru ").setCity("Bengaluru").setCountry("India"));
        hos.getIdentifier().add(new Identifier().setSystem("http://abdm.gov.in/facilities").setValue("HFR-ID-FOR-TMH").setType(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203").setCode("AC").setDisplay("Narayana"))));
        return hos;
    }

    public static Patient patientExample() {

        //making a Patient resource
        Patient pat = new Patient();
        pat.setId("RVH1003");
        Meta metapat = new Meta();
        metapat.getProfile().add(new CanonicalType("https://nrces.in/ndhm/fhir/r4/StructureDefinition/Patient"));
        pat.setMeta(metapat);
        pat.getIdentifier().add(new Identifier().setType(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203").setCode("SN").setDisplay("Subscriber Number"))).setSystem("http://gicofIndia.com/beneficiaries").setValue("BEN-101"));
        //pat.getIdentifier().add(new Identifier().setType(new CodeableConcept(new Coding().setSystem("https://nrces.in/ndhm/fhir/r4/CodeSystem/ndhm-identifier-type-code").setCode("PMJAY").setDisplay("Pradhan Mantri Jan Aarogya Yojana (PMJAY) ID"))).setValue("PMJAY-0101"));
        pat.setGender(Enumerations.AdministrativeGender.MALE);
//        pat.getName().add(new HumanName().setText("Prasidh Dixit"));
//        pat.getTelecom().add(new ContactPoint().setValue("9008496789").setSystem(ContactPoint.ContactPointSystem.PHONE));
        String date_string = "26-09-1960";
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
        Date date;
        try {
            date = formatter.parse(date_string);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        pat.setBirthDate(date);
        pat.addAddress((new Address().setText("#39 Kalena Agrahara, Kamanahalli, Bengaluru - 560056").setCity("Bengaluru").setPostalCode("560056").setState("Karnataka").setCountry("India")));
        return pat;
    }

    public static Claim claimExample(){
        //Creating the Claims request
        Claim claim = new Claim();
        claim.setId(UUID.randomUUID().toString());
        Meta metaClaim = new Meta();
        metaClaim.getProfile().add(new CanonicalType("https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-Claim.html"));
        metaClaim.setLastUpdated(new Date());
        claim.setMeta(metaClaim);
        claim.setStatus(org.hl7.fhir.r4.model.Claim.ClaimStatus.ACTIVE);
        claim.setType(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/claim-type").setCode("institutional")));
        claim.setUse(org.hl7.fhir.r4.model.Claim.Use.CLAIM);
        claim.setPatient(new Reference("Patient/RVH1003"));
        claim.addIdentifier(new Identifier().setSystem("http://identifiersystem.com").setValue("IdentifierValue"));
        claim.setCreated(new Date());
        claim.setInsurer(new Reference("Organization/GICOFINDIA"));
        claim.setProvider(new Reference("Organization/WeMeanWell01"));
        claim.setPriority(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/processpriority").setCode("normal")));
        claim.setPayee(new org.hl7.fhir.r4.model.Claim.PayeeComponent().setParty(new Reference("Organization/WeMeanWell01")).setType(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/payeetype").setCode("provider"))));
        claim.getCareTeam().add(new org.hl7.fhir.r4.model.Claim.CareTeamComponent().setSequence(4).setProvider(new Reference("Organization/WeMeanWell01")));
        claim.addInsurance(new org.hl7.fhir.r4.model.Claim.InsuranceComponent().setFocal(true).setCoverage(new Reference("Coverage/COVERAGE1")).setSequence(1));
        claim.getDiagnosis().add(new Claim.DiagnosisComponent().addType(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/ex-diagnosistype").setCode("admitting").setDisplay("Admitting Diagnosis"))).setSequence(1).setDiagnosis(new CodeableConcept(new Coding().setSystem("http://irdai.com").setCode("E906184").setDisplay("SINGLE INCISION LAPAROSCOPIC APPENDECTOMY")).setText("SINGLE INCISION LAPAROSCOPIC APPENDECTOMY")));
        claim.getDiagnosis().add(new Claim.DiagnosisComponent().addType(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/ex-diagnosistype").setCode("admitting").setDisplay("Admitting Diagnosis"))).setSequence(2).setDiagnosis(new Reference("Condition/1234")));
        //adding procedure items to the claims component
        // To check type is OPD
        claim.setSubType(new CodeableConcept(new Coding().setSystem("https://staging-hcx.swasth.app/hapi-fhir/fhir/CodeSystem/hcx-claim-sub-types").setCode("OPD")));
        //admission fees
        claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setCode("E101021").setSystem("https://irdai.gov.in/package-code").setDisplay("Twin Sharing Ac"))).setUnitPrice(new Money().setValue(100000).setCurrency("INR")));
        //tests
        claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setSystem("https://irdai.gov.in/package-code").setCode("E924260").setDisplay("CLINICAL TOXICOLOGY SCREEN, BLOOD"))).setUnitPrice(new Money().setValue(2000).setCurrency("INR")));
        claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setSystem("https://irdai.gov.in/package-code").setCode("E924261").setDisplay("CLINICAL TOXICOLOGY SCREEN,URINE"))).setUnitPrice(new Money().setValue(1000).setCurrency("INR")));
        claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setSystem("https://irdai.gov.in/package-code").setCode("E507029").setDisplay("ECG"))).setUnitPrice(new Money().setValue(5000).setCurrency("INR")));
        claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setSystem("https://irdai.gov.in/package-code").setCode("E6080377").setDisplay("UltraSound Abdomen"))).setUnitPrice(new Money().setValue(5000).setCurrency("INR")));

        //Consultation Medication and Hospital Charges
        claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setSystem("https://irdai.gov.in/package-code").setCode("502001").setDisplay("Surgeons Charges"))).setUnitPrice(new Money().setValue(1000).setCurrency("INR")));
        claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setSystem("https://irdai.gov.in/package-code").setCode("5020021").setDisplay("Anesthesiologist charges"))).setUnitPrice(new Money().setValue(1000).setCurrency("INR")));
        claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setSystem("https://irdai.gov.in/package-code").setCode("E6080373").setDisplay("Physician charges"))).setUnitPrice(new Money().setValue(1000).setCurrency("INR")));
        claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setSystem("https://irdai.gov.in/package-code").setCode("201008").setDisplay("Recovery Room"))).setUnitPrice(new Money().setValue(10000).setCurrency("INR")));
        claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setSystem("https://irdai.gov.in/package-code").setCode("406003").setDisplay("intra -venous (iv) set"))).setUnitPrice(new Money().setValue(5000).setCurrency("INR")));
        claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setSystem("https://irdai.gov.in/package-code").setCode("E507353").setDisplay("Oral Medication"))).setUnitPrice(new Money().setValue(5000).setCurrency("INR")));
        claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setSystem("https://irdai.gov.in/package-code").setCode("E925171").setDisplay("Hospital charges"))).setUnitPrice(new Money().setValue(5000).setCurrency("INR")));
        claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setSystem("https://irdai.gov.in/package-code").setCode("501001").setDisplay("Consultation Charges"))).setUnitPrice(new Money().setValue(5000).setCurrency("INR")));
        claim.getProcedure().add(new Claim.ProcedureComponent().setSequence(1).setProcedure(new CodeableConcept(new Coding().setCode("PROC1").setCode("SampleCOde1").setSystem("https://irdai.gov.in/package-code"))));
        claim.getProcedure().add(new Claim.ProcedureComponent().setSequence(2).setProcedure(new CodeableConcept(new Coding().setCode("PROC2").setCode("SampleCOde2").setSystem("https://irdai.gov.in/package-code"))));

        //total cost
//        claim.setTotal(new Money().setCurrency("INR").setValue(146000.0));
        return claim;
    }

    public static Coverage coverageExample(){
        //making the coverage resource
        Coverage cov = new Coverage();
        cov.setId("COVERAGE1");
        Meta metacov = new Meta();
        metacov.getProfile().add(new CanonicalType("https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-Coverage.html"));
        cov.setMeta(metacov);
        cov.setStatus(Coverage.CoverageStatus.ACTIVE);
        cov.getIdentifier().add(new Identifier().setValue("policy-RVH1003").setSystem("https://www.gicofIndia.in/policies"));
        cov.getSubscriber().setReference("Patient/RVH1003");
//        cov.setSubscriberId("2XX8971");
        cov.getBeneficiary().setReference( "Patient/RVH1003");
        cov.setRelationship(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/subscriber-relationship").setCode("self")));
        cov.getPayor().add(new Reference("Organization/GICOFINDIA"));
        return cov;
    }

    public static CommunicationRequest communicationRequestExample(){
        CommunicationRequest comReq = new CommunicationRequest();
        comReq.setId(UUID.randomUUID().toString());
        Meta meta = new Meta();
        meta.getProfile().add(new CanonicalType("https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-CommunicationRequest.html"));
        meta.setLastUpdated(new Date());
        comReq.setMeta(meta);
        comReq.getIdentifier().add(new Identifier().setSystem("http://irdai.gov.in/insurer/123456").setValue("ABCD123"));
        comReq.setStatus(CommunicationRequest.CommunicationRequestStatus.ACTIVE);
        comReq.getBasedOn().add(new Reference("Patient/RVH1003"));
//        comReq.getPayload().add(new CommunicationRequest.CommunicationRequestPayloadComponent().setContent(new StringType("Please verify the OTP sent to your mobile number to proceed.")));
        return comReq;
    }


    public static Communication communicationExample(){
        Communication comm =  new Communication();
        comm.setId(UUID.randomUUID().toString());
        Meta meta = new Meta();
        meta.getProfile().add(new CanonicalType("https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-Communication.html"));
        meta.setLastUpdated(new Date());
        comm.setMeta(meta);
        comm.getIdentifier().add(new Identifier().setSystem("http://www.providerco.com/communication").setValue("12345"));
        comm.setStatus(Communication.CommunicationStatus.COMPLETED);
        comm.getPayload().add(new Communication.CommunicationPayloadComponent().setContent(new Attachment().setContentType("application/pdf").setData("abcd".getBytes()).setTitle("accident_notes.pdf").setCreation(new Date())));
        return comm;
    }

    public static Practitioner practitionerExample() {
        //making the hospital org resource
        Practitioner hos = new Practitioner();
        hos.setId("PractitionerViswasKar");
        Meta metaorg1 = new Meta();
        metaorg1.getProfile().add(new CanonicalType("https://nrces.in/ndhm/fhir/r4/StructureDefinition/Practitioner"));
        hos.setMeta(metaorg1);
        hos.getName().add(new HumanName().setText("Dr Viswas kar"));
        hos.getIdentifier().add(new Identifier().setSystem("http://abdm.gov.in/facilities").setValue("DOC-123/456").setType(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203").setCode("MD").setDisplay("Medical License number"))));
        return hos;
    }

    public static Organization insurerOrganizationExample(){
        //making an organization resource
        Organization org = new Organization();
        Meta metaorg = new Meta();
        metaorg.getProfile().add(new CanonicalType("https://nrces.in/ndhm/fhir/r4/StructureDefinition/Organization"));
        org.setMeta(metaorg);
        org.setId("GICOFINDIA");
//        org.setName("GICOFINDIA");
        org.getIdentifier().add(new Identifier().setSystem("http://irdai.gov.in/insurers").setValue("GICOFINDIA").setType(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203").setCode("AC").setDisplay("GOVOFINDIA"))));
        return org;
    }
}

