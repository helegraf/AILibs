package de.upb.crc901.automl.metamining.pipelinecharacterizing;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.oned.Interval;
import org.apache.commons.math3.geometry.partitioning.Region.Location;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import hasco.core.Util;
import hasco.model.ComponentInstance;
import hasco.model.NumericParameterDomain;
import hasco.model.ParameterRefinementConfiguration;
import hasco.serialization.ComponentLoader;
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
	private String[] patterns;
	private int minSupport = 1;
	private String pipelineTreeName = "Pipeline";
	private ComponentLoader componentLoader;

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
		List<String> pipelineBranches = new ArrayList<String>();

		ComponentInstance classifierCI;

		// Component is pipeline
		if (pipeline.getComponent().getName().equals("pipeline")) {
			ComponentInstance preprocessorCI = pipeline.getSatisfactionOfRequiredInterfaces().get("preprocessor");

			if (preprocessorCI != null) {
				// Get searcher if it has been set
				ComponentInstance searcherCI = preprocessorCI.getSatisfactionOfRequiredInterfaces().get("search");
				if (searcherCI != null) {
					String searcherBranch = TreeRepresentationUtils.makeRepresentationForBranch(
							ontologyConnector.getAncestorsOfSearcher(searcherCI.getComponent().getName()));
					searcherBranch = TreeRepresentationUtils.addChildrenToNode(searcherBranch,
							getParametersForComponentInstance(searcherCI));
					if (searcherBranch != null) {
						pipelineBranches.add(searcherBranch);
					}
				}

				// Get evaluator if it has been set
				ComponentInstance evaluatorCI = preprocessorCI.getSatisfactionOfRequiredInterfaces().get("eval");
				if (evaluatorCI != null) {
					String evaluatorBranch = TreeRepresentationUtils.makeRepresentationForBranch(
							ontologyConnector.getAncestorsOfEvaluator(evaluatorCI.getComponent().getName()));
					evaluatorBranch = TreeRepresentationUtils.addChildrenToNode(evaluatorBranch,
							getParametersForComponentInstance(evaluatorCI));
					if (evaluatorBranch != null) {
						pipelineBranches.add(evaluatorBranch);
					}
				}
			}

			classifierCI = pipeline.getSatisfactionOfRequiredInterfaces().get("classifier");

			// Component is just a classifier
		} else {
			classifierCI = pipeline;
		}

		// Characterize classifier
		if (classifierCI != null) {
			String classifierBranch = TreeRepresentationUtils.makeRepresentationForBranch(
					ontologyConnector.getAncestorsOfClassifier(classifierCI.getComponent().getName()));
			classifierBranch = TreeRepresentationUtils.addChildrenToNode(classifierBranch,
					getParametersForComponentInstance(classifierCI));

			// Add classifier to pipeline representation
			pipelineBranches.add(classifierBranch);
		}

		// Put tree together
		return TreeRepresentationUtils.addChildrenToNode(pipelineTreeName, pipelineBranches);
	}

	protected List<String> getParametersForComponentInstance(ComponentInstance componentInstance) {
		List<String> parameters = new ArrayList<String>();
	
		if (componentInstance.getComponent().getName().contains("weka.classifiers.functions.SMO")) {
			System.out.println(componentInstance.getComponent().getName());
			System.out.println(componentInstance.getParameterValues());
			System.out.println(componentInstance.getSatisfactionOfRequiredInterfaces());
		}

		componentInstance.getComponent().getParameters().forEach(parameter -> {
			String parameterName = parameter.getName();
			List<String> parameterRefinement = new ArrayList<String>();
			parameterRefinement.add(parameterName);
			
			// Numeric parameter - needs to be refined
			if (parameter.isNumeric()) {
				ParameterRefinementConfiguration parameterRefinementConfiguration = componentLoader.getParamConfigs()
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

	public int test(TFDNode i1, TFDNode i2) {
		try {
			ComponentInstance pipelie = Util.getSolutionCompositionFromState(componentLoader.getComponents(),
					i1.getState());
			if (pipelie != null) {
				makeStringTreeRepresentation(pipelie);
			} else {
				System.err.println("Pipeline is null. (state of node: " + i1.getState() + ")");
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;
	}

}
