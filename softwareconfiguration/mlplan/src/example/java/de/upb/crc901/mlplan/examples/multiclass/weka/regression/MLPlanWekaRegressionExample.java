package de.upb.crc901.mlplan.examples.multiclass.weka.regression;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.upb.crc901.mlplan.core.AbstractMLPlanBuilder;
import de.upb.crc901.mlplan.core.MLPlan;
import de.upb.crc901.mlplan.core.MLPlanWekaBuilder;
import de.upb.crc901.mlplan.gui.outofsampleplots.OutOfSampleErrorPlotPlugin;
import hasco.gui.statsplugin.HASCOModelStatisticsPlugin;
import jaicore.basic.TimeOut;
import jaicore.concurrent.GlobalTimer;
import jaicore.graphvisualizer.plugin.graphview.GraphViewPlugin;
import jaicore.graphvisualizer.plugin.nodeinfo.NodeInfoGUIPlugin;
import jaicore.graphvisualizer.plugin.solutionperformanceplotter.SolutionPerformanceTimelinePlugin;
import jaicore.graphvisualizer.window.AlgorithmVisualizationWindow;
import jaicore.ml.WekaUtil;
import jaicore.ml.core.evaluation.measure.singlelabel.RootMeanSquaredErrorLoss;
import jaicore.ml.openml.OpenMLHelper;
import jaicore.planning.hierarchical.algorithms.forwarddecomposition.graphgenerators.tfd.TFDNodeInfoGenerator;
import jaicore.search.gui.plugins.rollouthistograms.SearchRolloutHistogramPlugin;
import jaicore.search.model.travesaltree.JaicoreNodeInfoGenerator;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;
import weka.core.converters.ArffLoader.ArffReader;

public class MLPlanWekaRegressionExample {
	
	private static final Logger L = LoggerFactory.getLogger(MLPlanWekaRegressionExample.class);
	
	private MLPlanWekaRegressionExample () {
		// Intentionally left blank
	}

	public static void main (String [] args) throws Exception {
		
		//Instances data = OpenMLHelper.getInstancesWithIndexSet(8, "abc");
		BufferedReader reader = new BufferedReader(new FileReader("resources/complete_noid.arff"));
		ArffReader arff = new ArffReader(reader);
		Instances data = arff.getData();
		data.setClassIndex(data.numAttributes()-1);
		
		long seed = 0;
		L.info("Split instances with seed {}", seed);
		List<Instances> stratifiedSplit = WekaUtil.getStratifiedSplit(data, seed, .7);

		/* initialize ML-Plan with the same config file that has been used to specify the experiments */	
		MLPlanWekaBuilder builder = AbstractMLPlanBuilder.forWeka();
		builder.withTinyTestForRegressionConfiguration();
		builder.withPerformanceMeasure(new RootMeanSquaredErrorLoss());
		builder.withTimeOut(new TimeOut(60, TimeUnit.SECONDS));
		builder.withNodeEvaluationTimeOut(new TimeOut(15, TimeUnit.SECONDS));
		builder.withCandidateEvaluationTimeOut(new TimeOut(15, TimeUnit.SECONDS));
		builder.withNumCpus(4);

		MLPlan mlplan = new MLPlan(builder, stratifiedSplit.get(0));
		mlplan.setLoggerName("mlplan");
		mlplan.setRandomSeed(0);
		
		new JFXPanel();
		AlgorithmVisualizationWindow window = new AlgorithmVisualizationWindow(mlplan, new GraphViewPlugin(), new NodeInfoGUIPlugin<>(new JaicoreNodeInfoGenerator<>(new TFDNodeInfoGenerator())), new SearchRolloutHistogramPlugin<>(),
				new SolutionPerformanceTimelinePlugin(), new HASCOModelStatisticsPlugin(), new OutOfSampleErrorPlotPlugin(stratifiedSplit.get(0), stratifiedSplit.get(1)));
		Platform.runLater(window);
		
		L.info("Build mlplan classifier");
		Classifier optimizedClassifier = mlplan.call();

		L.info("Open timeout tasks: {}", GlobalTimer.getInstance().getActiveTasks());

		Evaluation eval = new Evaluation(data);
		L.info("Assess test performance...");
		eval.evaluateModel(optimizedClassifier, stratifiedSplit.get(1));

		L.info("Test error was {}. Internally estimated error for this model was {}", eval.rootMeanSquaredError(), mlplan.getInternalValidationErrorOfSelectedClassifier());

		L.info("Experiment done.");
	}
}
