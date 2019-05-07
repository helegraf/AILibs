package de.upb.crc901.mlplan.examples.multiclass.weka.regression;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;

import de.upb.crc901.mlplan.core.MLPlan;
import de.upb.crc901.mlplan.core.MLPlanBuilder;
import de.upb.crc901.mlplan.examples.multiclass.weka.MLPlanWekaExperimenter;
import de.upb.crc901.mlplan.multiclass.wekamlplan.weka.model.MLPipeline;
import hasco.events.HASCOSolutionEvent;
import jaicore.basic.SQLAdapter;
import jaicore.basic.TimeOut;
import jaicore.concurrent.GlobalTimer;
import jaicore.experiments.ExperimentDBEntry;
import jaicore.experiments.IExperimentIntermediateResultProcessor;
import jaicore.experiments.IExperimentSetEvaluator;
import jaicore.experiments.exceptions.ExperimentEvaluationFailedException;
import jaicore.ml.WekaUtil;
import jaicore.ml.core.evaluation.measure.singlelabel.EMultiClassPerformanceMeasure;
import jaicore.ml.openml.OpenMLHelper;
import weka.classifiers.Classifier;
import weka.classifiers.evaluation.Evaluation;
import weka.core.Instances;

public class MLPlanWekaRegressionExperimenter implements IExperimentSetEvaluator {
	private static final String CLASSIFIER_FIELD = "classifier";
	private static final String PREPROCESSOR_FIELD = "preprocessor";
	private static final String EVALUATION_TIMEOUT_FIELD = "evaluationTimeout";

	private static final Logger L = LoggerFactory.getLogger(MLPlanWekaRegressionExperimenter.class);

	private final MLPlanWekaRegressionExperimenterConfig experimentConfig;
	private SQLAdapter adapter;
	private int experimentID;

	public MLPlanWekaRegressionExperimenter(final File configFile) {
		super();
		Properties props = new Properties();
		try {
			props.load(new FileInputStream(configFile));
		} catch (IOException e) {
			L.error("Could not find or access config file {}", configFile, e);
			System.exit(1);
		}
		experimentConfig = ConfigFactory.create(MLPlanWekaRegressionExperimenterConfig.class, props);
		if (experimentConfig.getDatasetFolder() == null) {
			throw new IllegalArgumentException("No dataset folder (datasetfolder) set in config.");
		}
		if (experimentConfig.evaluationsTable() == null) {
			throw new IllegalArgumentException("No evaluations table (db.evalTable) set in config");
		}
	}

	@Override
	public void evaluate(final ExperimentDBEntry experimentEntry, final IExperimentIntermediateResultProcessor processor) throws ExperimentEvaluationFailedException {
		try {
			experimentID = experimentEntry.getId();
			Map<String, String> experimentValues = experimentEntry.getExperiment().getValuesOfKeyFields();

			if (!experimentValues.containsKey("dataset")) {
				throw new IllegalArgumentException("\"dataset\" is not configured as a keyword in the experiment config");
			}
			if (!experimentValues.containsKey(EVALUATION_TIMEOUT_FIELD)) {
				throw new IllegalArgumentException("\"" + EVALUATION_TIMEOUT_FIELD + "\" is not configured as a keyword in the experiment config");
			}

			Instances data = OpenMLHelper.getInstancesWithIndexSet(Integer.parseInt(experimentValues.get("dataset")), "abc");
			
			long seed = Long.parseLong(experimentValues.get("seed"));
			L.info("Split instances with seed {}", seed);
			List<Instances> stratifiedSplit = WekaUtil.getStratifiedSplit(data, seed, .7);

			/* initialize ML-Plan with the same config file that has been used to specify the experiments */
			MLPlanBuilder builder = new MLPlanBuilder();
			builder.withSingleLabelClassificationMeasure(EMultiClassPerformanceMeasure.ROOT_MEAN_SQUARED_ERROR);
			builder.withAutoWEKAConfiguration();
			builder.withRandomCompletionBasedBestFirstSearch();
			builder.withTimeoutForNodeEvaluation(new TimeOut(new Integer(experimentValues.get(EVALUATION_TIMEOUT_FIELD)), TimeUnit.SECONDS));
			builder.withTimeoutForSingleSolutionEvaluation(new TimeOut(new Integer(experimentValues.get(EVALUATION_TIMEOUT_FIELD)), TimeUnit.SECONDS));

			MLPlan mlplan = new MLPlan(builder, stratifiedSplit.get(0));
			mlplan.setLoggerName("mlplan");
			mlplan.setTimeout(new Integer(experimentValues.get("timeout")), TimeUnit.SECONDS);
			mlplan.setRandomSeed(new Integer(experimentValues.get("seed")));
			mlplan.setNumCPUs(experimentEntry.getExperiment().getNumCPUs());
			mlplan.registerListener(this);

			L.info("Build mlplan classifier");
			Classifier optimizedClassifier = mlplan.call();

			L.info("Open timeout tasks: {}", GlobalTimer.getInstance().getActiveTasks());

			Evaluation eval = new Evaluation(data);
			L.info("Assess test performance...");
			eval.evaluateModel(optimizedClassifier, stratifiedSplit.get(1));

			L.info("Test error was {}. Internally estimated error for this model was {}", eval.errorRate(), mlplan.getInternalValidationErrorOfSelectedClassifier());
			Map<String, Object> results = new HashMap<>();
			results.put("loss", eval.errorRate());
			results.put(CLASSIFIER_FIELD, WekaUtil.getClassifierDescriptor(((MLPipeline) mlplan.getSelectedClassifier()).getBaseClassifier()));
			results.put(PREPROCESSOR_FIELD, ((MLPipeline) mlplan.getSelectedClassifier()).getPreprocessors().toString());

			writeFile("chosenModel." + experimentID + ".txt", results.get(PREPROCESSOR_FIELD) + "\n\n\n" + results.get(CLASSIFIER_FIELD));
			writeFile("result." + experimentID + ".txt", "intern: " + mlplan.getInternalValidationErrorOfSelectedClassifier() + "\ntest:" + results.get("loss") + "");

			processor.processResults(results);
			L.info("Experiment done.");
		}
		catch (Exception e) {
			throw new ExperimentEvaluationFailedException(e);
		}
	}

	@Subscribe
	public void rcvHASCOSolutionEvent(final HASCOSolutionEvent<Double> e) {
		if (adapter != null) {
			try {
				String classifier = "";
				String preprocessor = "";
				if (e.getSolutionCandidate().getComponentInstance().getComponent().getName().equals("pipeline")) {
					preprocessor = e.getSolutionCandidate().getComponentInstance().getSatisfactionOfRequiredInterfaces().get(PREPROCESSOR_FIELD).toString();
					classifier = e.getSolutionCandidate().getComponentInstance().getSatisfactionOfRequiredInterfaces().get(CLASSIFIER_FIELD).toString();
				} else {
					classifier = e.getSolutionCandidate().getComponentInstance().toString();
				}
				Map<String, Object> eval = new HashMap<>();
				eval.put("experiment_id", experimentID);
				eval.put(PREPROCESSOR_FIELD, preprocessor);
				eval.put(CLASSIFIER_FIELD, classifier);
				eval.put("rmse", e.getScore());
				eval.put("time_train", e.getSolutionCandidate().getTimeToEvaluateCandidate());
				adapter.insert(experimentConfig.evaluationsTable(), eval);
			} catch (Exception e1) {
				L.error("Could not store hasco solution in database", e1);
			}
		}
	}

	private static void writeFile(final String fileName, final String value) {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(fileName)))) {
			bw.write(value);
		} catch (IOException e) {
			L.error("Could not write value to file {}: {}", fileName, value);
		}
	}
}
