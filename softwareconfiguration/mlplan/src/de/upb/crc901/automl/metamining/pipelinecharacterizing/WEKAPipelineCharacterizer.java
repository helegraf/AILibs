package de.upb.crc901.automl.metamining.pipelinecharacterizing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.oned.Interval;
import org.apache.commons.math3.geometry.partitioning.Region.Location;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import de.upb.crc901.automl.hascowekaml.WEKAPipelineFactory;
import de.upb.crc901.automl.pipeline.basic.MLPipeline;
import de.upb.crc901.automl.pipeline.basic.SupervisedFilterSelector;
import hasco.core.Util;
import hasco.model.ComponentInstance;
import hasco.model.NumericParameterDomain;
import hasco.serialization.ComponentLoader;
import treeminer.FrequentSubtreeFinder;
import treeminer.TreeMiner;
import treeminer.TreeRepresentationUtils;

/**
 * A characterizer for MLPipelines that characterizes them using an ontology and
 * a tree mining algorithm.
 * 
 * @author Helena Graf
 *
 */
public class WEKAPipelineCharacterizer implements IPipelineCharacterizer {

	private FrequentSubtreeFinder treeMiner;
	private IOntologyConnector ontologyConnector;
	private String[] patterns;
	private int minSupport = 1;
	private String preprocessorSubTreeName = "Preprocessor";
	private String preprocessorsSubTreeName = "Preprocessors";
	private String pipelineTreeName = "Pipeline";
	private ComponentLoader componentLoader;
	private WEKAPipelineFactory wekaPipelineFactory = new WEKAPipelineFactory();

	public WEKAPipelineCharacterizer(ComponentLoader componentLoader) {
		this.treeMiner = new TreeMiner();
		this.componentLoader = componentLoader;

		try {
			ontologyConnector = new WEKAOntologyConnector();
		} catch (OWLOntologyCreationException e) {
			System.err.println("Cannot connect to Ontology!");
			throw new RuntimeException(e);
		}
	}

	@Override
	public void build(List<ComponentInstance> pipelines) {
		// Convert the pipelines to String representations
		List<String> pipelineRepresentations = new ArrayList<String>();
		pipelines.forEach(pipeline -> {
			// TODO remove this workaround
			try {
				pipelineRepresentations.add(makeStringTreeRepresentation(pipeline));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

		// Use the tree miner to find patterns
		treeMiner.findFrequentSubtrees(pipelineRepresentations, minSupport);
	}

	@Override
	public double[] characterize(ComponentInstance pipeline) {
		// Make tree representation from this pipeline
		String treeRepresentation = null;
		// TODO remove this workaroundd
		try {
			treeRepresentation = makeStringTreeRepresentation(pipeline);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Ask the treeMiner which of the patterns are included in this pipeline
		double[] pipelineCharacterization = new double[patterns.length];
		for (int i = 0; i < patterns.length; i++) {
			if (TreeRepresentationUtils.containsSubtree(treeRepresentation, patterns[i])) {
				pipelineCharacterization[i] = 1;
			} else {
				pipelineCharacterization[i] = 0;
			}
		}

		return pipelineCharacterization;
	}

	/**
	 * Converts the given MLPipeline to a String representation of its components
	 * using the ontology
	 * 
	 * @param pipeline
	 * @return
	 * @throws Exception
	 */
	protected String makeStringTreeRepresentation(ComponentInstance pipeline) throws Exception {
		// TODO add hyperparameters of the algorithms
		MLPipeline pipelineWEKA = wekaPipelineFactory.getComponentInstantiation(pipeline);

		// Get annotations for preprocessors
		List<String> preprocessorsSubTree = new ArrayList<String>();
		List<SupervisedFilterSelector> preprocessors = pipelineWEKA.getPreprocessors();
		preprocessors.forEach(preprocessor -> {
			// Get searcher annotation
			String searcher = preprocessor.getSearcher().getClass().getName();
			List<String> searcherBranch = ontologyConnector.getAncestorsOfSearcher(searcher);
			String searcherBranchRepresentation = TreeRepresentationUtils.makeRepresentationForBranch(searcherBranch);

			// Get evaluator annotation
			String evaluator = preprocessor.getEvaluator().getClass().getName();
			List<String> evaluatorBranch = ontologyConnector.getAncestorsOfEvaluator(evaluator);
			String evaluatorBranchRepresentation = TreeRepresentationUtils.makeRepresentationForBranch(evaluatorBranch);

			// Merge both annotations
			String preprocessorSubTree = TreeRepresentationUtils.addChildrenToNode(preprocessorSubTreeName,
					Arrays.asList(searcherBranchRepresentation, evaluatorBranchRepresentation));
			preprocessorsSubTree.add(preprocessorSubTree);
		});
		// Merge preprocessors
		String preprocessorsSubTreeRepresentation = TreeRepresentationUtils.addChildrenToNode(preprocessorsSubTreeName,
				preprocessorsSubTree);

		// Get annotations for classifier
		String classifier = pipelineWEKA.getBaseClassifier().getClass().getName();
		List<String> classifierBranch = ontologyConnector.getAncestorsOfClassifier(classifier);
		String classifierBranchRepresentation = TreeRepresentationUtils.makeRepresentationForBranch(classifierBranch);

		// Merge preprocessors and classifiers
		return TreeRepresentationUtils.addChildrenToNode(pipelineTreeName,
				Arrays.asList(preprocessorsSubTreeRepresentation, classifierBranchRepresentation));
	}

	protected List<String> getParametersForComponentInstance(ComponentInstance classifier) {
		List<String> parameters = new ArrayList<String>();

		componentLoader.getParamConfigs().get(classifier).forEach((parameter, parameterRefinementConfiguration) -> {
			String parameterName = parameter.getName();
			List<String> parameterRefinement = new ArrayList<String>();

			// Categorical parameter - just take the value
			if (parameter.isCategorical()) {
				parameterRefinement.add(classifier.getParameterValues().get(parameterName));

				// Numeric parameter - needs to be refined
			} else {
				NumericParameterDomain parameterDomain = ((NumericParameterDomain) parameter.getDefaultDomain());
				Interval currentInterval = null;
				Interval nextInterval = new Interval(parameterDomain.getMin(), parameterDomain.getMax());
				double parameterValue = Double.parseDouble(classifier.getParameterValues().get(parameterName));
				double precision = parameterValue == 0 ? 0 : Math.ulp(parameterValue);

				while (currentInterval != nextInterval) {
					currentInterval = nextInterval;
					parameterRefinement.add(serializeInterval(currentInterval));

					List<Interval> refinement = Util.getNumericParameterRefinement(nextInterval, parameterValue,
							parameterDomain.isInteger(), parameterRefinementConfiguration);

					for (Interval interval : refinement) {
						if (interval.checkPoint(parameterValue, precision) == Location.INSIDE
								|| interval.checkPoint(parameterValue, precision) == Location.BOUNDARY) {
							nextInterval = interval;
							break;
						}
					}
				}
			}
		});

		return parameters;
	}

	protected String serializeInterval(Interval interval) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		builder.append(interval.getInf());
		builder.append(",");
		builder.append(interval.getSup());
		builder.append("]");
		return builder.toString();
	}

	@Override
	public double[][] getCharacterizationsOfTrainingExamples() {
		// TODO Implement
		// TODO maybe adjust return parameter type here
		return null;
	}

	/**
	 * @return the ontologyConnector
	 */
	public IOntologyConnector getOntologyConnector() {
		return ontologyConnector;
	}

	/**
	 * @param ontologyConnector
	 *            the ontologyConnector to set
	 */
	public void setOntologyConnector(IOntologyConnector ontologyConnector) {
		this.ontologyConnector = ontologyConnector;
	}

	/**
	 * Get the minimum support required for a pattern to be considered frequent for
	 * the tree mining algorithm.
	 * 
	 * @return The minimum support a tree pattern must have to be considered
	 *         frequent
	 */
	public int getMinSupport() {
		return minSupport;
	}

	/**
	 * Set the minimum support required for a pattern to be considered frequent for
	 * the tree mining algorithm.
	 * 
	 * @param minSupport
	 *            The minimum support a tree pattern must have to be considered
	 *            frequent
	 */
	public void setMinSupport(int minSupport) {
		this.minSupport = minSupport;
	}

}
