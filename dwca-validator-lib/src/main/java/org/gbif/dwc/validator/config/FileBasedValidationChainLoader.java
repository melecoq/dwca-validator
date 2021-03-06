package org.gbif.dwc.validator.config;

import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.validator.Evaluators;
import org.gbif.dwc.validator.annotation.AnnotationLoader;
import org.gbif.dwc.validator.chain.EvaluatorChain;
import org.gbif.dwc.validator.criteria.annotation.CriterionConfigurationKey;
import org.gbif.dwc.validator.criteria.annotation.DatasetCriterionBuilderKey;
import org.gbif.dwc.validator.criteria.annotation.RecordCriterionBuilderKey;
import org.gbif.dwc.validator.criteria.dataset.DatasetCriterion;
import org.gbif.dwc.validator.criteria.dataset.DatasetCriterionBuilder;
import org.gbif.dwc.validator.criteria.record.RecordCriterion;
import org.gbif.dwc.validator.criteria.record.RecordCriterionBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Class allowing to build a validation chain from a configuration file in yaml.
 * 
 * @author cgendreau
 */
public class FileBasedValidationChainLoader {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedValidationChainLoader.class);

  private static final String BASE_PACKAGE_TO_SCAN = "org.gbif.dwc.validator";
  private static final String RECORD_CRITERIA_SECTION = "recordCriteria";
  private static final String DATASET_CRITERIA_SECTION = "datasetCriteria";

  private static final String DWC_TERM_ALIAS = "!dwcTerm";
  private static final String DC_TERM_ALIAS = "!dcTerm";

  /**
   * Only works for RecordEvaluator for now.
   * 
   * @param configFilePath
   * @return head of the validation chain or null if the chain can not be created
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  public EvaluatorChain buildValidationChainFromYamlFile(File configFile) throws IOException {
    InputStream ios = null;
    EvaluatorChain chainHead = null;

    try {
      ios = new FileInputStream(configFile);

      Yaml yaml = new Yaml(buildYamlContructor());

      // configuration file is organized as sections
      Map<String, Object> conf = (Map<String, Object>) yaml.load(ios);

      List<RecordCriterion> recordEvaluatorList = (List<RecordCriterion>) conf.get(RECORD_CRITERIA_SECTION);
      List<DatasetCriterion> datasetEvaluatorList = (List<DatasetCriterion>) conf.get(DATASET_CRITERIA_SECTION);

      ios.close();

      chainHead = Evaluators.buildFromEvaluatorList(recordEvaluatorList, datasetEvaluatorList);

    } catch (IOException ioEx) {
      throw ioEx;
    } finally {
      IOUtils.closeQuietly(ios);
    }

    return chainHead;
  }


  /**
   * Build a yaml configured Constructor object.
   * 
   * @return
   */
  private Constructor buildYamlContructor() {

    // Get all annotated EvaluationRuleBuilder implementations
    Set<Class<RecordCriterionBuilder>> recordCriteriaBuilderClasses =
      AnnotationLoader.getAnnotatedClasses(BASE_PACKAGE_TO_SCAN, RecordCriterionBuilderKey.class,
        RecordCriterionBuilder.class);

    Set<Class<DatasetCriterionBuilder>> datasetCriteriaBuilderClasses =
      AnnotationLoader.getAnnotatedClasses(BASE_PACKAGE_TO_SCAN, DatasetCriterionBuilderKey.class,
        DatasetCriterionBuilder.class);

    Constructor yamlConstructor =
      new ValidatorYamlContructor(recordCriteriaBuilderClasses, datasetCriteriaBuilderClasses);

    // Register an alias for DwcTerm and DcTerm class
    yamlConstructor.addTypeDescription(new TypeDescription(DwcTerm.class, DWC_TERM_ALIAS));
    yamlConstructor.addTypeDescription(new TypeDescription(DcTerm.class, DC_TERM_ALIAS));

    // Register aliases on class name for @EvaluationRuleConfigurationKey
// Set<Class<?>> evaluationRuleConfigurationClasses =
// AnnotationLoader.getAnnotatedClasses(BASE_PACKAGE_TO_SCAN, EvaluationRuleConfigurationKey.class);
// registerAliases(evaluationRuleConfigurationClasses, yamlConstructor);

    // Register aliases on class name for RecordEvaluatorConfigurationKey
    Set<Class<?>> criteriaConfigurationClasses =
      AnnotationLoader.getAnnotatedClasses(BASE_PACKAGE_TO_SCAN, CriterionConfigurationKey.class);
    registerAliases(criteriaConfigurationClasses, yamlConstructor);

    return yamlConstructor;
  }

  /**
   * Register aliases based on Class name.
   * 
   * @param class list
   * @param yamlConstructor
   */
  private void registerAliases(Collection<Class<?>> classList, Constructor yamlConstructor) {
    for (Class<?> currClass : classList) {
      yamlConstructor.addTypeDescription(new TypeDescription(currClass, "!"
        + StringUtils.uncapitalize(currClass.getSimpleName())));
    }
  }

}
