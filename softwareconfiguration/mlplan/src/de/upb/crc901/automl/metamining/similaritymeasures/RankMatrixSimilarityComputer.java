package de.upb.crc901.automl.metamining.similaritymeasures;

import java.util.Arrays;

import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

public class RankMatrixSimilarityComputer implements IRankMatrixSimilarityComputer {

	@Override
	public INDArray computeSimilarityOfRankMatrix(double[][][] performanceValues) {
		double[][] significances = new double[performanceValues.length][performanceValues[1].length];
		MannWhitneyUTest significanceTest = new MannWhitneyUTest();

		// For all datasets
		for (int i = 0; i < performanceValues.length; i++) {
			// For all pipelines
			for (int j = 0; j < performanceValues[i].length; j++) {
				double score = 0;
				if (performanceValues[i][j] != null && performanceValues[i][j].length > 0) {

					// Compared with all other workflows, compute a score
					for (int k = 0; k < performanceValues[i].length; k++) {
						if (performanceValues[i][k] != null && performanceValues[i][k].length > 0) {
							double significance = significanceTest.mannWhitneyUTest(performanceValues[i][j],
									performanceValues[i][k]);
							if (significance < 0.05) {
								double mean1 = Arrays.stream(performanceValues[i][j]).average().orElse(Double.NaN);
								double mean2 = Arrays.stream(performanceValues[i][k]).average().orElse(Double.NaN);

								if (mean1 > mean2) {
									score++;
								}
							} else {
								score += 0.5;
							}
						} else {
							score++;
						}
					}
				}

				significances[i][j] = score;
			}
		}

		return Nd4j.create(significances);
	}

}
