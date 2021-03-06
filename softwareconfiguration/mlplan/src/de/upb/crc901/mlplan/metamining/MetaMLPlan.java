package de.upb.crc901.mlplan.metamining;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

import org.aeonbits.owner.ConfigCache;
import org.apache.commons.lang3.time.StopWatch;

import com.google.common.eventbus.EventBus;

import de.upb.crc901.mlplan.metamining.databaseconnection.ExperimentRepository;
import de.upb.crc901.mlplan.multiclass.MLPlanClassifierConfig;
import de.upb.crc901.mlplan.multiclass.wekamlplan.MLPlanWekaClassifier;
import de.upb.crc901.mlplan.multiclass.wekamlplan.weka.MLPipelineComponentInstanceFactory;
import de.upb.crc901.mlplan.multiclass.wekamlplan.weka.WEKAPipelineFactory;
import de.upb.crc901.mlplan.multiclass.wekamlplan.weka.model.MLPipeline;
import hasco.core.Util;
import hasco.metamining.MetaMinerBasedSorter;
import hasco.model.Component;
import hasco.model.ComponentInstance;
import jaicore.ml.core.evaluation.measure.singlelabel.ZeroOneLoss;
import jaicore.ml.metafeatures.GlobalCharacterizer;
import jaicore.planning.graphgenerators.task.tfd.TFDNode;
import jaicore.search.algorithms.standard.AbstractORGraphSearch;
import jaicore.search.algorithms.standard.bestfirst.nodeevaluation.INodeEvaluator;
import jaicore.search.algorithms.standard.lds.BestFirstLimitedDiscrepancySearchFactory;
import jaicore.search.algorithms.standard.lds.NodeOrderList;
import jaicore.search.model.other.EvaluatedSearchGraphPath;
import jaicore.search.model.other.SearchGraphPath;
import jaicore.search.model.probleminputs.NodeRecommendedTree;
import jaicore.search.model.travesaltree.Node;
import jaicore.search.structure.graphgenerator.ReducedGraphGenerator;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Instances;

public class MetaMLPlan extends AbstractClassifier {

	/**
	 * Generated by Eclipse
	 */
	private static final long serialVersionUID = 4772178784402396834L;

	// Search components
	private AbstractORGraphSearch<NodeRecommendedTree<TFDNode, String>, EvaluatedSearchGraphPath<TFDNode, String, NodeOrderList>, TFDNode, String, NodeOrderList, Node<TFDNode, NodeOrderList>, String> lds;
	private WEKAMetaminer metaMiner;
	WEKAPipelineFactory factory = new WEKAPipelineFactory();

	// Search configuration
	private long timeoutInSeconds = 60;
	private long safetyInSeconds = 1;
	private int CPUs = 1;
	private String metaFeatureSetName = "all";
	private String datasetSetName = "all";
	private int seed = 0;

	// Search results
	private Classifier bestModel;
	private Collection<Component> components;

	// For intermediate results
	private EventBus eventBus = new EventBus();

	public MetaMLPlan(Instances data) throws IOException {
		this(new File("conf/automl/searchmodels/weka/weka-all-autoweka.json"), data);
	}

	public MetaMLPlan(File configFile, Instances data) throws IOException {
		// Prepare mlPlan to get a graphGenerator
		MLPlanWekaClassifier mlPlan = new MLPlanWekaClassifier(configFile, factory, null,
				ConfigCache.getOrCreate(MLPlanClassifierConfig.class)) {
			@Override
			protected INodeEvaluator<TFDNode, Double> getSemanticNodeEvaluator(Instances data) {
				return null;
			}
		};
		mlPlan.setData(data);

		// Set search components except lds
		this.components = mlPlan.getComponents();
		this.metaMiner = new WEKAMetaminer(mlPlan.getComponentParamRefinements());

		// Get lds
		BestFirstLimitedDiscrepancySearchFactory<TFDNode, String, NodeOrderList> ldsFactory = new BestFirstLimitedDiscrepancySearchFactory<>();
		NodeRecommendedTree<TFDNode, String> problemInput = new NodeRecommendedTree<>(
				new ReducedGraphGenerator<>(mlPlan.getGraphGenerator()),
				new MetaMinerBasedSorter(metaMiner, mlPlan.getComponents()));
		ldsFactory.setProblemInput(problemInput);
		this.lds = ldsFactory.getAlgorithm();

		// LimitedDiscrepancySearchFactory<TFDNode, String, NodeOrderList> factory = new
		// LimitedDiscrepancySearchFactory<>();
		// factory.setProblemInput(problemInput);
		// this.lds = factory.getAlgorithm();
		// VisualizationWindow window = new VisualizationWindow(lds);
		// window.setTooltipGenerator(new NodeTooltipGenerator<>(new
		// TFDTooltipGenerator<>()));

	}

	public void buildMetaComponents(String host, String user, String password) throws Exception {
		ExperimentRepository repo = new ExperimentRepository(host, user, password,
				new MLPipelineComponentInstanceFactory(components), CPUs, metaFeatureSetName, datasetSetName);
		metaMiner.build(repo.getDistinctPipelines(), repo.getDatasetCahracterizations(),
				repo.getPipelineResultsOnDatasets());
	}

	public void buildMetaComponents(String host, String user, String password, int limit) throws Exception {
		// TODO maybe some sophisticated selection for limit? / temporary method
		System.out.println("MetaMLPlan: Get past experiment data from data base and build MetaMiner.");
		ExperimentRepository repo = new ExperimentRepository(host, user, password,
				new MLPipelineComponentInstanceFactory(components), CPUs, metaFeatureSetName, datasetSetName);
		repo.setLimit(limit);
		metaMiner.build(repo.getDistinctPipelines(), repo.getDatasetCahracterizations(),
				repo.getPipelineResultsOnDatasets());
	}

	@Override
	public void buildClassifier(Instances data) throws Exception {
		// Start timer to interrupt search if it takes to long
//		TimerTask tt = new TimerTask() {
//
//			@Override
//			public void run() {
//				System.out.println("MetaMLPlan: Interrupting search because time is running out.");
//				lds.cancel();
//			}
//		};
//
//		// Start timer that takes into account training time of the best model as well
//		new Timer().schedule(tt, (timeoutInSeconds - safetyInSeconds) * 1000);
		StopWatch totalTimer = new StopWatch();
		totalTimer.start();

		// Characterize data set and give to meta miner
		System.out.println("MetaMLPlan: Characterizing data set");
		metaMiner.setDataSetCharacterization(new GlobalCharacterizer().characterize(data));

		// Preparing the split for validating pipelines
		System.out.println("MetaMLPlan: Preparing validation split");
		jaicore.ml.evaluation.evaluators.weka.MonteCarloCrossValidationEvaluator mccv = new jaicore.ml.evaluation.evaluators.weka.MonteCarloCrossValidationEvaluator(
				new ZeroOneLoss(),  5, data, .7f, seed);

		// Search for solutions
		System.out.println("MetaMLPlan: Searching for solutions");
		StopWatch trainingTimer = new StopWatch();
		bestModel = null;
		double bestScore = 1;
		double bestModelMaxTrainingTime = 0;

		while (!lds.isCanceled()) {
			try {
				SearchGraphPath<TFDNode, String> searchGraphPath;
				try {
					searchGraphPath = lds.nextSolution();
				} catch (NoSuchElementException e) {
					System.out.println("MetaMLPlan: Finish search (Exhaustive search conducted).");
					break;
				}

				List<TFDNode> solution = searchGraphPath.getNodes();

				if (solution == null) {
					System.out.println("MetaMLPlan: Ran out of solutions. Search is over.");
					break;
				}

				// Prepare pipeline
				ComponentInstance ci = Util.getSolutionCompositionFromState(components,
						solution.get(solution.size() - 1).getState(), true);
				MLPipeline pl = factory.getComponentInstantiation(ci);

				// Evaluate pipeline
				trainingTimer.reset();
				trainingTimer.start();
				System.out.println("MetaMLPlan: Evaluate Pipeline: " + pl);
				double score = mccv.evaluate(pl);
				System.out.println("MetaMLPlan: Pipeline Score: " + score);
				trainingTimer.stop();

				eventBus.post(new IntermediateSolutionEvent(pl, score, System.currentTimeMillis()));

				// Check if better than previous best
				System.out.println(score + " " + pl);
				if (score < bestScore) {
					bestModel = pl;
					bestScore = score;
				}
				if (trainingTimer.getTime() > bestModelMaxTrainingTime) {
					bestModelMaxTrainingTime = trainingTimer.getTime();
				}

				// Check if enough time remaining to re-train the current best model on the
				// whole training data
				if ((timeoutInSeconds - safetyInSeconds)
						* 1000 <= (totalTimer.getTime() + bestModelMaxTrainingTime)) {
					System.out.println(
							"MetaMLPlan: Stopping search to train best model on whole training data which is expected to take "
									+ bestModelMaxTrainingTime + " ms.");
					break;
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		Thread finalEval = new Thread() {

			@Override
			public void run() {
				System.out.println("MetaMLPlan: Evaluating best model on whole training data (" + bestModel + ")");
				try {
					bestModel.buildClassifier(data);
				} catch (Exception e) {
					bestModel = null;
					System.out.println("Evaluation of best model failed with exception.");
					e.printStackTrace();
				}
			}
		};

		TimerTask newT = new TimerTask() {
			@Override
			public void run() {
				System.out.println("MetaMLPlan: Interrupt building of final classifier because time is running out.");
				finalEval.interrupt();
			}
		};

		// Start timer that interrupts the final training
		try {
			new Timer().schedule(newT,
					(long) (timeoutInSeconds * 1000 - safetyInSeconds * 1000 - totalTimer.getTime()));
		} catch (IllegalArgumentException e) {
			System.out.println("No time anymore to start evaluation of final model. Abort search.");
			return;
		}
		finalEval.start();
		finalEval.join();

		System.out.println("MetaMLPlan: Ready. Best solution: " + bestModel);
	}

	@Override
	public double classifyInstance(final Instance instance) throws Exception {
		return bestModel.classifyInstance(instance);
	}

	public void registerListenerForIntermediateSolutions(Object listener) {
		eventBus.register(listener);
	}

	public void setTimeOutInSeconds(int timeOutInSeconds) {
		this.timeoutInSeconds = timeOutInSeconds;
	}

	public void setMetaFeatureSetName(String metaFeatureSetName) {
		this.metaFeatureSetName = metaFeatureSetName;
	}

	public void setDatasetSetName(String datasetSetName) {
		this.datasetSetName = datasetSetName;
	}

	public void setCPUs(int cPUs) {
		CPUs = cPUs;
	}

	public WEKAMetaminer getMetaMiner() {
		return metaMiner;
	}

	public void setSeed(int seed) {
		this.seed = seed;
	}
}
