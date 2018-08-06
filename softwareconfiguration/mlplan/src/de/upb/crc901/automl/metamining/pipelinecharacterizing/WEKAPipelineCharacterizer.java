package de.upb.crc901.automl.metamining.pipelinecharacterizing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.geometry.euclidean.oned.Interval;
import org.apache.commons.math3.geometry.partitioning.Region.Location;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import hasco.core.Util;
import hasco.model.Component;
import hasco.model.ComponentInstance;
import hasco.model.NumericParameterDomain;
import hasco.model.Parameter;
import hasco.model.ParameterRefinementConfiguration;
import jaicore.planning.graphgenerators.task.tfd.TFDNode;
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

	private List<String> foundPipelinePatterns;
	private int patternMinSupport = 1;

	private String pipelineTreeName = "Pipeline";
	private Map<Component, Map<Parameter, ParameterRefinementConfiguration>> componentParameters;

	public WEKAPipelineCharacterizer(
			Map<Component, Map<Parameter, ParameterRefinementConfiguration>> componentParameters) {
		this.treeMiner = new TreeMiner();
		this.componentParameters = componentParameters;

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
			pipelineRepresentations.add(makeStringTreeRepresentation(pipeline));
		});

		// Use the tree miner to find patterns
		foundPipelinePatterns = treeMiner.findFrequentSubtrees(pipelineRepresentations, patternMinSupport);
	}

	@Override
	public double[] characterize(ComponentInstance pipeline) {
		// Make tree representation from this pipeline
		String treeRepresentation = makeStringTreeRepresentation(pipeline);

		// Ask the treeMiner which of the patterns are included in this pipeline
		double[] pipelineCharacterization = new double[foundPipelinePatterns.size()];
		for (int i = 0; i < foundPipelinePatterns.size(); i++) {
			if (TreeRepresentationUtils.containsSubtree(treeRepresentation, foundPipelinePatterns.get(i))) {
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
	protected String makeStringTreeRepresentation(ComponentInstance pipeline) {
		List<String> pipelineBranches = new ArrayList<String>();
		ComponentInstance classifierCI;

		// Component is pipeline
		if (pipeline.getComponent().getName().equals("pipeline")) {
			ComponentInstance preprocessorCI = pipeline.getSatisfactionOfRequiredInterfaces().get("preprocessor");

			if (preprocessorCI != null) {
				// Characterize searcher
				ComponentInstance searcherCI = preprocessorCI.getSatisfactionOfRequiredInterfaces().get("search");
				addCharacterizationOfPipelineElement(pipelineBranches, searcherCI);

				// Characterize evaluator
				ComponentInstance evaluatorCI = preprocessorCI.getSatisfactionOfRequiredInterfaces().get("eval");
				addCharacterizationOfPipelineElement(pipelineBranches, evaluatorCI);
			}

			classifierCI = pipeline.getSatisfactionOfRequiredInterfaces().get("classifier");

			// Component is just a classifier
		} else {
			classifierCI = pipeline;
		}

		// Characterize classifier
		addCharacterizationOfPipelineElement(pipelineBranches, classifierCI);

		// Put tree together
		return TreeRepresentationUtils.addChildrenToNode(pipelineTreeName, pipelineBranches);
	}

	/**
	 * Gets the ontology characterization and selected parameters of the given
	 * ComponentInstance and ads its characterization (the branch of a tree that is
	 * the current pipeline) to the pipeline tree by adding its branch
	 * representation as a last element of the list of branches.
	 * 
	 * @param pipelineBranches
	 *            The current branches of the pipeline.
	 * @param componentInstance
	 *            The pipeline element to be characterized
	 */
	protected void addCharacterizationOfPipelineElement(List<String> pipelineBranches,
			ComponentInstance componentInstance) {
		if (componentInstance != null) {
			// Get generalization
			List<String> branchComponents = ontologyConnector
					.getAncestorsOfAlgorithm(componentInstance.getComponent().getName());

			// Get parameters
			branchComponents.set(branchComponents.size() - 1,
					TreeRepresentationUtils.addChildrenToNode(branchComponents.get(branchComponents.size() - 1),
							getParametersForComponentInstance(componentInstance)));

			// Serialize
			String branch = TreeRepresentationUtils.makeRepresentationForBranch(branchComponents);
			pipelineBranches.add(branch);
		}
	}

	protected List<String> getParametersForComponentInstance(ComponentInstance componentInstance) {
		List<String> parameters = new ArrayList<String>();

		// Get Parameters of base classifier if this is a meta classifier
		if (componentInstance.getSatisfactionOfRequiredInterfaces() != null
				&& componentInstance.getSatisfactionOfRequiredInterfaces().size() > 0) {
			componentInstance.getSatisfactionOfRequiredInterfaces().forEach((requiredInterface, component) -> {
				// so far, only have the "K" interface & this has no param so can directly get

				List<String> kernelFunctionCharacterisation = Arrays.asList(requiredInterface);
				kernelFunctionCharacterisation
						.addAll(ontologyConnector.getAncestorsOfAlgorithm(component.getComponent().getName()));
				parameters.add(TreeRepresentationUtils.addChildrenToNode(requiredInterface, Arrays
						.asList(TreeRepresentationUtils.makeRepresentationForBranch(kernelFunctionCharacterisation))));
			});
		}

		// Get other parameters
		componentInstance.getComponent().getParameters().forEach(parameter -> {
			String parameterName = parameter.getName();
			List<String> parameterRefinement = new ArrayList<String>();
			parameterRefinement.add(parameterName);

			// Numeric parameter - needs to be refined
			if (parameter.isNumeric()) {
				ParameterRefinementConfiguration parameterRefinementConfiguration = componentParameters
						.get(componentInstance.getComponent()).get(parameter);
				NumericParameterDomain parameterDomain = ((NumericParameterDomain) parameter.getDefaultDomain());
				Interval currentInterval = null;
				Interval nextInterval = new Interval(parameterDomain.getMin(), parameterDomain.getMax());
				double parameterValue = Double.parseDouble(componentInstance.getParameterValues().get(parameterName));
				double precision = parameterValue == 0 ? 0 : Math.ulp(parameterValue);

				while (nextInterval != null) {
					currentInterval = nextInterval;
					parameterRefinement.add(serializeInterval(currentInterval));

					List<Interval> refinement = Util.getNumericParameterRefinement(nextInterval, parameterValue,
							parameterDomain.isInteger(), parameterRefinementConfiguration);

					if (refinement.size() == 0) {
						nextInterval = null;
						break;
					}

					for (Interval interval : refinement) {
						if (interval.checkPoint(parameterValue, precision) == Location.INSIDE
								|| interval.checkPoint(parameterValue, precision) == Location.BOUNDARY) {
							nextInterval = interval;
							break;
						}
					}
				}
				// Categorical parameter
			} else {
				if (parameter.isCategorical()) {
					parameterRefinement.add(componentInstance.getParameterValues().get(parameterName));
				}
			}
			parameters.add(TreeRepresentationUtils.makeRepresentationForBranch(parameterRefinement));
		});

		return parameters;
	}

	/**
	 * Helper method for serializing an interval so that it can be used in String
	 * representations of parameters of pipeline elements.
	 * 
	 * @param interval
	 *            The interval to be serialized
	 * @return The String representation of the interval
	 */
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
		return treeMiner.getCharacterizationsOfTrainingExamples();
	}

	/**
	 * Get the used ontology connector.
	 * 
	 * @return The used ontology connector
	 */
	public IOntologyConnector getOntologyConnector() {
		return ontologyConnector;
	}

	/**
	 * Set the ontology connector to be used.
	 * 
	 * @param ontologyConnector
	 *            the ontologyConnector to be used
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
		return patternMinSupport;
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
		this.patternMinSupport = minSupport;
	}

	public int test(TFDNode i1, TFDNode i2, Collection<Component> collection) {
		// TODO remove this method only for testing purposes!
		try {
			ComponentInstance pipelie = Util.getSolutionCompositionFromState(collection, i1.getState());
			if (pipelie != null) {
				System.out.println(makeStringTreeRepresentation(pipelie));
			} else {
				System.err.println("Pipeline is null. (state of node: " + i1.getState() + ")");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}

}
