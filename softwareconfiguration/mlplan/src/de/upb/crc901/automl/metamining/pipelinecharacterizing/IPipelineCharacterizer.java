package de.upb.crc901.automl.metamining.pipelinecharacterizing;

import java.util.List;

import de.upb.crc901.automl.pipeline.basic.MLPipeline;
import hasco.model.ComponentInstance;

/**
 * Finds patterns in given MLPipelines. A pipeline characterizer first has to be
 * built with {@link #build(List)}, where it identifies patterns in the given
 * data base of pipelines. Subsequently, it can be used to check for these
 * patterns in a new pipeline.
 * 
 * @author Helena Graf
 *
 */
public interface IPipelineCharacterizer {

	/**
	 * Finds frequent patterns in the given list of pipelines.
	 * 
	 * @param pipelines
	 *            The pipelines to go through for patterns
	 */
	public void build(List<ComponentInstance> pipelines);

	/**
	 * Checks which of the found patterns (found during the training phase in
	 * {@link IPipelineCharacterizer#build(List)}) occur in this pipeline.
	 * 
	 * If in the returned list l, l[j]=1, pattern j occurs in this pipeline.
	 * Otherwise l[j]=0 and pattern j doesn't occur in this pipeline.
	 * 
	 * @param pipeline
	 *            The pipeline for which pattern occurrence is checked
	 * @return A list representing pattern occurrences in the pipeline
	 */
	public double[] characterize(ComponentInstance pipeline);

	/**
	 * For each {@link MLPipeline} that was used in the training (given by its
	 * ComponentInstance), return which found pattern (found during the training phase in
	 * {@link IPipelineCharacterizer#build(List)}) occurs in which pipeline.
	 * 
	 * If in the returned matrix m, m[i][j]=1, pattern j occurs in training pipeline
	 * i. Otherwise m[i][j]=0 and pattern j doesn't occur in training pipeline i.
	 * 
	 * @return A matrix representing pattern occurrences in pipelines
	 */
	public double[][] getCharacterizationsOfTrainingExamples();
}
