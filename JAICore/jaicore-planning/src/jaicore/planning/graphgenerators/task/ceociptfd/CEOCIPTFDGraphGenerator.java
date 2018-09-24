package jaicore.planning.graphgenerators.task.ceociptfd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jaicore.logic.fol.structure.Literal;
import jaicore.logic.fol.structure.Monom;
import jaicore.planning.graphgenerators.task.TaskPlannerUtil;
import jaicore.planning.graphgenerators.task.ceoctfd.CEOCTFDGraphGenerator;
import jaicore.planning.graphgenerators.task.tfd.TFDNode;
import jaicore.planning.model.ceoc.CEOCAction;
import jaicore.planning.model.ceoc.CEOCOperation;
import jaicore.planning.model.core.Action;
import jaicore.planning.model.core.PlannerUtil;
import jaicore.planning.model.task.ceocipstn.CEOCIPSTNPlanningProblem;

/**
 * Graph Generator for HTN planning where (i) operations have conditional effects, (ii) operations may create new objects, and (iii) method preconditions may contain evaluable predicates.
 * 
 * @author fmohr
 *
 */
@SuppressWarnings("serial")
public class CEOCIPTFDGraphGenerator extends CEOCTFDGraphGenerator {

	public CEOCIPTFDGraphGenerator(CEOCIPSTNPlanningProblem problem) {
		super(problem);
		
		/* now overwrite util to get access to the evaluable predicates */
		this.util = new TaskPlannerUtil(problem.getEvaluablePlanningPredicates());
	}

//	protected Collection<TFDNode> getSuccessorsResultingFromResolvingComplexTask(Monom state, Literal taskToBeResolved, List<Literal> remainingOtherTasks) {
//		Collection<TFDNode> successors = new ArrayList<>();
//		String nextTaskName = taskToBeResolved.getPropertyName();
//
//		/* if there is an oracle for the task, use it */
//		Map<String, OracleTaskResolver> oracleResolvers = ((CEOCIPSTNPlanningProblem) problem).getOracleResolvers();
//		if (oracleResolvers != null && oracleResolvers.containsKey(nextTaskName)) {
//
//			/* for each sub-solution produced by the oracle, create a successor node */
//			try {
//				Collection<List<Action>> subsolutions = oracleResolvers.get(nextTaskName).getSubSolutions(state, taskToBeResolved);
//				for (List<Action> subsolution : subsolutions) {
//					if (subsolution.size() > 1)
//						throw new UnsupportedOperationException("Currently only subplans of length 1 possible!");
//					Action applicableAction = subsolution.get(0);
//					Monom updatedState = new Monom(state, false);
//					PlannerUtil.updateState(updatedState, applicableAction);
//					List<Literal> remainingTasks = new ArrayList<>(remainingOtherTasks);
//					remainingTasks.remove(0);
//					successors
//							.add(new TFDNode(updatedState, remainingTasks, null, new CEOCAction((CEOCOperation) applicableAction.getOperation(), applicableAction.getGrounding())));
//				}
//
//				return successors;
//			} catch (Exception e) {
//				e.printStackTrace();
//				return new ArrayList<>();
//			}
//		}
//
//		/* otherwise, ordinary computation */
//		else
//			return super.getSuccessorsResultingFromResolvingComplexTask(state, taskToBeResolved, remainingOtherTasks);
//	}

	@Override
	public String toString() {
		return "CEOCIPTFDGraphGenerator [problem=" + problem + ", primitiveTasks=" + primitiveTasks + "]";
	}

}
