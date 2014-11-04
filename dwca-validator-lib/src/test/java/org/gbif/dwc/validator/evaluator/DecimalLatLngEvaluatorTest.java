package org.gbif.dwc.validator.evaluator;

import org.gbif.dwc.record.Record;
import org.gbif.dwc.record.RecordImpl;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.text.ArchiveField;
import org.gbif.dwc.text.ArchiveField.DataType;
import org.gbif.dwc.validator.TestEvaluationResultHelper;
import org.gbif.dwc.validator.config.ArchiveValidatorConfig;
import org.gbif.dwc.validator.evaluator.impl.DecimalLatLngEvaluator;
import org.gbif.dwc.validator.evaluator.impl.DecimalLatLngEvaluator.DecimalLatLngEvaluatorBuilder;
import org.gbif.dwc.validator.result.type.ContentValidationType;
import org.gbif.dwc.validator.result.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Optional;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test DecimalLatLngEvaluator implementation.
 * 
 * @author cgendreau
 */
public class DecimalLatLngEvaluatorTest {

  private Record buildMockRecord(String id, String lat, String lng) {
    // create a mock Record
    ArchiveField idField = new ArchiveField(0, DwcTerm.occurrenceID, null, DataType.string);
    ArchiveField latitudeField = new ArchiveField(1, DwcTerm.decimalLatitude, null, DataType.string);
    ArchiveField longitudeField = new ArchiveField(2, DwcTerm.decimalLongitude, null, DataType.string);
    List<ArchiveField> fieldList = new ArrayList<ArchiveField>();
    fieldList.add(latitudeField);
    fieldList.add(longitudeField);
    RecordImpl testRecord = new RecordImpl(idField, fieldList, "rowType", false);
    testRecord.setRow(new String[] {id, lat, lng});
    return testRecord;
  }

  @Test
  public void testInvalidCoordinates() {
    DecimalLatLngEvaluator decimalLatLngEvaluator = DecimalLatLngEvaluatorBuilder.create().build();

    Optional<ValidationResult> result1 = decimalLatLngEvaluator.handleEval(buildMockRecord("1", "a", "40"));
    Optional<ValidationResult> result2 = decimalLatLngEvaluator.handleEval(buildMockRecord("2", "70", "b"));

    assertTrue(TestEvaluationResultHelper.containsValidationType(result1.get(),
      ContentValidationType.RECORD_CONTENT_VALUE));
    assertTrue(TestEvaluationResultHelper.containsValidationType(result2.get(),
      ContentValidationType.RECORD_CONTENT_VALUE));
  }

  @Test
  public void testOutOfBoundsCoordinates() {
    DecimalLatLngEvaluator decimalLatLngEvaluator = DecimalLatLngEvaluatorBuilder.create().build();

    Optional<ValidationResult> result1 = decimalLatLngEvaluator.handleEval(buildMockRecord("1", "91", "120"));
    Optional<ValidationResult> result2 = decimalLatLngEvaluator.handleEval(buildMockRecord("2", "91", "40"));
    Optional<ValidationResult> result3 = decimalLatLngEvaluator.handleEval(buildMockRecord("3", "70", "181"));

    assertTrue(TestEvaluationResultHelper.containsValidationType(result1.get(),
      ContentValidationType.RECORD_CONTENT_BOUNDS));
    assertTrue(TestEvaluationResultHelper.containsValidationType(result2.get(),
      ContentValidationType.RECORD_CONTENT_BOUNDS));
    assertTrue(TestEvaluationResultHelper.containsValidationType(result3.get(),
      ContentValidationType.RECORD_CONTENT_BOUNDS));
  }

  @Test
  public void testPossiblyInvalidCoordinates() {

    DecimalLatLngEvaluator decimalLatLngEvaluator = DecimalLatLngEvaluatorBuilder.create().build();

    Optional<ValidationResult> result1 = decimalLatLngEvaluator.handleEval(buildMockRecord("1", "0", "0"));

    assertTrue(TestEvaluationResultHelper.containsResultMessage(result1.get(),
      ArchiveValidatorConfig.getLocalizedString("evaluator.decimal_lat_lng.zero", "0", "0")));
  }

  @Test
  public void testValidCoordinates() {
    DecimalLatLngEvaluator decimalLatLngEvaluator = DecimalLatLngEvaluatorBuilder.create().build();

    Optional<ValidationResult> result1 = decimalLatLngEvaluator.handleEval(buildMockRecord("1", "30.001", "40.001"));
    Optional<ValidationResult> result2 = decimalLatLngEvaluator.handleEval(buildMockRecord("2", "30", "120"));

    assertFalse(result1.isPresent());
    assertFalse(result2.isPresent());
  }

}
