package org.gbif.dwc.validator.evaluator.impl;

import org.gbif.dwc.record.Record;
import org.gbif.dwc.terms.ConceptTerm;
import org.gbif.dwc.validator.config.ArchiveValidatorConfig;
import org.gbif.dwc.validator.evaluator.RecordEvaluatorIF;
import org.gbif.dwc.validator.result.Result;
import org.gbif.dwc.validator.result.ResultAccumulatorIF;
import org.gbif.dwc.validator.result.ValidationContext;
import org.gbif.dwc.validator.result.ValidationResult;
import org.gbif.dwc.validator.result.ValidationResultElement;
import org.gbif.dwc.validator.result.type.ContentValidationType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.common.base.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChainableRecordEvaluator to check the uniqueness of specific fields.
 * This ChainableRecordEvaluator will only produce results on postIterate() call.
 * This implementation will write a new file with all the id and then sort it using org.gbif.utils.file.FileUtils.
 * GBIF FileUtils can also sort directly on the archive file, it may be a better solution than writing a new
 * file containing all the id.
 * 
 * @author cgendreau
 */
public class UniquenessEvaluator implements RecordEvaluatorIF {

  /**
   * Builder of UniquenessEvaluator object.
   * Return UniquenessEvaluator is NOT totally immutable due to file access.
   * 
   * @author cgendreau
   */
  public static class UniquenessEvaluatorBuilder {

    private ValidationContext evaluatorContext;
    private ConceptTerm term;

    private UniquenessEvaluatorBuilder(ValidationContext evaluatorContext) {
      this.evaluatorContext = evaluatorContext;
    }

    /**
     * Create with default value. Using coreId, ValidationContext.CORE
     * 
     * @return
     */
    public static UniquenessEvaluatorBuilder create() {
      return new UniquenessEvaluatorBuilder(ValidationContext.CORE);
    }

    public UniquenessEvaluator build() throws IOException, IllegalStateException {
      if (evaluatorContext == null) {
        throw new IllegalStateException("The reference data must be set");
      }
      return new UniquenessEvaluator(term, evaluatorContext);
    }

    /**
     * Set on which ConceptTerm the evaluation should be made.
     * 
     * @param term
     * @param evaluatorContext context of the provided term
     * @return
     */
    public UniquenessEvaluatorBuilder on(ConceptTerm term, ValidationContext evaluatorContext) {
      this.evaluatorContext = evaluatorContext;
      this.term = term;
      return this;
    }
  }

  private final ValidationContext evaluatorContext;
  private final ConceptTerm term;
  private final String conceptTermString;

  private static final Logger LOGGER = LoggerFactory.getLogger(UniquenessEvaluator.class);
  org.gbif.utils.file.FileUtils GBIF_FILE_UTILS = new org.gbif.utils.file.FileUtils();
  private static final int BUFFER_THRESHOLD = 1000;

  private static final String FILE_EXT = ".txt";

  private final List<String> idList;
  private final FileWriter fw;

  private final String fileName;
  private final String sortedFileName;

  /**
   * @param evaluatorContext
   * @param term id term is null, the coreId will be used
   * @throws IOException
   */
  private UniquenessEvaluator(ConceptTerm term, ValidationContext evaluatorContext) throws IOException {
    this.evaluatorContext = evaluatorContext;
    this.term = term;

    this.conceptTermString = term != null ? term.simpleName() : "coreId";

    idList = new ArrayList<String>(BUFFER_THRESHOLD);
    String randomUUID = UUID.randomUUID().toString();
    fileName = randomUUID + FILE_EXT;
    sortedFileName = randomUUID + "_sorted" + FILE_EXT;
    fw = new FileWriter(new File(fileName));
  }

  public static UniquenessEvaluatorBuilder create() {
    return UniquenessEvaluatorBuilder.create();
  }

  /**
   * Clean generated files
   */
  private void cleanup() {
    new File(fileName).delete();
    new File(sortedFileName).delete();
  }

  /**
   * Record each fields that shall be unique.
   */
  @Override
  public void handleEval(Record record, ResultAccumulatorIF resultAccumulator) {

    if (term == null) {
      idList.add(record.id());
    } else {
      idList.add(record.value(term));
    }

    if (idList.size() >= BUFFER_THRESHOLD) {
      try {
        for (String curr : idList) {
          fw.write(curr);
        }
      } catch (IOException ioEx) {
        LOGGER.error("Can't write to file using FileWriter", ioEx);
      }
      idList.clear();
    }
  }

  @Override
  public void handlePostIterate(ResultAccumulatorIF resultAccumulator) {
    try {
      // ensure list is empty
      for (String curr : idList) {
        fw.write(curr + ArchiveValidatorConfig.ENDLINE);
      }
      fw.flush();
    } catch (IOException ioEx) {
      LOGGER.error("Can't write to file using FileWriter", ioEx);
    } finally {
      try {
        fw.close();
      } catch (IOException ioEx) {
        LOGGER.error("Can't close UniquenessEvaluator FileWriter properly", ioEx);
      }
    }

    // sort the file containing the id
    try {
      GBIF_FILE_UTILS.sort(new File(fileName), new File(sortedFileName), Charsets.UTF_8.toString(), 0, null, null,
        ArchiveValidatorConfig.ENDLINE, 0);
    } catch (IOException ioEx) {
      LOGGER.error("Can't sort id file", ioEx);
    }

    // search for duplicates
    BufferedReader br = null;
    try {
      String previousLine = "";
      String currentLine;

      br = new BufferedReader(new FileReader(sortedFileName));
      ValidationResultElement validationResultElement = null;
      while ((currentLine = br.readLine()) != null) {
        if (previousLine.equalsIgnoreCase(currentLine)) {
          validationResultElement =
            new ValidationResultElement(ContentValidationType.FIELD_UNIQUENESS, Result.ERROR,
              ArchiveValidatorConfig.getLocalizedString("evaluator.uniqueness", currentLine, conceptTermString));
          resultAccumulator.accumulate(new ValidationResult(currentLine, evaluatorContext, validationResultElement));
        }
        previousLine = currentLine;
      }
    } catch (IOException ioEx) {
      LOGGER.error("Can't sort id file", ioEx);
    } finally {
      try {
        if (br != null)
          br.close();
      } catch (IOException ioEx) {
        LOGGER.error("Can't close BufferedReader", ioEx);
      }
    }
    cleanup();
  }
}
