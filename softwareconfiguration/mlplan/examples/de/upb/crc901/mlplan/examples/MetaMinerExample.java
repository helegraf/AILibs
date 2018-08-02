package de.upb.crc901.mlplan.examples;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.openml.apiconnector.io.OpenmlConnector;
import org.openml.apiconnector.xml.DataSetDescription;

import de.upb.crc901.automl.hascowekaml.HASCOForWekaML;
import de.upb.crc901.automl.hascowekaml.WEKAPipelineFactory;
import de.upb.crc901.automl.pipeline.basic.MLPipeline;
import hasco.core.HASCOProblemReduction;
import hasco.core.Util;
import hasco.model.ComponentInstance;
import hasco.serialization.ComponentLoader;
import jaicore.graphvisualizer.SimpleGraphVisualizationWindow;
import jaicore.ml.WekaUtil;
import jaicore.ml.evaluation.MonteCarloCrossValidationEvaluator;
import jaicore.ml.evaluation.MulticlassEvaluator;
import jaicore.planning.algorithms.forwarddecomposition.ForwardDecompositionHTNPlannerFactory;
import jaicore.planning.graphgenerators.task.tfd.TFDNode;
import jaicore.planning.graphgenerators.task.tfd.TFDTooltipGenerator;
import jaicore.search.algorithms.standard.core.ORGraphSearch;
import jaicore.search.algorithms.standard.lds.BestFirstLimitedDiscrepancySearch;
import jaicore.search.algorithms.standard.lds.NodeOrderList;
import jaicore.search.structure.core.GraphGenerator;
import jaicore.search.structure.core.Node;
import weka.core.Instances;

/**
 * Illustrates the usage of the WEKAMetaMiner.
 * 
 * @author Helena Graf
 *
 */
public class MetaMinerExample {

	public static void main(String[] args) throws Exception {
		/* load data for segment dataset and create a train-test-split */
		OpenmlConnector connector = new OpenmlConnector();
		DataSetDescription ds = connector.dataGet(40984);
		File file = ds.getDataset("4350e421cdc16404033ef1812ea38c01");
		Instances data = new Instances(new BufferedReader(new FileReader(file)));
		data.setClassIndex(data.numAttributes() - 1);
		List<Instances> split = WekaUtil.getStratifiedSplit(data, new Random(0), .7f);
		
		/* initialize mlplan, and let it run for 30 seconds */
		File configFile = new File("model/weka/weka-all-autoweka.json");
		HASCOForWekaML hasco = new HASCOForWekaML(configFile);
		ComponentLoader componentLoader = new ComponentLoader();
		componentLoader.loadComponents(configFile);
		
		/* get the graph generator from the reduction */
		HASCOProblemReduction reduction = new HASCOProblemReduction(configFile, "AbstractClassifier", true);
		GraphGenerator<TFDNode, String> graphGenerator = reduction
				.getGraphGeneratorUsedByHASCOForSpecificPlanner(new ForwardDecompositionHTNPlannerFactory<Double>());
		ORGraphSearch<TFDNode, String,NodeOrderList> lds = new BestFirstLimitedDiscrepancySearch<>(graphGenerator, (n1,n2) -> -1);
		new SimpleGraphVisualizationWindow<>(lds).getPanel().setTooltipGenerator(new TFDTooltipGenerator());;
		TimerTask tt = new TimerTask() {
			
			@Override
			public void run() {
				lds.cancel();
			}
		};
		new Timer().schedule(tt, 120000);
		MLPipeline currentlyBestSolution = null;
		double bestScore = 1;
		while (!lds.isInterrupted()) {
			List<TFDNode> solution = lds.nextSolution();
			if (solution == null)
				break;
			try {
			ComponentInstance ci = Util.getSolutionCompositionFromState(componentLoader.getComponents(), solution.get(solution.size() - 1).getState());
			WEKAPipelineFactory factory = new WEKAPipelineFactory();
			MLPipeline pl = factory.getComponentInstantiation(ci);
			MonteCarloCrossValidationEvaluator mccv = new MonteCarloCrossValidationEvaluator(new MulticlassEvaluator(new Random(0)), 3, split.get(0), .7f);
			double score = mccv.evaluate(pl);
			System.out.println(pl);
			System.out.println(score);
			if (score < bestScore) {
				currentlyBestSolution = pl;
				bestScore = score;
			}
			}
			catch (Throwable e) {
				e.printStackTrace();
			}
		}
		System.out.println("ready. Best solution: " + currentlyBestSolution);

		// System.out.println(hasco.getGraphGenerator());

		// WEKAMetaminer metaMiner = new WEKAMetaminer(data);
		// metaMiner.build();
		// MetaMinerBasedSorter comparator = new MetaMinerBasedSorter(metaMiner,
		// componentLoader);
		// mlplan.get.setOrGraphSearchFactory(new
		// ImprovedLimitedDiscrepancySearchFactory(comparator));

		// mlplan.buildClassifier(split.get(0));

		/* evaluate solution produced by mlplan */
		// Evaluation eval = new Evaluation(split.get(0));
		// eval.evaluateModel(mlplan, split.get(1));
		// System.out.println("Error Rate of the solution produced by ML-Plan: " + (100
		// - eval.pctCorrect()) / 100f);
	}

}
