package de.upb.crc901.automl.metamining;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import de.upb.crc901.automl.metamining.pipelinecharacterizing.IPipelineCharacterizer;
import de.upb.crc901.automl.metamining.pipelinecharacterizing.WEKAPipelineCharacterizer;
import de.upb.crc901.automl.metamining.similaritymeasures.F3Optimizer;
import de.upb.crc901.automl.metamining.similaritymeasures.IHeterogenousSimilarityMeasureComputer;
import de.upb.crc901.automl.metamining.similaritymeasures.IRankMatrixSimilarityComputer;
import de.upb.crc901.automl.metamining.similaritymeasures.RankMatrixSimilarityComputer;
import hasco.metamining.IMetaMiner;
import hasco.model.Component;
import hasco.model.ComponentInstance;
import hasco.model.Parameter;
import hasco.model.ParameterRefinementConfiguration;
import weka.core.Attribute;
import weka.core.Instances;

public class WEKAMetaminer implements IMetaMiner {

	private boolean hasBeenBuilt = false;
	private INDArray datasetMetafeatures;
	private Enumeration<Attribute> dataSetMetaFeaturesAttributes;

	private IHeterogenousSimilarityMeasureComputer similarityMeasure = new F3Optimizer(0.1);
	private IRankMatrixSimilarityComputer similarityComputer = new RankMatrixSimilarityComputer();
	private IPipelineCharacterizer pipelineCharacterizer;

	public WEKAMetaminer(Map<Component, Map<Parameter, ParameterRefinementConfiguration>> paramConfigs) {
		this.pipelineCharacterizer = new WEKAPipelineCharacterizer(paramConfigs);
	}

	@Override
	public double score(ComponentInstance componentInstance) {
		// Check if has been trained
		if (!hasBeenBuilt) {
			throw new RuntimeException("Metaminer has not been built!");
		}
		if (dataSetMetaFeaturesAttributes == null) {
			throw new RuntimeException("Metaminer has not been given a data set characterization!");
		}

		// Characterize pipeline and compute similarity with data set
		double[] pipelineMetafeatures = pipelineCharacterizer.characterize(componentInstance);
		return similarityMeasure.computeSimilarity(datasetMetafeatures, Nd4j.create(pipelineMetafeatures));
	}

	public void build(ArrayList<ComponentInstance> distinctPipelines, Instances metaFeatureInformation,
			double[][][] performanceValues) throws Exception {
		// Check whether has been built
		if (hasBeenBuilt) {
			throw new Exception("MetaMiner has already been built!");
		}

		// ----- Data set Characterization -----

		dataSetMetaFeaturesAttributes = metaFeatureInformation.enumerateAttributes();

		// Convert to matrix (Matrix X with rows representing data sets)
		INDArray datasetsMetafeatures = Nd4j.create(metaFeatureInformation.size() - 1,
				metaFeatureInformation.numAttributes());
		for (int i = 1; i < metaFeatureInformation.size(); i++) {
			datasetsMetafeatures.putRow(i - 1, Nd4j.create(metaFeatureInformation.get(i).toDoubleArray()));
		}

		// ----- Pipeline Characterization -----

		// Compute relative performance ranks of pipelines on data sets
		INDArray rankMatrix = similarityComputer.computeSimilarityOfRankMatrix(performanceValues);

		// Initialize PipelineCharacterizer with list of distinct pipelines
		pipelineCharacterizer.build(distinctPipelines);

		// Get Characterization of base pipelines from PipelineCharacterizer (Matrix W)
		INDArray pipelinesMetafeatures = Nd4j.create(pipelineCharacterizer.getCharacterizationsOfTrainingExamples());

		// Initialize HeterogenousSimilarityMeasures
		similarityMeasure.build(datasetsMetafeatures, pipelinesMetafeatures, rankMatrix);

		// Building is finished
		hasBeenBuilt = true;
	}

	public void setDataSetCharacterization(Map<String, Double> datasetCharacterization) {
		// Characterize the given data set with characterizer (set x)
		datasetMetafeatures = Nd4j.create(datasetCharacterization.size());
		int i = 0;
		for (Enumeration<Attribute> attributes = dataSetMetaFeaturesAttributes; attributes.hasMoreElements(); i++) {
			datasetMetafeatures.putScalar(i, datasetCharacterization.get(attributes.nextElement().name()));
		}
	}

	public IHeterogenousSimilarityMeasureComputer getSimilarityMeasure() {
		return similarityMeasure;
	}

	public void setSimilarityMeasure(IHeterogenousSimilarityMeasureComputer similarityMeasure) {
		this.similarityMeasure = similarityMeasure;
	}

	public IPipelineCharacterizer getPipelineCharacterizer() {
		return pipelineCharacterizer;
	}

	public void setPipelineCharacterizer(IPipelineCharacterizer pipelineCharacterizer) {
		this.pipelineCharacterizer = pipelineCharacterizer;
	}

}
