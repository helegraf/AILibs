package de.upb.crc901.mlplan.examples;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Random;

import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.xml.DataSetDescription;

import de.upb.crc901.automl.metamining.MetaMLPlan;
import jaicore.ml.WekaUtil;
import weka.classifiers.Evaluation;
import weka.core.Instances;

/**
 * Illustrates the usage of the WEKAMetaMiner.
 * 
 * @author Helena Graf
 *
 */
public class MetaMinerExample {

	public static void main(String[] args) throws Exception {
		// Load data for a data set and create a train-test-split
		OpenmlConnector connector = new OpenmlConnector();
		DataSetDescription ds = connector.dataGet(40984);
		File file = ds.getDataset("4350e421cdc16404033ef1812ea38c01");
		Instances data = new Instances(new BufferedReader(new FileReader(file)));
		data.setClassIndex(data.numAttributes() - 1);
		List<Instances> split = WekaUtil.getStratifiedSplit(data, new Random(0), .7f);

		// Initialize meta mlplan and let it run for 30 seconds
		MetaMLPlan metaMLPlan = new MetaMLPlan();
		metaMLPlan.setTimeOutInSeconds(30);
		metaMLPlan.buildClassifier(split.get(0));

		// Evaluate solution produced by meta mlplan
		Evaluation eval = new Evaluation(split.get(0));
		eval.evaluateModel(metaMLPlan, split.get(1));
		System.out.println("Error Rate of the solution produced by Meta ML-Plan: " + (100 - eval.pctCorrect()) / 100f);
	}

}
