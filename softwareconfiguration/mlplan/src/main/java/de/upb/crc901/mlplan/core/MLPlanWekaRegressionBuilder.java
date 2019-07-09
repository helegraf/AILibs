package de.upb.crc901.mlplan.core;

import java.io.File;
import java.io.IOException;

import de.upb.crc901.mlplan.multiclass.MLPlanClassifierConfig;
import de.upb.crc901.mlplan.multiclass.wekamlplan.IClassifierFactory;
import de.upb.crc901.mlplan.multiclass.wekamlplan.weka.WEKAPipelineFactory;
import jaicore.basic.MathExt;
import jaicore.basic.ResourceUtil;
import jaicore.ml.core.evaluation.measure.IMeasure;
import jaicore.ml.core.evaluation.measure.singlelabel.RootMeanSquaredErrorLoss;
import jaicore.ml.evaluation.evaluators.weka.factory.MonteCarloCrossValidationEvaluatorFactory;
import jaicore.ml.evaluation.evaluators.weka.splitevaluation.SimpleSLCSplitBasedClassifierEvaluator;
import jaicore.ml.weka.dataset.splitter.ArbitrarySplitter;
import jaicore.ml.weka.dataset.splitter.IDatasetSplitter;

public class MLPlanWekaRegressionBuilder extends AbstractMLPlanSingleLabelBuilder {

	private static final String RES_SSC_WEKA_REGRESSION = "resources/automl/searchmodels/weka/weka-regression.json";
	private static final String RES_SSC_RF_PREPROC = "resources/automl/searchmodels/weka/random-forest.json";
	private static final String RES_SSC_RF_ONLY = "resources/automl/searchmodels/weka/random-forest-clean.json";
	private static final String RES_SSC_TINY_WEKA_REGRESSION = "resources/automl/searchmodels/weka/tinytest_regression.json";
	private static final String RES_SSC_WEKA_REGRESSION_NOSMOREG = "resources/automl/searchmodels/weka/weka-regression_noSMOreg.json";
	
	private static final String RES_PREFERRED_COMPONENTS_REGRESSION = "resources/mlplan/weka-regression-preferenceList.txt";

	/* Default configuration values. */
	protected static final IMeasure<Double, Double> LOSS_FUNCTION = new RootMeanSquaredErrorLoss();
	private static final String DEF_REQUESTED_HASCO_INTERFACE = "AbstractClassifier";
	private static final IDatasetSplitter DEF_SELECTION_HOLDOUT_SPLITTER = new ArbitrarySplitter();
	private static final IClassifierFactory DEF_CLASSIFIER_FACTORY = new WEKAPipelineFactory();
	private static final File DEF_PREFERRED_COMPONENTS_REGRESSION = ResourceUtil.getResourceAsFile(RES_PREFERRED_COMPONENTS_REGRESSION);
	private static final MonteCarloCrossValidationEvaluatorFactory DEF_SEARCH_PHASE_EVALUATOR = new MonteCarloCrossValidationEvaluatorFactory()
			.withNumMCIterations(SEARCH_NUM_MC_ITERATIONS).withTrainFoldSize(SEARCH_TRAIN_FOLD_SIZE)
			.withSplitBasedEvaluator(new SimpleSLCSplitBasedClassifierEvaluator(LOSS_FUNCTION))
			.withDatasetSplitter(new ArbitrarySplitter());
	private static final MonteCarloCrossValidationEvaluatorFactory DEF_SELECTION_PHASE_EVALUATOR = new MonteCarloCrossValidationEvaluatorFactory()
			.withNumMCIterations(SELECTION_NUM_MC_ITERATIONS).withTrainFoldSize(SELECTION_TRAIN_FOLD_SIZE)
			.withSplitBasedEvaluator(new SimpleSLCSplitBasedClassifierEvaluator(LOSS_FUNCTION))
			.withDatasetSplitter(new ArbitrarySplitter());
	public MLPlanWekaRegressionBuilder() throws IOException {
		super();
		this.withSearchSpaceConfigFile(ResourceUtil.getResourceAsFile(RES_SSC_WEKA_REGRESSION));
		this.withPreferredComponentsFile(DEF_PREFERRED_COMPONENTS_REGRESSION);
		this.withRequestedInterface(DEF_REQUESTED_HASCO_INTERFACE);
		this.withClassifierFactory(DEF_CLASSIFIER_FACTORY);
		this.withDatasetSplitterForSearchSelectionSplit(DEF_SELECTION_HOLDOUT_SPLITTER);
		this.withSearchPhaseEvaluatorFactory(DEF_SEARCH_PHASE_EVALUATOR);
		this.withSelectionPhaseEvaluatorFactory(DEF_SELECTION_PHASE_EVALUATOR);
		this.setPerformanceMeasureName(LOSS_FUNCTION.getClass().getSimpleName());

		// /* configure blow-ups for MCCV */
		double blowUpInSelectionPhase = MathExt
				.round(1f / SEARCH_TRAIN_FOLD_SIZE * SELECTION_NUM_MC_ITERATIONS / SEARCH_NUM_MC_ITERATIONS, 2);
		this.getAlgorithmConfig().setProperty(MLPlanClassifierConfig.K_BLOWUP_SELECTION,
				String.valueOf(blowUpInSelectionPhase));
		double blowUpInPostprocessing = MathExt.round(
				(1 / (1 - this.getAlgorithmConfig().dataPortionForSelection())) / SELECTION_NUM_MC_ITERATIONS, 2);
		this.getAlgorithmConfig().setProperty(MLPlanClassifierConfig.K_BLOWUP_POSTPROCESS,
				String.valueOf(blowUpInPostprocessing));
	}

	public MLPlanWekaRegressionBuilder withWEKARegressionConfiguration() throws IOException {
		this.withSearchSpaceConfigFile(ResourceUtil.getResourceAsFile(RES_SSC_WEKA_REGRESSION));
		return this;
	}

	public MLPlanWekaRegressionBuilder withRandomForestAndPreprocessorsOnlyConfiguration() throws IOException {
		this.withSearchSpaceConfigFile(ResourceUtil.getResourceAsFile(RES_SSC_RF_PREPROC));
		return this;
	}

	public MLPlanWekaRegressionBuilder withRandomForestOnlyAndNoPreprocessorsConfiguration() throws IOException {
		this.withSearchSpaceConfigFile(ResourceUtil.getResourceAsFile(RES_SSC_RF_ONLY));
		return this;
	}

	public MLPlanWekaRegressionBuilder withTinyTestForRegressionConfiguration() throws IOException {
		this.withSearchSpaceConfigFile(ResourceUtil.getResourceAsFile(RES_SSC_TINY_WEKA_REGRESSION));
		return this;
	}

	public MLPlanWekaRegressionBuilder withWEKARegressionConfigurationNoSMO() throws IOException {
		this.withSearchSpaceConfigFile(ResourceUtil.getResourceAsFile(RES_SSC_WEKA_REGRESSION_NOSMOREG));
		return this;
	}
}
