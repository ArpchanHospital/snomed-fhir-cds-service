package org.snomed.cdsservice.service;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSCoding;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSReference;
import org.snomed.cdsservice.model.CDSSource;
import org.snomed.cdsservice.model.CDSTrigger;
import org.snomed.cdsservice.model.MedicationConditionCDSTrigger;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.snomed.cdsservice.service.medication.MedicationCombinationRuleLoaderService;
import org.snomed.cdsservice.service.medication.MedicationConditionRuleLoaderService;
import org.snomed.cdsservice.service.medication.MedicationOrderSelectCDSService;
import org.snomed.cdsservice.service.medication.dose.MedicationDoseFormsLoaderService;
import org.snomed.cdsservice.service.medication.dose.SnomedMedicationDefinedDailyDoseService;
import org.snomed.cdsservice.service.model.ManyToOneMapEntry;
import org.snomed.cdsservice.service.tsclient.ConceptParameters;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.util.StreamUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
class MedicationOrderSelectCDSServiceTest {

    public static final String SNOMEDCT_SYSTEM = "http://snomed.info/sct";
    private static final String CONTRAINDICATION_ALERT_TYPE = "Contraindication";
    private static final String HIGH_DOSAGE_ALERT_TYPE = "High Dosage";
	private static final String INVALID_DOSAGE_ALERT_TYPE = "Validation Error";

	@Autowired
    SnomedMedicationDefinedDailyDoseService snomedMedicationDefinedDailyDoseService;
    @MockBean
    private MedicationConditionRuleLoaderService ruleLoaderService;
    @MockBean
    private MedicationCombinationRuleLoaderService medicationRuleLoaderService;
    @Autowired
    private MedicationOrderSelectCDSService service;
    @MockBean
    private FHIRTerminologyServerClient mockTsClient;
    @MockBean
    private MedicationDoseFormsLoaderService mockDoseFormsLoaderService;

    @BeforeEach
    void setMockOutput() throws ServiceException {
        CDSTrigger trigger = new MedicationConditionCDSTrigger(
                "Atorvastatin",
                Collections.singleton(new Coding("http://snomed.info/sct", "1145419005", null)),
                "Disease of liver",
                List.of(
                        new Coding("http://snomed.info/sct", "235856003", "Disease of liver"),
                        new Coding("http://snomed.info/sct", "197321007", "Steatosis of liver")
                ),
                new CDSCard(
                        "c2f4ca5c-96a0-49c5-bb80-cbfcc015abfd",
                        "Contraindication: {{ActualMedication}} with patient condition {{ActualCondition}}.",
                        "The use of {{RuleMedication}} is contraindicated when the patient has {{RuleCondition}}.",
                        CDSIndicator.warning,
                        new CDSSource("Wikipedia"),
                        Stream.of(new CDSReference(Collections.singletonList(new CDSCoding("http://snomed.info/sct", "1145419005")))).collect(Collectors.toList()),
						Stream.of(new CDSReference(Collections.singletonList(new CDSCoding("http://snomed.info/sct", "197321007")))).collect(Collectors.toList()), CONTRAINDICATION_ALERT_TYPE));
        service.setMedicationOrderSelectTriggers(List.of(trigger));
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("1145419005"))).thenReturn(getConceptParamsForDrugAtorvastatinTablet());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("258684004"))).thenReturn(getConceptParamsForDoseUnitMg());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("732936001"))).thenReturn(getConceptParamsForDoseUnitFormTablet());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("373444002"))).thenReturn(getConceptParamsForSubstanceAtorvastatin());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("408051007"))).thenReturn(getConceptParamsForDrugRamiprilOralTablet());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("386872004"))).thenReturn(getConceptParamsForSubstanceRamipril());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("782087002"))).thenReturn(getConceptParamsForDrugRanitidineInjection());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("258773002"))).thenReturn(getConceptParamsForDoseUnitMl());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("372755005"))).thenReturn(getConceptParamsForSubstanceRanitidine());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("317249006"))).thenReturn(getConceptParamsForDrugRanitidineOralTablet());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("433216006"))).thenReturn(getConceptParamsForDrugColchicineAndProbenecidTablet());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("387365004"))).thenReturn(getConceptParamsForSubstanceProbenecid());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("258685003"))).thenReturn(getConceptParamsForDoseUnitMcg());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("387413002"))).thenReturn(getConceptParamsForSubstanceColchicine());
        when(mockTsClient.lookup(eq(SNOMEDCT_SYSTEM), eq("dummyCode"))).thenThrow(new RuntimeException("dummy exception"));
        when(mockDoseFormsLoaderService.loadDoseFormMap()).thenReturn(getMockMapList());
        snomedMedicationDefinedDailyDoseService.setDoseFormsManySnomedToOneAtcCodeMap(getMockMapList());
    }

	@Test
	public void shouldReturnAlert_WhenDrugAndConditionIsContraindicated() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundle.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(1, cards.size());

		CDSCard cdsCard = cards.get(0);
		assertEquals("Contraindication: \"Atorvastatin (as atorvastatin calcium) 10 mg oral tablet\" with patient condition \"Steatosis of liver\".", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertEquals("The use of Atorvastatin is contraindicated when the patient has Disease of liver.", cdsCard.getDetail());
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertEquals("197321007", cdsCard.getReferenceConditions().get(0).getCoding().get(0).getCode());
		assertEquals(CONTRAINDICATION_ALERT_TYPE, cdsCard.getAlertType());
	}


	@Test
	public void shouldReturnOverDoseWarningAlert_WhenPrescribedDailyDoseExceedsMaximumThresholdFactor() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithWarningExceedsOverDose.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard = cards.get(1);
		assertEquals("The amount of Atorvastatin prescribed is 6 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 6.00 times the average daily dose."));
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C10AA05"));
		assertEquals(HIGH_DOSAGE_ALERT_TYPE, cdsCard.getAlertType());
	}

	@Test
	public void shouldReturnOverDoseWarningAlert_WhenPrescribedDailyDoseEqualsMaximumThresholdFactor() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithWarningEqualsOverDose.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(1, cards.size());

		CDSCard cdsCard = cards.get(0);
		assertEquals("The amount of Ranitidine prescribed is 4 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 4.00 times the average daily dose."));
		assertEquals("317249006", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=A02BA02"));
		assertEquals(HIGH_DOSAGE_ALERT_TYPE, cdsCard.getAlertType());
	}

	@Test
	public void shouldReturnOverDoseInfoAlert_WhenPrescribedDailyDoseExceedsAcceptableThresholdFactor() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithInfoOverDose.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard = cards.get(1);
		assertEquals("The amount of Atorvastatin prescribed is 2.5 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.info, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 2.50 times the average daily dose."));
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C10AA05"));
		assertEquals(HIGH_DOSAGE_ALERT_TYPE, cdsCard.getAlertType());
	}
	@Test
	public void shouldNotReturnOverDoseAlert_WhenPrescribedDailyDoseIsWithinAcceptableThresholdFactor() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithNoOverDose.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(1, cards.size());
	}
	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForFrequencyPeriodUnitInDays() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithFrequencyPeriodUnitInDays.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard = cards.get(1);
		assertEquals("The amount of Atorvastatin prescribed is 12 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 12.00 times the average daily dose."));
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C10AA05"));
		assertEquals(HIGH_DOSAGE_ALERT_TYPE, cdsCard.getAlertType());
	}
	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForFrequencyPeriodUnitInHours() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithFrequencyPeriodUnitInHours.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard = cards.get(1);
		assertEquals("The amount of Atorvastatin prescribed is 12 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 12.00 times the average daily dose."));
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C10AA05"));
		assertEquals(HIGH_DOSAGE_ALERT_TYPE, cdsCard.getAlertType());
	}
	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForFrequencyPeriodUnitInWeeks() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithFrequencyPeriodUnitInWeeks.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard = cards.get(1);
		assertEquals("The amount of Atorvastatin prescribed is 5 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 5.00 times the average daily dose."));
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C10AA05"));
		assertEquals(HIGH_DOSAGE_ALERT_TYPE, cdsCard.getAlertType());
	}
	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForFrequencyPeriodUnitInMonths() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithFrequencyPeriodUnitInMonths.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard = cards.get(1);
		assertEquals("The amount of Atorvastatin prescribed is 5 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 5.00 times the average daily dose."));
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C10AA05"));
		assertEquals(HIGH_DOSAGE_ALERT_TYPE, cdsCard.getAlertType());
	}


	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForMultipleDrugs_WithDifferentDosageUnits() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithDosageAndUnits.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());
		CDSCard cdsCard1 = cards.get(0);
		assertEquals("The amount of Ramipril prescribed is 96 times the average daily dose.", cdsCard1.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard1.getIndicator());
		assertTrue( cdsCard1.getDetail().contains("Conclusion : Combined prescribed amount is 96.00 times the average daily dose."));
		assertEquals("408051007", cdsCard1.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard1.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C09AA05"));
		assertEquals(HIGH_DOSAGE_ALERT_TYPE, cdsCard1.getAlertType());

		CDSCard cdsCard2 = cards.get(1);
		assertEquals("The amount of Ranitidine prescribed is 40 times the average daily dose.", cdsCard2.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard2.getIndicator());
		assertTrue( cdsCard2.getDetail().contains("Conclusion : Combined prescribed amount is 40.00 times the average daily dose."));
		assertEquals("782087002", cdsCard2.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard2.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=A02BA02"));
		assertEquals(HIGH_DOSAGE_ALERT_TYPE, cdsCard2.getAlertType());
	}

	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForMultipleDrugsHavingSameSubstance_WithDifferentRouteOfAdministration() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithDifferentManufacturedDosageFormAndDifferentRouteOfAdministration.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(1, cards.size());

		CDSCard cdsCard = cards.get(0);
		assertEquals("The amount of Ranitidine prescribed is 64 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 64.00 times the average daily dose."));
		assertTrue( cdsCard.getDetail().contains("Parenteral"));
		assertTrue( cdsCard.getDetail().contains("Oral"));
		assertEquals("317249006", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertEquals("782087002", cdsCard.getReferenceMedications().get(1).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=A02BA02"));
		assertEquals(HIGH_DOSAGE_ALERT_TYPE, cdsCard.getAlertType());
	}

	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForMultipleDrugsHavingSameSubstance_WithDifferentManufacturedDoseForms() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithDifferentManufacturedDosageFormAndDifferentRouteOfAdministration.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(1, cards.size());

		CDSCard cdsCard = cards.get(0);
		assertEquals("The amount of Ranitidine prescribed is 64 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 64.00 times the average daily dose."));
		assertTrue( cdsCard.getDetail().contains("Ranitidine (as ranitidine hydrochloride) 150 mg oral tablet"));
		assertTrue( cdsCard.getDetail().contains("Ranitidine (as ranitidine hydrochloride) 25 mg/mL solution for injection"));
		assertEquals("317249006", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertEquals("782087002", cdsCard.getReferenceMedications().get(1).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=A02BA02"));
		assertEquals(HIGH_DOSAGE_ALERT_TYPE, cdsCard.getAlertType());
	}

	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForSingleDrugHavingMultipleSubstances() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithCombinatorialDrug.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard1 = cards.get(0);
		assertEquals("The amount of Probenecid prescribed is 6 times the average daily dose.", cdsCard1.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard1.getIndicator());
		assertTrue( cdsCard1.getDetail().contains("Conclusion : Combined prescribed amount is 6.00 times the average daily dose."));
		assertEquals("433216006", cdsCard1.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertEquals(1, cdsCard1.getReferenceMedications().size());
		assertTrue(cdsCard1.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=M04AB01"));
		assertEquals(HIGH_DOSAGE_ALERT_TYPE, cdsCard1.getAlertType());

		CDSCard cdsCard2 = cards.get(1);
		assertEquals("The amount of Colchicine prescribed is 6 times the average daily dose.", cdsCard2.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard2.getIndicator());
		assertTrue( cdsCard2.getDetail().contains("Conclusion : Combined prescribed amount is 6.00 times the average daily dose."));
		assertEquals("433216006", cdsCard2.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertEquals(1, cdsCard2.getReferenceMedications().size());
		assertTrue(cdsCard2.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=M04AC01"));
		assertEquals(HIGH_DOSAGE_ALERT_TYPE, cdsCard2.getAlertType());

    }

	@Test
	public void shouldCreateInvalidDosageCdssAlerts_WhenRequestBundleContainsMismatchedDoseUnitsAndRoutes() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithMismatchedDoseUnitsAndRoutes.json"), StandardCharsets.UTF_8)
		));
		List<CDSCard> cards = service.call(cdsRequest);
		CDSCard cdsCard1 = cards.get(0);
		CDSCard cdsCard2 = cards.get(1);
		CDSCard cdsCard3 = cards.get(2);
		assertEquals(3, cards.size());
		assertEquals(CONTRAINDICATION_ALERT_TYPE, cdsCard1.getAlertType());
		assertEquals(INVALID_DOSAGE_ALERT_TYPE, cdsCard2.getAlertType());
		assertEquals(INVALID_DOSAGE_ALERT_TYPE, cdsCard3.getAlertType());
		assertTrue(cdsCard2.getDetail().contains("dose unit"));
		assertTrue(cdsCard3.getDetail().contains("dose route"));
	}
	@Test
	public void shouldThrowException_WhenRequestBundleContainsInvalidMedicationCode() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithInvalidMedicationCode.json"), StandardCharsets.UTF_8)
		));
		assertThrows(ResponseStatusException.class, () ->service.call(cdsRequest) );
	}


    private ConceptParameters getConceptParamsForDoseUnitMg() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"258684004\"},{\"name\":\"display\",\"valueString\":\"mg\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20020131\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"258681007\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"258681007|International System of Units unit of mass (qualifier value)|\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"milligram (qualifier value)\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"mg\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000549004\",\"display\":\"ACCEPTABLE\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000549004\",\"display\":\"ACCEPTABLE\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"milligram\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"258681007\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private ConceptParameters getConceptParamsForDoseUnitFormTablet() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"732936001\"},{\"name\":\"display\",\"valueString\":\"Tablet\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20170731\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"732935002\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"732935002|Unit of presentation (unit of presentation)|\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"Tablet\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"Tablet (unit of presentation)\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"732935002\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private ConceptParameters getConceptParamsForDoseUnitMl() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"258773002\"},{\"name\":\"display\",\"valueString\":\"mL\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20020131\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"282115005\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"282115005|International System of Units-derived unit of volume (qualifier value)|\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"Milliliter (qualifier value)\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000549004\",\"display\":\"ACCEPTABLE\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000549004\",\"display\":\"ACCEPTABLE\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"cm3\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000549004\",\"display\":\"ACCEPTABLE\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000549004\",\"display\":\"ACCEPTABLE\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"cc\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"mL\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000549004\",\"display\":\"ACCEPTABLE\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"Milliliter\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000549004\",\"display\":\"ACCEPTABLE\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"Millilitre\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"282115005\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private ConceptParameters getConceptParamsForDoseUnitMcg() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"258685003\"},{\"name\":\"display\",\"valueString\":\"mcg\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20020131\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"258681007\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"258681007|International System of Units unit of mass (qualifier value)|\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000549004\",\"display\":\"ACCEPTABLE\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000549004\",\"display\":\"ACCEPTABLE\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"microgram\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"mcg\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000549004\",\"display\":\"ACCEPTABLE\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000549004\",\"display\":\"ACCEPTABLE\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"ug\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"microgram (qualifier value)\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"258681007\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private ConceptParameters getConceptParamsForDrugRamiprilOralTablet() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"408051007\"},{\"name\":\"display\",\"valueString\":\"Ramipril 5 mg oral tablet\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20180731\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"true\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"780345000 : 411116001 = 421026006, 763032000 = 732936001, 1142139005 = #1, { 762949000 = 386872004, 732943007 = 386872004, 1142135004 = #5, 732945000 = 258684004, 1142136003 = #1, 732947008 = 732936001 }\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"780345000|Product containing only ramipril in oral dose form (medicinal product form)| : 411116001|Has manufactured dose form (attribute)| = 421026006|Conventional release oral tablet (dose form)|, 763032000|Has unit of presentation (attribute)| = 732936001|Tablet (unit of presentation)|, 1142139005|Count of base of active ingredient (attribute)| = #1, { 762949000|Has precise active ingredient (attribute)| = 386872004|Ramipril (substance)|, 732943007|Has basis of strength substance (attribute)| = 386872004|Ramipril (substance)|, 1142135004|Has presentation strength numerator value (attribute)| = #5, 732945000|Has presentation strength numerator unit (attribute)| = 258684004|milligram (qualifier value)|, 1142136003|Has presentation strength denominator value (attribute)| = #1, 732947008|Has presentation strength denominator unit (attribute)| = 732936001|Tablet (unit of presentation)| }\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"Product containing precisely ramipril 5 milligram/1 each conventional release oral tablet (clinical drug)\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"Ramipril 5 mg oral tablet\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"780345000\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private ConceptParameters getConceptParamsForDrugRanitidineInjection() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"782087002\"},{\"name\":\"display\",\"valueString\":\"Ranitidine (as ranitidine hydrochloride) 25 mg/mL solution for injection\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20190131\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"true\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"1237164000 : 411116001 = 385219001, 1142139005 = #1, { 762949000 = 24202000, 732943007 = 372755005, 1142138002 = #25, 733725009 = 258684004, 1142137007 = #1, 733722007 = 258773002 }\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"1237164000|Product containing precisely ranitidine (as ranitidine hydrochloride) 25 milligram/1 milliliter conventional release solution for infusion and/or injection (clinical drug)| : 411116001|Has manufactured dose form (attribute)| = 385219001|Conventional release solution for injection (dose form)|, 1142139005|Count of base of active ingredient (attribute)| = #1, { 762949000|Has precise active ingredient (attribute)| = 24202000|Ranitidine hydrochloride (substance)|, 732943007|Has basis of strength substance (attribute)| = 372755005|Ranitidine (substance)|, 1142138002|Has concentration strength numerator value (attribute)| = #25, 733725009|Has concentration strength numerator unit (attribute)| = 258684004|milligram (qualifier value)|, 1142137007|Has concentration strength denominator value (attribute)| = #1, 733722007|Has concentration strength denominator unit (attribute)| = 258773002|Milliliter (qualifier value)| }\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"Product containing precisely ranitidine (as ranitidine hydrochloride) 25 milligram/1 milliliter conventional release solution for injection (clinical drug)\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"Ranitidine (as ranitidine hydrochloride) 25 mg/mL solution for injection\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"1237164000\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private ConceptParameters getConceptParamsForDrugRanitidineOralTablet() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"317249006\"},{\"name\":\"display\",\"valueString\":\"Ranitidine (as ranitidine hydrochloride) 150 mg oral tablet\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20180731\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"true\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"780346004 : 411116001 = 421026006, 763032000 = 732936001, 1142139005 = #1, { 762949000 = 24202000, 732943007 = 372755005, 1142135004 = #150, 732945000 = 258684004, 1142136003 = #1, 732947008 = 732936001 }\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"780346004|Product containing only ranitidine in oral dose form (medicinal product form)| : 411116001|Has manufactured dose form (attribute)| = 421026006|Conventional release oral tablet (dose form)|, 763032000|Has unit of presentation (attribute)| = 732936001|Tablet (unit of presentation)|, 1142139005|Count of base of active ingredient (attribute)| = #1, { 762949000|Has precise active ingredient (attribute)| = 24202000|Ranitidine hydrochloride (substance)|, 732943007|Has basis of strength substance (attribute)| = 372755005|Ranitidine (substance)|, 1142135004|Has presentation strength numerator value (attribute)| = #150, 732945000|Has presentation strength numerator unit (attribute)| = 258684004|milligram (qualifier value)|, 1142136003|Has presentation strength denominator value (attribute)| = #1, 732947008|Has presentation strength denominator unit (attribute)| = 732936001|Tablet (unit of presentation)| }\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"Ranitidine (as ranitidine hydrochloride) 150 mg oral tablet\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"Product containing precisely ranitidine (as ranitidine hydrochloride) 150 milligram/1 each conventional release oral tablet (clinical drug)\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"780346004\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private ConceptParameters getConceptParamsForDrugColchicineAndProbenecidTablet() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"433216006\"},{\"name\":\"display\",\"valueString\":\"Colchicine 500 microgram and probenecid 500 mg oral tablet\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20180731\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"true\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"778851009 : 411116001 = 421026006, 763032000 = 732936001, 1142139005 = #2, 766939001 = 773923003, { 762949000 = 387365004, 732943007 = 387365004, 1142135004 = #500, 732945000 = 258684004, 1142136003 = #1, 732947008 = 732936001 }, { 762949000 = 387413002, 732943007 = 387413002, 1142135004 = #500, 732945000 = 258685003, 1142136003 = #1, 732947008 = 732936001 }\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"778851009|Product containing only colchicine and probenecid in oral dose form (medicinal product form)| : 411116001|Has manufactured dose form (attribute)| = 421026006|Conventional release oral tablet (dose form)|, 763032000|Has unit of presentation (attribute)| = 732936001|Tablet (unit of presentation)|, 1142139005|Count of base of active ingredient (attribute)| = #2, 766939001|Plays role (attribute)| = 773923003|Uricosuric therapeutic role (role)|, { 762949000|Has precise active ingredient (attribute)| = 387365004|Probenecid (substance)|, 732943007|Has basis of strength substance (attribute)| = 387365004|Probenecid (substance)|, 1142135004|Has presentation strength numerator value (attribute)| = #500, 732945000|Has presentation strength numerator unit (attribute)| = 258684004|milligram (qualifier value)|, 1142136003|Has presentation strength denominator value (attribute)| = #1, 732947008|Has presentation strength denominator unit (attribute)| = 732936001|Tablet (unit of presentation)| }, { 762949000|Has precise active ingredient (attribute)| = 387413002|Colchicine (substance)|, 732943007|Has basis of strength substance (attribute)| = 387413002|Colchicine (substance)|, 1142135004|Has presentation strength numerator value (attribute)| = #500, 732945000|Has presentation strength numerator unit (attribute)| = 258685003|microgram (qualifier value)|, 1142136003|Has presentation strength denominator value (attribute)| = #1, 732947008|Has presentation strength denominator unit (attribute)| = 732936001|Tablet (unit of presentation)| }\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"Product containing precisely colchicine 500 microgram and probenecid 500 milligram/1 each conventional release oral tablet (clinical drug)\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"Colchicine 500 microgram and probenecid 500 mg oral tablet\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"778851009\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private ConceptParameters getConceptParamsForDrugAtorvastatinTablet() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"1145419005\"},{\"name\":\"display\",\"valueString\":\"Atorvastatin (as atorvastatin calcium) 10 mg oral tablet\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20210731\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"true\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"773456008 : 411116001 = 421026006, 763032000 = 732936001, 1142139005 = #1, { 762949000 = 108601004, 732943007 = 373444002, 1142135004 = #10, 732945000 = 258684004, 1142136003 = #1, 732947008 = 732936001 }\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"773456008|Product containing only atorvastatin in oral dose form (medicinal product form)| : 411116001|Has manufactured dose form (attribute)| = 421026006|Conventional release oral tablet (dose form)|, 763032000|Has unit of presentation (attribute)| = 732936001|Tablet (unit of presentation)|, 1142139005|Count of base of active ingredient (attribute)| = #1, { 762949000|Has precise active ingredient (attribute)| = 108601004|Atorvastatin calcium (substance)|, 732943007|Has basis of strength substance (attribute)| = 373444002|Atorvastatin (substance)|, 1142135004|Has presentation strength numerator value (attribute)| = #10, 732945000|Has presentation strength numerator unit (attribute)| = 258684004|milligram (qualifier value)|, 1142136003|Has presentation strength denominator value (attribute)| = #1, 732947008|Has presentation strength denominator unit (attribute)| = 732936001|Tablet (unit of presentation)| }\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"Product containing precisely atorvastatin (as atorvastatin calcium) 10 milligram/1 each conventional release oral tablet (clinical drug)\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"Atorvastatin (as atorvastatin calcium) 10 mg oral tablet\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"773456008\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private ConceptParameters getConceptParamsForSubstanceAtorvastatin() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"373444002\"},{\"name\":\"display\",\"valueString\":\"Atorvastatin\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20020731\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"115667008 + 372912004 : 726542003 = 734592007, \"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"115667008|Pyrrole (substance)| + 372912004|Substance with 3-hydroxy-3-methylglutaryl-coenzyme A reductase inhibitor mechanism of action (substance)| : 726542003|Has disposition (attribute)| = 734592007|3-hydroxy-3-methylglutaryl-coenzyme A reductase inhibitor (disposition)|, \"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"Atorvastatin\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"Atorvastatin (substance)\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"115667008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"372912004\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private ConceptParameters getConceptParamsForSubstanceRamipril() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"386872004\"},{\"name\":\"display\",\"valueString\":\"Ramipril\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20030131\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"414000001 + 372733002 : 726542003 = 734579009, \"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"414000001|Dipeptide (substance)| + 372733002|Substance with angiotensin-converting enzyme inhibitor mechanism of action (substance)| : 726542003|Has disposition (attribute)| = 734579009|Angiotensin-converting enzyme inhibitor (disposition)|, \"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"Ramipril (substance)\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"Ramipril\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"414000001\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"372733002\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private ConceptParameters getConceptParamsForSubstanceRanitidine() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"372755005\"},{\"name\":\"display\",\"valueString\":\"Ranitidine\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20020731\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"372524001 : 726542003 = 734812003, \"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"372524001|Substance with histamine H2 receptor antagonist mechanism of action (substance)| : 726542003|Has disposition (attribute)| = 734812003|Histamine H2 receptor antagonist (disposition)|, \"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"Ranitidine\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"Ranitidine (substance)\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"372524001\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private ConceptParameters getConceptParamsForSubstanceColchicine() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"387413002\"},{\"name\":\"display\",\"valueString\":\"Colchicine\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20030131\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"418524008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"418524008|Colchicum alkaloid (substance)|\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"Colchicine (substance)\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"Colchicine\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"418524008\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private ConceptParameters getConceptParamsForSubstanceProbenecid() {
        String response = "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"code\",\"valueString\":\"387365004\"},{\"name\":\"display\",\"valueString\":\"Probenecid\"},{\"name\":\"name\",\"valueString\":\"SNOMED CT release 2023-05-31\"},{\"name\":\"system\",\"valueString\":\"http://snomed.info/sct\"},{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20230531\"},{\"name\":\"inactive\",\"valueBoolean\":false},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"effectiveTime\"},{\"name\":\"valueString\",\"valueString\":\"20030131\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"moduleId\"},{\"name\":\"value\",\"valueCode\":\"900000000000207008\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"inactive\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"sufficientlyDefined\"},{\"name\":\"valueBoolean\",\"valueString\":\"false\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalFormTerse\"},{\"name\":\"valueString\",\"valueString\":\"387406002 + 372758007\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"normalForm\"},{\"name\":\"valueString\",\"valueString\":\"387406002|Sulfonamide (substance)| + 372758007|Uricosuric agent (substance)|\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000003001\",\"display\":\"Fully specified name\"}},{\"name\":\"value\",\"valueString\":\"Probenecid (substance)\"}]},{\"extension\":[{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000509007\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]},{\"url\":\"http://snomed.info/fhir/StructureDefinition/designation-use-context\",\"extension\":[{\"url\":\"context\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000508004\"}},{\"url\":\"role\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000548007\",\"display\":\"PREFERRED\"}},{\"url\":\"type\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}}]}],\"name\":\"designation\",\"part\":[{\"name\":\"language\",\"valueCode\":\"en\"},{\"name\":\"use\",\"valueCoding\":{\"system\":\"http://snomed.info/sct\",\"code\":\"900000000000013009\",\"display\":\"Synonym\"}},{\"name\":\"value\",\"valueString\":\"Probenecid\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"387406002\"}]},{\"name\":\"property\",\"part\":[{\"name\":\"code\",\"valueString\":\"parent\"},{\"name\":\"value\",\"valueCode\":\"372758007\"}]}]}";
        Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response);
        ConceptParameters conceptParameters = new ConceptParameters();
        conceptParameters.setParameter(parameters.getParameter());
        return conceptParameters;
    }

    private List<ManyToOneMapEntry> getMockMapList() {
        List<ManyToOneMapEntry> mapEntryList = new ArrayList<>();
        Set<String> oralCodes = new HashSet<>();
        oralCodes.add("421026006");
        ManyToOneMapEntry oral = new ManyToOneMapEntry(oralCodes, "O", 3, "oral");
        Set<String> parenteralCodes = new HashSet<>();
        parenteralCodes.add("385219001");
        ManyToOneMapEntry parenteral = new ManyToOneMapEntry(parenteralCodes, "P", 3, "parenteral");
        mapEntryList.add(oral);
        mapEntryList.add(parenteral);
        return mapEntryList;
    }

}
