package de.upb.crc901.automl.metamining.similaritymeasures;

import org.nd4j.linalg.api.ndarray.INDArray;

public interface IRankMatrixSimilarityComputer {

	INDArray computeSimilarityOfRankMatrix(double[][][] performanceValues);
}
