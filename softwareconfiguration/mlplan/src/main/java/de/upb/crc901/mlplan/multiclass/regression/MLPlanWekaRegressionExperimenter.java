package de.upb.crc901.mlplan.multiclass.regression;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.aeonbits.owner.ConfigCache;
import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;

import de.upb.crc901.mlplan.core.AbstractMLPlanBuilder;
import de.upb.crc901.mlplan.core.MLPlan;
import de.upb.crc901.mlplan.core.MLPlanWekaBuilder;
import de.upb.crc901.mlplan.core.events.ClassifierFoundEvent;
import de.upb.crc901.mlplan.multiclass.wekamlplan.weka.model.MLPipeline;
import jaicore.basic.SQLAdapter;
import jaicore.basic.TimeOut;
import jaicore.concurrent.GlobalTimer;
import jaicore.experiments.ExperimentDBEntry;
import jaicore.experiments.ExperimentRunner;
import jaicore.experiments.IExperimentDatabaseHandle;
import jaicore.experiments.IExperimentIntermediateResultProcessor;
import jaicore.experiments.IExperimentSetEvaluator;
import jaicore.experiments.databasehandle.ExperimenterSQLHandle;
import jaicore.experiments.exceptions.ExperimentDBInteractionFailedException;
import jaicore.experiments.exceptions.ExperimentEvaluationFailedException;
import jaicore.experiments.exceptions.IllegalExperimentSetupException;
import jaicore.ml.WekaUtil;
import jaicore.ml.core.evaluation.measure.singlelabel.RootMeanSquaredErrorLoss;
import jaicore.ml.openml.OpenMLHelper;
import jaicore.ml.weka.dataset.splitter.ArbitrarySplitter;
import weka.classifiers.Classifier;
import weka.classifiers.evaluation.Evaluation;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader.ArffReader;
import weka.core.converters.ArffSaver;
import weka.filters.unsupervised.attribute.Reorder;

public class MLPlanWekaRegressionExperimenter implements IExperimentSetEvaluator {
	private static final String CLASSIFIER_FIELD = "classifier";
	private static final String PREPROCESSOR_FIELD = "preprocessor";

	private static final Logger L = LoggerFactory.getLogger(MLPlanWekaRegressionExperimenter.class);

	private static MLPlanWekaRegressionExperimenterConfig experimentConfig = ConfigCache
			.getOrCreate(MLPlanWekaRegressionExperimenterConfig.class);
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
		if (experimentConfig.evaluationsTable() == null) {
			throw new IllegalArgumentException("No evaluations table (db.evalTable) set in config");
		}
	}

	@Override
	public void evaluate(final ExperimentDBEntry experimentEntry,
			final IExperimentIntermediateResultProcessor processor) throws ExperimentEvaluationFailedException {
		try {
			this.adapter = new SQLAdapter(experimentConfig.getDBHost(), experimentConfig.getDBUsername(),
					experimentConfig.getDBPassword(), experimentConfig.getDBDatabaseName());
			experimentID = experimentEntry.getId();
			Map<String, String> experimentValues = experimentEntry.getExperiment().getValuesOfKeyFields();

			int datasetid =Integer.parseInt(experimentValues.get("dataset"));
			long seed = Long.parseLong(experimentValues.get("seed"));
			
			runmlplan(experimentValues, experimentEntry, processor, getTrainData(datasetid, seed), getTestData(datasetid, seed));
		} catch (Exception e) {
			throw new ExperimentEvaluationFailedException(e);
		}
	}

	public void runmlplan(Map<String, String> experimentValues, ExperimentDBEntry experimentEntry,
			IExperimentIntermediateResultProcessor processor, Instances train, Instances test) throws Exception {
		MLPlanWekaBuilder builder = AbstractMLPlanBuilder.forWeka();
		builder.withWEKARegressionConfiguration();
		builder.withPerformanceMeasure(new RootMeanSquaredErrorLoss());
		builder.withTimeOut(new TimeOut(Integer.parseInt(experimentValues.get("timeout")), TimeUnit.SECONDS));
		builder.withNodeEvaluationTimeOut(
				new TimeOut(Integer.parseInt(experimentValues.get("evaluationTimeout")), TimeUnit.SECONDS));
		builder.withCandidateEvaluationTimeOut(
				new TimeOut(Integer.parseInt(experimentValues.get("evaluationTimeout")), TimeUnit.SECONDS));
		builder.withNumCpus(experimentEntry.getExperiment().getNumCPUs());

		MLPlan mlplan = new MLPlan(builder, train);
		mlplan.setLoggerName("mlplan");
		mlplan.setTimeout(new Integer(experimentValues.get("timeout")), TimeUnit.SECONDS);
		mlplan.setRandomSeed(new Integer(experimentValues.get("seed")));
		mlplan.registerListener(this);

		L.info("Build mlplan classifier");
		Classifier optimizedClassifier = mlplan.call();

		L.info("Open timeout tasks: {}", GlobalTimer.getInstance().getActiveTasks());

		Evaluation eval = new Evaluation(train);
		L.info("Assess test performance...");
		eval.evaluateModel(optimizedClassifier, test);

		L.info("Test error was {}. Internally estimated error for this model was {}", eval.rootMeanSquaredError(),
				mlplan.getInternalValidationErrorOfSelectedClassifier());
		Map<String, Object> results = new HashMap<>();
		results.put("rmse", eval.rootMeanSquaredError());
		if (mlplan.getSelectedClassifier() instanceof MLPipeline) {
			results.put(CLASSIFIER_FIELD, WekaUtil
					.getClassifierDescriptor(((MLPipeline) mlplan.getSelectedClassifier()).getBaseClassifier()));
			results.put(PREPROCESSOR_FIELD,
					((MLPipeline) mlplan.getSelectedClassifier()).getPreprocessors().toString());
		} else {
			results.put(CLASSIFIER_FIELD, WekaUtil.getClassifierDescriptor(mlplan.getSelectedClassifier()));
		}

		processor.processResults(results);
		L.info("Experiment done.");
	}
	
	public Instances getTrainData(int datasetid, long seed) throws IOException {
		return getData(datasetid, seed, "train");
	}
	
	public Instances getTestData(int datasetid, long seed) throws IOException {
		return getData(datasetid, seed, "test");
	}
	
	public Instances getData(int datasetid, long seed, String traintest) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(getFileString(datasetid, seed, traintest)));
		ArffReader arff = new ArffReader(reader);
		Instances data = arff.getData();
		data.setClassIndex(data.numAttributes()-1);
		
		return data;
	}

	public void createAndSaveSplit(int datasetid, long seed) throws Exception {
		// Get data
		Instances data = OpenMLHelper.getInstancesWithIndexSet(datasetid, "abc");

		// Reorder attributes
		Reorder filter = new Reorder();
		StringBuilder optionsString = new StringBuilder();
		
		for (int i = 0; i < data.numAttributes(); i++) {
			if (i != data.classIndex()) {
				optionsString.append(i + 1 + ",");
			}			
		}
		optionsString.append(data.classIndex()+1);
		filter.setInputFormat(data);
		filter.setOptions(new String[] { "-R", optionsString.toString() });

		for (int i = 0; i < data.numInstances(); i++) {
			filter.input(data.instance(i));
		}
		
		filter.batchFinished();
		Instances newData = filter.getOutputFormat();
		Instance processed;
		while ((processed = filter.output()) != null) {
			newData.add(processed);
		}

		List<Instances> randomSplit = new ArbitrarySplitter().split(newData, seed, .7);
		Instances train = randomSplit.get(0);
		Instances test = randomSplit.get(1);

		saveData(train, getFileString(datasetid, seed, "train"));
		saveData(test, getFileString(datasetid, seed, "test"));
	}
	
	private void saveData(Instances data, String fileName) throws IOException {
		ArffSaver saver = new ArffSaver();
		saver.setInstances(data);
		saver.setFile(new File(fileName));
		saver.writeBatch();
	}
	
	private String getFileString(int datasetid, long seed, String traintest) {
		return "resources/datasplits_regression/" + datasetid + "_" + seed + "_" + traintest + ".arff";
	}

	@Subscribe
	public void rcvHASCOSolutionEvent(final ClassifierFoundEvent e) {
		if (adapter != null) {
			try {
				String classifier = "";
				String preprocessor = "";
				if (e.getSolutionCandidate() instanceof MLPipeline) {
					MLPipeline solution = (MLPipeline) e.getSolutionCandidate();
					preprocessor = solution.getPreprocessors().isEmpty() ? ""
							: solution.getPreprocessors().get(0).toString();
					classifier = WekaUtil.getClassifierDescriptor(solution.getBaseClassifier());
				} else {
					classifier = WekaUtil.getClassifierDescriptor(e.getSolutionCandidate());
				}
				Map<String, Object> eval = new HashMap<>();
				eval.put("experiment_id", experimentID);
				eval.put(PREPROCESSOR_FIELD, preprocessor);
				eval.put(CLASSIFIER_FIELD, classifier);
				eval.put("rmse", e.getScore());
				eval.put("time_train", e.getTimestamp());
				adapter.insert(experimentConfig.evaluationsTable(), eval);
			} catch (Exception e1) {
				L.error("Could not store hasco solution in database", e1);
			}
		}
	}

	public static void main(String[] args)
			throws ExperimentDBInteractionFailedException, IllegalExperimentSetupException {
		File configFile = new File("conf/mlplan-weka-regression.properties");
		IExperimentDatabaseHandle dbHandle = new ExperimenterSQLHandle(experimentConfig);
		ExperimentRunner runner = new ExperimentRunner(experimentConfig,
				new MLPlanWekaRegressionExperimenter(configFile), dbHandle);
		runner.randomlyConductExperiments(1, false);
	}
}
