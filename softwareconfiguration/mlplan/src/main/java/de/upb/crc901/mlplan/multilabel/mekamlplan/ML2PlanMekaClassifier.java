package de.upb.crc901.mlplan.multilabel.mekamlplan;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import de.upb.crc901.mlplan.multiclass.MLPlanClassifierConfig;
import de.upb.crc901.mlplan.multilabel.ML2PlanClassifierConfig;
import hasco.core.HASCOSolutionCandidate;
import hasco.exceptions.ComponentInstantiationFailedException;
import hasco.model.Component;
import hasco.model.ComponentInstance;
import hasco.optimizingfactory.OptimizingFactory;
import hasco.optimizingfactory.OptimizingFactoryProblem;
import hasco.serialization.ComponentLoader;
import hasco.variants.forwarddecomposition.twophase.TwoPhaseHASCO;
import hasco.variants.forwarddecomposition.twophase.TwoPhaseHASCOFactory;
import hasco.variants.forwarddecomposition.twophase.TwoPhaseSoftwareConfigurationProblem;
import jaicore.basic.ILoggingCustomizable;
import jaicore.basic.IObjectEvaluator;
import jaicore.basic.MathExt;
import jaicore.basic.algorithm.AAlgorithm;
import jaicore.basic.algorithm.AlgorithmExecutionCanceledException;
import jaicore.basic.algorithm.AlgorithmState;
import jaicore.basic.algorithm.events.AlgorithmEvent;
import jaicore.basic.algorithm.events.AlgorithmInitializedEvent;
import jaicore.basic.algorithm.events.SolutionCandidateFoundEvent;
import jaicore.basic.algorithm.exceptions.AlgorithmException;
import jaicore.basic.algorithm.exceptions.ObjectEvaluationFailedException;
import jaicore.ml.WekaUtil;
import jaicore.ml.core.evaluation.measure.multilabel.MonteCarloCrossValidationEvaluator;
import jaicore.ml.core.evaluation.measure.multilabel.MultiLabelMeasureBuilder;
import jaicore.ml.core.evaluation.measure.multilabel.MultiLabelPerformanceMeasure;
import jaicore.planning.hierarchical.algorithms.forwarddecomposition.graphgenerators.tfd.TFDNode;
import jaicore.search.algorithms.standard.bestfirst.nodeevaluation.AlternativeNodeEvaluator;
import jaicore.search.algorithms.standard.bestfirst.nodeevaluation.INodeEvaluator;
import jaicore.search.core.interfaces.GraphGenerator;
import meka.classifiers.multilabel.MultiLabelClassifier;
import weka.classifiers.Classifier;
import weka.core.Capabilities;
import weka.core.Capabilities.Capability;
import weka.core.CapabilitiesHandler;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Option;
import weka.core.OptionHandler;

/**
 * A MEKA classifier wrapping the functionality of ML-Plan where the constructed
 * object is a MEKA classifier.
 *
 * It implements the algorithm interface with itself (with modified state) as an
 * output
 *
 * @author wever, fmohr 
 *
 */
public abstract class ML2PlanMekaClassifier extends AAlgorithm<Instances, Classifier> implements Classifier, CapabilitiesHandler, OptionHandler,
		ILoggingCustomizable, MultiLabelClassifier {

	/** Logger for controlled output. */
	private Logger logger = LoggerFactory.getLogger(ML2PlanMekaClassifier.class);
	private String loggerName;

	private final File componentFile;
	private final Collection<Component> components;
	private final MultiLabelClassifierFactory factory;
	private INodeEvaluator<TFDNode, Double> preferredNodeEvaluator;
	private final MultiLabelPerformanceMeasure performanceMeasure;
	private final ML2PlanClassifierConfig config;
	private Classifier selectedClassifier;
	private double internalValidationErrorOfSelectedClassifier;
	private final EventBus eventBus = new EventBus();
	private OptimizingFactory<TwoPhaseSoftwareConfigurationProblem, MultiLabelClassifier, HASCOSolutionCandidate<Double>, Double> optimizingFactory;

	private AlgorithmState state = AlgorithmState.created;
	private Instances data = null;

	public ML2PlanMekaClassifier(final File componentFile, final MultiLabelClassifierFactory factory,
			final MultiLabelPerformanceMeasure performanceMeasure, final ML2PlanClassifierConfig config, Instances data)
			throws IOException {
		super(config, data);
		this.componentFile = componentFile;
		this.components = new ComponentLoader(componentFile).getComponents();
		this.factory = factory;
		this.performanceMeasure = performanceMeasure;
		this.config = config;
	}

	@Override
	public AlgorithmEvent nextWithException() throws InterruptedException, AlgorithmExecutionCanceledException, TimeoutException, AlgorithmException {
		switch (this.state) {
		case created:

			/* check whether data has been set */
			if (this.data == null) {
				throw new IllegalArgumentException("Data to work on is still NULL");
			}

			/* check number of CPUs assigned */
			if (this.config.cpus() < 1) {
				throw new IllegalStateException("Cannot generate search where number of CPUs is " + this.config.cpus());
			}

			/* set up exact splits */
			float selectionDataPortion = this.config.dataPortionForSelection();
			Instances dataShownToSearch;
			if (selectionDataPortion > 0) {
				Collection<Integer>[] instancesInFolds = WekaUtil.getArbitrarySplit(this.data,
						new Random(this.config.randomSeed()), selectionDataPortion);
				List<Instances> folds = WekaUtil.realizeSplit(data, instancesInFolds);
				dataShownToSearch = folds.get(1);
			} else {
				dataShownToSearch = this.data;
			}

			if (dataShownToSearch.isEmpty()) {
				throw new IllegalStateException("Cannot search on no data.");
			}

			/* dynamically compute blow-ups */
			double blowUpInSelectionPhase = MathExt.round(1f / this.config.getMCCVTrainFoldSizeDuringSearch()
					* this.config.numberOfMCIterationsDuringSelection()
					/ this.config.numberOfMCIterationsDuringSearch(), 2);
			double blowUpInPostprocessing = MathExt.round((1 / (1 - this.config.dataPortionForSelection()))
					/ this.config.numberOfMCIterationsDuringSelection(), 2);
			this.config.setProperty(MLPlanClassifierConfig.K_BLOWUP_SELECTION, String.valueOf(blowUpInSelectionPhase));
			this.config.setProperty(MLPlanClassifierConfig.K_BLOWUP_POSTPROCESS,
					String.valueOf(blowUpInPostprocessing));

			/* communicate the parameters with which ML-Plan will run */
			if (logger.isInfoEnabled()) {
				this.logger.info(
						"Starting ML2-Plan with the following setup:\n\tDataset: {}\n\tTarget: {}\n\tCPUs: {}\n\tTimeout: {}s\n\tTimeout for single candidate evaluation: {}s\n\tTimeout for node evaluation: {}s\n\tRandom Completions per node evaluation: {}\n\tPortion of data for selection phase: {}%\n\tMCCV for search: {} iterations with {}% for training\n\tMCCV for select: {} iterations with {}% for training\n\tBlow-ups are {} for selection phase and {} for post-processing phase.",
						this.data.relationName(), this.performanceMeasure, this.config.cpus(), this.config.timeout(),
						this.config.timeoutForCandidateEvaluation() / 1000,
						this.config.timeoutForNodeEvaluation() / 1000, this.config.numberOfRandomCompletions(),
						MathExt.round(this.config.dataPortionForSelection() * 100, 2),
						this.config.numberOfMCIterationsDuringSearch(),
						(int) (100 * this.config.getMCCVTrainFoldSizeDuringSearch()),
						this.config.numberOfMCIterationsDuringSelection(),
						(int) (100 * this.config.getMCCVTrainFoldSizeDuringSelection()),
						this.config.expectedBlowupInSelection(), this.config.expectedBlowupInPostprocessing());
				this.logger.info("Using the following preferred node evaluator: {}", this.preferredNodeEvaluator);
			}

			/* create HASCO problem */
			IObjectEvaluator<MultiLabelClassifier, Double> searchBenchmark = new MonteCarloCrossValidationEvaluator(
					MultiLabelMeasureBuilder.getEvaluator(this.performanceMeasure),
					this.config.numberOfMCIterationsDuringSearch(), dataShownToSearch,
					this.config.getMCCVTrainFoldSizeDuringSearch(), this.config.randomSeed());
			IObjectEvaluator<ComponentInstance, Double> wrappedSearchBenchmark = c -> {
				double result = 1.0;
				try {
					System.out.println("Evaluating solution (search)");
					result = searchBenchmark.evaluate(this.factory.getComponentInstantiation(c));
				} catch (Exception e) {
					logger.info("Exception while evaluating multilabel pipeline. Message: {}", e.getMessage());
				}
				System.out.println("Evaluate pipeline with result " + result);
				return result;
			};

			IObjectEvaluator<MultiLabelClassifier, Double> selectionBenchmark = new IObjectEvaluator<MultiLabelClassifier, Double>() {

				@Override
				public Double evaluate(final MultiLabelClassifier object) throws ObjectEvaluationFailedException {

					/* first conduct MCCV */
					MonteCarloCrossValidationEvaluator mccv = new MonteCarloCrossValidationEvaluator(
							MultiLabelMeasureBuilder.getEvaluator(ML2PlanMekaClassifier.this.performanceMeasure),
							ML2PlanMekaClassifier.this.config.numberOfMCIterationsDuringSelection(),
							ML2PlanMekaClassifier.this.data,
							ML2PlanMekaClassifier.this.config.getMCCVTrainFoldSizeDuringSelection(),
							config.randomSeed());
					

					/* now retrieve .75-percentile from stats */
					double mean = mccv.evaluate(object);
					double percentile = mccv.getPercentile_75();
					ML2PlanMekaClassifier.this.logger.info(
							"Select {} as .75-percentile where {} would have been the mean. Samples size of MCCV was {}",
							percentile, mean, mccv.getN());
					return percentile;
				}
			};
			IObjectEvaluator<ComponentInstance, Double> wrappedSelectionBenchmark = c -> {
				try {
					return selectionBenchmark
							.evaluate(this.factory.getComponentInstantiation(c));
				} catch (ComponentInstantiationFailedException e) {
					throw new ObjectEvaluationFailedException(e, "Evaluation of composition failed as the component instantiation could not be built.");
				}
			};
			TwoPhaseSoftwareConfigurationProblem problem;
			try {
				problem = new TwoPhaseSoftwareConfigurationProblem(this.componentFile,
						"MLClassifier", wrappedSearchBenchmark, wrappedSelectionBenchmark);
				
				/* configure and start optimizing factory */
				OptimizingFactoryProblem<TwoPhaseSoftwareConfigurationProblem, MultiLabelClassifier, Double> optimizingFactoryProblem = new OptimizingFactoryProblem<>(
						this.factory, problem);
				TwoPhaseHASCOFactory hascoFactory = new TwoPhaseHASCOFactory();
//				hascoFactory.setPreferredNodeEvaluator(new AlternativeNodeEvaluator<TFDNode, Double>(
//						this.getSemanticNodeEvaluator(dataShownToSearch), this.preferredNodeEvaluator));
				hascoFactory.setConfig(this.config);
				this.optimizingFactory = new OptimizingFactory<>(optimizingFactoryProblem, hascoFactory);
				this.optimizingFactory.setLoggerName(this.loggerName + ".2phasehasco");
				this.optimizingFactory.setTimeout(this.config.timeout(), TimeUnit.MILLISECONDS);
				this.optimizingFactory.setNumCPUs(this.config.cpus());
				this.optimizingFactory.registerListener(this);
				this.optimizingFactory.init();

				/* set state to active */
				return this.activate();
			} catch (IOException e) {
				throw new AlgorithmException(e, "Could not create TwoPhase configuration problem.");
			}

		case active:

			/* train the classifier returned by the optimizing factory */
			long startOptimizationTime = System.currentTimeMillis();
			this.selectedClassifier = this.optimizingFactory.call();
			this.internalValidationErrorOfSelectedClassifier = this.optimizingFactory.getPerformanceOfObject();
			long startBuildTime = System.currentTimeMillis();
			try {
				this.selectedClassifier.buildClassifier(this.data);
			} catch (Exception e) {
				throw new AlgorithmException(e, "Training the classifier failed!");
			}
			long endBuildTime = System.currentTimeMillis();
			this.logger.info(
					"Selected model has been built on entire dataset. Build time of chosen model was {}ms. Total construction time was {}ms",
					endBuildTime - startBuildTime, endBuildTime - startOptimizationTime);
			this.terminate();

		default:
			throw new IllegalStateException("Cannot do anything in state " + this.state);
		}
	}

	@Override
	public Classifier call() throws InterruptedException, AlgorithmExecutionCanceledException, TimeoutException, AlgorithmException {
		while (this.hasNext()) {
			this.nextWithException();
		}
		return this;
	}

	protected abstract INodeEvaluator<TFDNode, Double> getSemanticNodeEvaluator(Instances data);

	@Override
	public void buildClassifier(final Instances data) throws Exception {
		this.setData(data);
		this.call();
	}

	@Override
	public double classifyInstance(final Instance instance) throws Exception {
		if (this.selectedClassifier == null) {
			throw new IllegalStateException("Classifier has not been built yet.");
		}
		return this.selectedClassifier.classifyInstance(instance);
	}

	@Override
	public double[] distributionForInstance(final Instance instance) throws Exception {
		if (this.selectedClassifier == null) {
			throw new IllegalStateException("Classifier has not been built yet.");
		}

		return this.selectedClassifier.distributionForInstance(instance);
	}

	@Override
	public Capabilities getCapabilities() {
		Capabilities result = new Capabilities(this);
		result.disableAll();

		// attributes
		result.enable(Capability.NOMINAL_ATTRIBUTES);
		result.enable(Capability.NUMERIC_ATTRIBUTES);
		result.enable(Capability.DATE_ATTRIBUTES);
		result.enable(Capability.STRING_ATTRIBUTES);
		result.enable(Capability.RELATIONAL_ATTRIBUTES);
		result.enable(Capability.MISSING_VALUES);

		// class
		result.enable(Capability.NOMINAL_CLASS);
		result.enable(Capability.NUMERIC_CLASS);
		result.enable(Capability.DATE_CLASS);
		result.enable(Capability.MISSING_CLASS_VALUES);

		// instances
		result.setMinimumNumberInstances(1);
		return result;
	}

	@Override
	public Enumeration<Option> listOptions() {
		return null;
	}

	@Override
	public void setOptions(final String[] options) throws Exception {
		for (int i = 0; i < options.length; i += 2) {
			switch (options[i].toLowerCase()) {
			case "-t":
				this.setTimeout(Integer.parseInt(options[i + 1]));
				break;
			case "-r":
				this.setRandomSeed(Integer.parseInt(options[i + 1]));
				break;
			default:
				throw new IllegalArgumentException("Unknown option " + options[i] + ".");
			}
		}
	}

	@Override
	public String[] getOptions() {
		return new String[0];
	}

	@Override
	public void setLoggerName(final String name) {
		this.loggerName = name;
		this.logger.info("Switching logger name to {}", name);
		this.logger = LoggerFactory.getLogger(name);
		this.logger.info("Switched ML-Plan logger to {}", name);

	}

	public void setPortionOfDataForPhase2(final float portion) {
		this.config.setProperty(MLPlanClassifierConfig.SELECTION_PORTION, String.valueOf(portion));
	}

	public void activateVisualization() {
		this.config.setProperty(MLPlanClassifierConfig.K_VISUALIZE, String.valueOf(true));
	}

	public void deactivateVisualization() {
		this.config.setProperty(MLPlanClassifierConfig.K_VISUALIZE, String.valueOf(false));
	}

	@Override
	public String getLoggerName() {
		return this.loggerName;
	}

	public INodeEvaluator<TFDNode, Double> getPreferredNodeEvaluator() {
		return this.preferredNodeEvaluator;
	}

	public void setPreferredNodeEvaluator(final INodeEvaluator<TFDNode, Double> preferredNodeEvaluator) {
		this.preferredNodeEvaluator = preferredNodeEvaluator;
	}

	public ML2PlanClassifierConfig getConfig() {
		return this.config;
	}

	public File getComponentFile() {
		return this.componentFile;
	}

	public void setTimeoutForSingleSolutionEvaluation(final int timeout) {
		this.config.setProperty(ML2PlanClassifierConfig.K_RANDOM_COMPLETIONS_TIMEOUT_PATH,
				String.valueOf(timeout * 1000));
	}

	public void setTimeoutForNodeEvaluation(final int timeout) {
		this.config.setProperty(ML2PlanClassifierConfig.K_RANDOM_COMPLETIONS_TIMEOUT_NODE,
				String.valueOf(timeout * 1000));
	}

//	@Override
//	public void setTimeout(long timeout, TimeUnit timeUnit) {
//		// TODO Auto-generated method stub
//		
//	}

	public void setTimeout(final int seconds) {
		this.config.setProperty(ML2PlanClassifierConfig.K_TIMEOUT, String.valueOf(seconds*1000));
	}

//	@Override
//	public void setTimeout(final TimeOut timeout) {
//	
//	}
//
//	@Override
//	public TimeOut getTimeout() {
//		return null;
//	}

	public Collection<Component> getComponents() {
		return Collections.unmodifiableCollection(this.components);
	}

	public void setRandomSeed(final int seed) {
		this.config.setProperty(MLPlanClassifierConfig.K_RANDOM_SEED, String.valueOf(seed));
	}

	@Override
	public void setNumCPUs(final int num) {
		if (num < 1) {
			throw new IllegalArgumentException("Need to work with at least one CPU");
		}
		if (num > Runtime.getRuntime().availableProcessors()) {
			this.logger.warn("Warning, configuring {} CPUs where the system has only {}", num,
					Runtime.getRuntime().availableProcessors());
		}
		this.config.setProperty(MLPlanClassifierConfig.K_CPUS, String.valueOf(num));
	}

	@Subscribe
	public void receiveSolutionEvent(final SolutionCandidateFoundEvent<HASCOSolutionCandidate<Double>> event) {
		HASCOSolutionCandidate<Double> solution = event.getSolutionCandidate();
		try {
			this.logger.info("Received new solution {} with score {} and evaluation time {}ms",
					this.factory.getComponentInstantiation(solution.getComponentInstance()), solution.getScore(),
					solution.getTimeToEvaluateCandidate());
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.eventBus.post(event);
	}

	public void registerListenerForSolutionEvaluations(final Object listener) {
		this.eventBus.register(listener);
	}

	public Classifier getSelectedClassifier() {
		return this.selectedClassifier;
	}

	public GraphGenerator<TFDNode, String> getGraphGenerator() {
		if (this.state == AlgorithmState.created) {
			this.init();
		}
		TwoPhaseHASCO twoPhaseHASCO = ((TwoPhaseHASCO) this.optimizingFactory.getOptimizer());
		return twoPhaseHASCO.getGraphGenerator();
	}

	public void setData(final Instances data) {
		this.data = data;
	}

	public Instances getData() {
		return this.data;
	}

	public AlgorithmInitializedEvent init() {
		AlgorithmEvent e = null;
		while (this.hasNext()) {
			e = this.next();
			if (e instanceof AlgorithmInitializedEvent) {
				return (AlgorithmInitializedEvent) e;
			}
		}
		throw new IllegalStateException("Could not complete initialization");
	}

	public double getInternalValidationErrorOfSelectedClassifier() {
		return this.internalValidationErrorOfSelectedClassifier;
	}
	
	@Override
	public void setDebug(boolean debug) {
		throw new UnsupportedOperationException("Cannot change log level dynamically for ML2Plan.");
	}

	@Override
	public boolean getDebug() {
		return logger.isDebugEnabled();
	}

	@Override
	public String debugTipText() {
		return "Multilabel Classifier using MLPlan";
	}

	@Override
	public String getModel() {
		if (selectedClassifier != null) {
			return selectedClassifier.toString();
		} else {
			return "No model built yet.";
		}
	}

}