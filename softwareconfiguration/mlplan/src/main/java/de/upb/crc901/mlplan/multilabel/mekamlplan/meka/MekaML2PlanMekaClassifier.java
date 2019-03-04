package de.upb.crc901.mlplan.multilabel.mekamlplan.meka;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.aeonbits.owner.ConfigFactory;

import de.upb.crc901.mlplan.multilabel.ML2PlanClassifierConfig;
import de.upb.crc901.mlplan.multilabel.mekamlplan.ML2PlanMekaBuilder;
import de.upb.crc901.mlplan.multilabel.mekamlplan.ML2PlanMekaClassifier;
import jaicore.planning.hierarchical.algorithms.forwarddecomposition.graphgenerators.tfd.TFDNode;
import jaicore.search.algorithms.standard.bestfirst.nodeevaluation.INodeEvaluator;
import weka.core.Instances;

/**
 * Wrapper for the abstract {@link ML2PlanMekaClassifier}.
 * 
 * @author Helena Graf
 *
 */
public class MekaML2PlanMekaClassifier extends ML2PlanMekaClassifier {

	/**
	 * Construct a new classifier configured according to the given builder.
	 * 
	 * @param builder
	 *            the builder used to configure the new classifier
	 * @throws IOException
	 *             if a configuration in the builder cannot be loaded
	 */
	public MekaML2PlanMekaClassifier(ML2PlanMekaBuilder builder, Instances data) throws IOException {
		super(builder.getSearchSpaceConfigFile(), new MEKAPipelineFactory(), builder.getPerformanceMeasure(),
				builder.getAlhorithmConfigFile() != null ? loadOwnerConfig(builder.getAlhorithmConfigFile())
						: ConfigFactory.create(ML2PlanClassifierConfig.class), data);
		this.setPreferredNodeEvaluator(node -> 0.0);
	}

	/**
	 * Construct a new classifier using the standard configuration (the
	 * configuration created by creating a new {@link ML2PlanMekaBuilder} with no
	 * arguments).
	 * 
	 * @throws IOException
	 *             if a configuration in the builder cannot be loaded
	 */
	public MekaML2PlanMekaClassifier(Instances data) throws IOException {
		this(new ML2PlanMekaBuilder(), data);
	}

	private static ML2PlanClassifierConfig loadOwnerConfig(File configFile) throws IOException {
		Properties props = new Properties();
		if (configFile.exists()) {
			FileInputStream fis = new FileInputStream(configFile);
			props.load(fis);
		} else {
			System.out.println(
					"Config file " + configFile.getAbsolutePath() + " not found, working with default parameters.");
		}
		return ConfigFactory.create(ML2PlanClassifierConfig.class, props);
	}

	@Override
	protected INodeEvaluator<TFDNode, Double> getSemanticNodeEvaluator(Instances data) {
		return node->null;
	}
}
