package org.gbif.dwc.validator.criteria.record;

import org.gbif.dwc.record.Record;
import org.gbif.dwc.validator.config.ValidatorConfig;
import org.gbif.dwc.validator.criteria.annotation.RecordCriterionKey;
import org.gbif.dwc.validator.criteria.configuration.CompletenessCriterionConfiguration;
import org.gbif.dwc.validator.result.EvaluationContext;
import org.gbif.dwc.validator.result.Result;
import org.gbif.dwc.validator.result.type.ContentValidationType;
import org.gbif.dwc.validator.result.validation.ValidationResult;
import org.gbif.dwc.validator.result.validation.ValidationResultElement;
import org.gbif.dwc.validator.transformation.ValueTransformation;
import org.gbif.dwc.validator.transformation.ValueTransformationResult;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Optional;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

@RecordCriterionKey(key = "completenessCriterion")
class CompletenessCriterion implements RecordCriterion {

  private final String key = CompletenessCriterion.class.getAnnotation(RecordCriterionKey.class).key();

  private final List<ValueTransformation<Boolean>> valueTransformations;

  private final Result level;
  private final String rowTypeRestriction;

  CompletenessCriterion(CompletenessCriterionConfiguration completenessCriterionConfiguration) {
    this.valueTransformations = completenessCriterionConfiguration.getValueTransformations();
    this.rowTypeRestriction = completenessCriterionConfiguration.getRowTypeRestriction();
    this.level = completenessCriterionConfiguration.getLevel();
  }

  @Override
  public String getCriteriaKey() {
    return key;
  }

  @Override
  public Optional<ValidationResult> validate(Record record, EvaluationContext evaluationContext) {
    // if we specified a rowType restriction, check that the record is also of this rowType
    if (StringUtils.isNotBlank(rowTypeRestriction) && !rowTypeRestriction.equalsIgnoreCase(record.rowType())) {
      return Optional.absent();
    }

    List<ValidationResultElement> elementList = new ArrayList<ValidationResultElement>();

    for (ValueTransformation<Boolean> currValueTransformation : valueTransformations) {
      ValueTransformationResult<Boolean> parsingResult = currValueTransformation.transform(record);

      if (parsingResult.isNotTransformed() || BooleanUtils.isFalse(parsingResult.getData())) {
        elementList.add(new ValidationResultElement(key, ContentValidationType.RECORD_CONTENT_VALUE, level,
          ValidatorConfig.getLocalizedString("criterion.completeness_criterion.incomplete", parsingResult.getTerm())));
      }
    }

    if (elementList != null && elementList.size() > 0) {
      return Optional.of(new ValidationResult(record.id(), evaluationContext, record.rowType(), elementList));
    }

    return Optional.of(new ValidationResult(record.id(), evaluationContext, record.rowType()));
  }

}
