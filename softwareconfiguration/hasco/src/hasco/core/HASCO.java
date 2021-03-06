package hasco.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.aeonbits.owner.ConfigCache;
import org.slf4j.Logger;

import hasco.events.HASCOSearchInitializedEvent;
import hasco.events.HASCOSolutionEvent;
import hasco.model.Component;
import hasco.model.ComponentInstance;
import hasco.model.Parameter;
import hasco.model.ParameterRefinementConfiguration;
import hasco.optimizingfactory.SoftwareConfigurationAlgorithm;
import hasco.reduction.HASCOReduction;
import jaicore.basic.ILoggingCustomizable;
import jaicore.basic.algorithm.AlgorithmEvent;
import jaicore.basic.algorithm.AlgorithmFinishedEvent;
import jaicore.basic.algorithm.AlgorithmInitializedEvent;
import jaicore.basic.algorithm.AlgorithmProblemTransformer;
import jaicore.basic.algorithm.AlgorithmState;
import jaicore.graphvisualizer.gui.VisualizationWindow;
import jaicore.planning.EvaluatedSearchGraphBasedPlan;
import jaicore.planning.algorithms.forwarddecomposition.ForwardDecompositionReducer;
import jaicore.planning.graphgenerators.task.tfd.TFDTooltipGenerator;
import jaicore.planning.model.CostSensitiveHTNPlanningProblem;
import jaicore.planning.model.CostSensitivePlanningToSearchProblemTransformer;
import jaicore.planning.model.ceoc.CEOCAction;
import jaicore.planning.model.ceoc.CEOCOperation;
import jaicore.planning.model.core.Plan;
import jaicore.planning.model.task.ceocipstn.CEOCIPSTNPlanningProblem;
import jaicore.planning.model.task.ceocipstn.OCIPMethod;
import jaicore.search.algorithms.standard.bestfirst.BestFirst;
import jaicore.search.algorithms.standard.bestfirst.events.EvaluatedSearchSolutionCandidateFoundEvent;
import jaicore.search.core.interfaces.GraphGenerator;
import jaicore.search.core.interfaces.IGraphSearch;
import jaicore.search.core.interfaces.IGraphSearchFactory;
import jaicore.search.model.other.EvaluatedSearchGraphPath;
import jaicore.search.model.probleminputs.GraphSearchProblemInput;
import jaicore.search.model.travesaltree.NodeTooltipGenerator;

/**
 * Hierarchically create an object of type T
 *
 * @author fmohr
 *
 * @param <T>
 */
public class HASCO<ISearch, N, A, V extends Comparable<V>> extends SoftwareConfigurationAlgorithm<RefinementConfiguredSoftwareConfigurationProblem<V>, HASCORunReport<V>, HASCOSolutionCandidate<V>, V> {

	/* problem and algorithm setup */
	private final RefinementConfiguredSoftwareConfigurationProblem<V> configurationProblem;
	private final Collection<Component> components;
	private final IHASCOPlanningGraphGeneratorDeriver<N, A> planningGraphGeneratorDeriver;
	private final AlgorithmProblemTransformer<GraphSearchProblemInput<N, A, V>, ISearch> searchProblemTransformer;
	private HASCOConfig config = ConfigCache.getOrCreate(HASCOConfig.class);
	private final IGraphSearchFactory<ISearch, ?, N, A, V, ?, ?> searchFactory;

	/* Parameters for the search algorithm configuration */
	/** Factory for producing planners solving the HTN planning problem. */
	// private final IHASCOSearchSpaceUtilFactory<N, A, V> searchSpaceUtilFactory;

	/** Object evaluator for assessing the quality of plans. */

	/* working constants of the algorithms - these are effectively final but are not set at object creation time */
	private CostSensitiveHTNPlanningProblem<CEOCOperation, OCIPMethod, CEOCAction, CEOCIPSTNPlanningProblem<CEOCOperation, OCIPMethod, CEOCAction>, V> planningProblem;
	private GraphSearchProblemInput<N, A, V> searchProblem;
	private IGraphSearch<ISearch, ?, N, A, V, ?, ?> search;
	private final List<HASCOSolutionCandidate<V>> listOfAllRecognizedSolutions = new ArrayList<>();

	/* runtime variables of algorithm */
	private boolean searchCreatedAndInitialized = false;
	private final TimeRecordingEvaluationWrapper<V> timeGrabbingEvaluationWrapper;

	public HASCO(RefinementConfiguredSoftwareConfigurationProblem<V> configurationProblem, IHASCOPlanningGraphGeneratorDeriver<N, A> planningGraphGeneratorDeriver,
			IGraphSearchFactory<ISearch, ?, N, A, V, ?, ?> searchFactory, AlgorithmProblemTransformer<GraphSearchProblemInput<N, A, V>, ISearch> searchProblemTransformer) {
		super();
		if (configurationProblem == null)
			throw new IllegalArgumentException("Cannot work with configuration problem NULL");
		this.configurationProblem = configurationProblem;
		this.planningGraphGeneratorDeriver = planningGraphGeneratorDeriver;
		this.searchFactory = searchFactory;
		this.searchProblemTransformer = searchProblemTransformer;
		this.components = configurationProblem.getComponents();
		this.timeGrabbingEvaluationWrapper = new TimeRecordingEvaluationWrapper<>(configurationProblem.getCompositionEvaluator());
		setInput(new RefinementConfiguredSoftwareConfigurationProblem<>(
				new SoftwareConfigurationProblem<V>(components, configurationProblem.getRequiredInterface(), timeGrabbingEvaluationWrapper), configurationProblem.getParamRefinementConfig()));
	}

	@Override
	public AlgorithmEvent nextWithException() throws Exception {
		Logger logger = getLogger();
		switch (getState()) {
		case created: {
			logger.info("Starting HASCO run.");

			/* check whether there is a refinement config for each numeric parameter */
			Map<Component, Map<Parameter, ParameterRefinementConfiguration>> paramRefinementConfig = getInput().getParamRefinementConfig();
			for (Component c : this.components) {
				for (Parameter p : c.getParameters()) {
					if (p.isNumeric() && (!paramRefinementConfig.containsKey(c) || !paramRefinementConfig.get(c).containsKey(p))) {
						throw new IllegalArgumentException("No refinement config was delivered for numeric parameter " + p.getName() + " of component " + c.getName());
					}
				}
			}

			/* derive search problem */
			logger.debug("Deriving search problem");
			planningProblem = new HASCOReduction<V>().transform(getInput());
			if (logger.isDebugEnabled()) {
				String operations = planningProblem.getCorePlanningProblem().getDomain().getOperations().stream().map(o -> "\n\t\t" + o.getName() + "(" + o.getParams() + ")\n\t\t\tPre: "
						+ o.getPrecondition() + "\n\t\t\tAdd List: " + o.getAddLists() + "\n\t\t\tDelete List: " + o.getDeleteLists()).collect(Collectors.joining());
				String methods = planningProblem
						.getCorePlanningProblem().getDomain().getMethods().stream().map(m -> "\n\t\t" + m.getName() + "(" + m.getParameters() + ") for task " + m.getTask() + "\n\t\t\tPre: "
								+ m.getPrecondition() + "\n\t\t\tPre Eval: " + m.getEvaluablePrecondition() + "\n\t\t\tNetwork: " + m.getNetwork().getLineBasedStringRepresentation())
						.collect(Collectors.joining());
				logger.debug("Derived the following HTN planning problem:\n\tOperations:{}\n\tMethods:{}", operations, methods);
			}
			searchProblem = new CostSensitivePlanningToSearchProblemTransformer<CEOCOperation, OCIPMethod, CEOCAction, CEOCIPSTNPlanningProblem<CEOCOperation, OCIPMethod, CEOCAction>, V, N, A>(
					planningGraphGeneratorDeriver).transform(planningProblem);

			/* communicate that algorithm has been initialized */
			logger.debug("Emitting intialization event");
			AlgorithmInitializedEvent initEvent = new AlgorithmInitializedEvent();
			post(initEvent);
			setState(AlgorithmState.active);
			return initEvent;
		}
		case active: {

			/* if the search itself has not been initialized, do this now */
			if (!searchCreatedAndInitialized) {

				/* create search algorithm, set its logger, and initialize visualization*/
				logger.debug("Creating the search object");
				searchFactory.setProblemInput(searchProblem, searchProblemTransformer);
				search = searchFactory.getAlgorithm();
				search.setNumCPUs(config.cpus());
				search.setTimeout(config.timeout() * 1000, TimeUnit.MILLISECONDS);
				String loggerName = getLoggerName();
				if (loggerName != null && loggerName.length() > 0 && search instanceof ILoggingCustomizable) {
					logger.info("Setting logger name of {} to {}", search, loggerName + ".search");
					((ILoggingCustomizable) this.search).setLoggerName(loggerName + ".search");
				}
				if (config.visualizationEnabled()) {
					logger.info("Launching graph visualization");
					VisualizationWindow<?, ?> window = new VisualizationWindow<>(search);
					if ((planningGraphGeneratorDeriver instanceof DefaultHASCOPlanningGraphGeneratorDeriver
							&& ((DefaultHASCOPlanningGraphGeneratorDeriver) planningGraphGeneratorDeriver).getWrappedDeriver() instanceof ForwardDecompositionReducer) && search instanceof BestFirst) {
						window.setTooltipGenerator(new NodeTooltipGenerator<>(new TFDTooltipGenerator()));
					}
				}

				/* now initialize the search */
				logger.debug("Initializing the search");
				boolean searchInitializationObserved = false;
				while (search.hasNext() && !(searchInitializationObserved = (search.next() instanceof AlgorithmInitializedEvent)))
					;
				if (!searchInitializationObserved)
					throw new IllegalStateException("The search underlying HASCO could not be initialized successully.");
				HASCOSearchInitializedEvent event = new HASCOSearchInitializedEvent();
				post(event);
				searchCreatedAndInitialized = true;
				return event;
			}

			/* otherwise iterate over the search */
			while (search.hasNext()) {
				AlgorithmEvent searchEvent = search.nextWithException();

				/* if the underlying search algorithm finished, we also finish */
				if (searchEvent instanceof AlgorithmFinishedEvent) {
					return terminate();
				}

				/* otherwise, if a solution has been found, we announce this finding to our listeners and memorize if it is a new best candidate */
				else if (searchEvent instanceof EvaluatedSearchSolutionCandidateFoundEvent) {
					logger.info("Received new solution from search, communicating this solution to the HASCO listeners.");
					@SuppressWarnings("unchecked")
					EvaluatedSearchSolutionCandidateFoundEvent<N, A, V> solutionEvent = (EvaluatedSearchSolutionCandidateFoundEvent<N, A, V>) searchEvent;
					EvaluatedSearchGraphPath<N, A, V> searchPath = solutionEvent.getSolutionCandidate();
					Plan<CEOCAction> plan = planningGraphGeneratorDeriver.getPlan(searchPath.getNodes());
					ComponentInstance objectInstance = Util.getSolutionCompositionForPlan(components, planningProblem.getCorePlanningProblem().getInit(), plan, true);
					V score = timeGrabbingEvaluationWrapper.hasEvaluationForComponentInstance(objectInstance) ? solutionEvent.getSolutionCandidate().getScore()
							: timeGrabbingEvaluationWrapper.evaluate(objectInstance);
					EvaluatedSearchGraphBasedPlan<CEOCAction, V, N> evaluatedPlan = new EvaluatedSearchGraphBasedPlan<>(plan, score, searchPath);
					HASCOSolutionCandidate<V> solution = new HASCOSolutionCandidate<>(objectInstance, evaluatedPlan,
							timeGrabbingEvaluationWrapper.getEvaluationTimeForComponentInstance(objectInstance));
					updateBestSeenSolution(solution);
					listOfAllRecognizedSolutions.add(solution);
					HASCOSolutionEvent<V> hascoSolutionEvent = new HASCOSolutionEvent<>(solution);
					post(hascoSolutionEvent);
					return hascoSolutionEvent;
				}
			}
			return terminate();
		}
		default:
			throw new IllegalStateException("HASCO cannot do anything in state " + getState());
		}

	}

	protected void afterSearch() {
	}

	/**
	 * @return The config object defining the properties.
	 */
	public HASCOConfig getConfig() {
		return config;
	}

	public void setConfig(HASCOConfig config) {
		this.config = config;
	}

	/**
	 * Set the number of CPUs to be used by HASCO.
	 *
	 * @param numberOfCPUs
	 *            The number of cpus to be used.
	 */
	@Override
	public void setNumCPUs(final int numberOfCPUs) {
		this.getConfig().setProperty(HASCOConfig.K_CPUS, numberOfCPUs + "");
	}

	public GraphGenerator<N, A> getGraphGenerator() {
		return searchProblem.getGraphGenerator();
	}
	
	/**
	 * @return Returns the number of CPUs that is to be used by HASCO.
	 */
	public int getNumCPUs() {
		return this.getConfig().cpus();
	}

	@Override
	public RefinementConfiguredSoftwareConfigurationProblem<V> getInput() {
		return configurationProblem;
	}

	public CostSensitiveHTNPlanningProblem<CEOCOperation, OCIPMethod, CEOCAction, CEOCIPSTNPlanningProblem<CEOCOperation, OCIPMethod, CEOCAction>, V> getPlanningProblem() {
		return planningProblem;
	}

	public void setVisualization(boolean visualization) {
		this.config.setProperty(HASCOConfig.K_VISUALIZE, String.valueOf(visualization));
	}

	@Override
	public void cancel() {
		if (search != null)
			search.cancel();
		this.terminate();
	}

	public IHASCOPlanningGraphGeneratorDeriver<N, A> getPlanningGraphGeneratorDeriver() {
		return planningGraphGeneratorDeriver;
	}

	public AlgorithmProblemTransformer<GraphSearchProblemInput<N, A, V>, ISearch> getSearchProblemTransformer() {
		return searchProblemTransformer;
	}

	public AlgorithmInitializedEvent init() {
		AlgorithmEvent e = null;
		while (hasNext()) {
			e = next();
			if (e instanceof AlgorithmInitializedEvent)
				return (AlgorithmInitializedEvent) e;
		}
		throw new IllegalStateException("Could not complete initialization");
	}

	@Override
	public HASCORunReport<V> getOutput() {
		return new HASCORunReport<>(listOfAllRecognizedSolutions);
	}
}
