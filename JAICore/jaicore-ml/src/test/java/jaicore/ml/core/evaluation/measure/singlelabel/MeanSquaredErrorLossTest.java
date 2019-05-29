package jaicore.ml.core.evaluation.measure.singlelabel;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class MeanSquaredErrorLossTest {
	@Test
	public void testComputeAverageSingleZero( ) {
		MeanSquaredErrorLoss loss = new MeanSquaredErrorLoss();
		List<Double> actual = Arrays.asList(0.0);
		List<Double> expected = Arrays.asList(0.0);
		double actualLoss = loss.calculateAvgMeasure(actual, expected);		
		assertEquals(0, actualLoss,0.00001);
	}
	
	@Test
	public void testComputeAverageSingleOne( ) {
		MeanSquaredErrorLoss loss = new MeanSquaredErrorLoss();
		List<Double> actual = Arrays.asList(0.0);
		List<Double> expected = Arrays.asList(1.0);
		double actualLoss = loss.calculateAvgMeasure(actual, expected);		
		assertEquals(1, actualLoss,0.00001);
	}
	
	@Test
	public void testComputeAverageMultiple( ) {
		MeanSquaredErrorLoss loss = new MeanSquaredErrorLoss();
		List<Double> actual = Arrays.asList(0.0, 1.0, 2.0, 3.0, 2.0);
		List<Double> expected = Arrays.asList(1.0, 1.0, 2.0, 0.0, 2.0);
		double actualLoss = loss.calculateAvgMeasure(actual, expected);		
		assertEquals(2, actualLoss,0.00001);
	}
}
