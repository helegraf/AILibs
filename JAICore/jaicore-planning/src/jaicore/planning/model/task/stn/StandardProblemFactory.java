package jaicore.planning.model.task.stn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jaicore.logic.fol.structure.CNFFormula;
import jaicore.logic.fol.structure.Literal;
import jaicore.logic.fol.structure.LiteralParam;
import jaicore.logic.fol.structure.Monom;
import jaicore.logic.fol.structure.VariableParam;
import jaicore.planning.model.ceoc.CEOCAction;
import jaicore.planning.model.ceoc.CEOCOperation;
import jaicore.planning.model.conditional.CEOperation;
import jaicore.planning.model.core.Action;
import jaicore.planning.model.core.Operation;
import jaicore.planning.model.strips.StripsPlanningDomain;
import jaicore.planning.model.task.ceocstn.CEOCSTNPlanningProblem;
import jaicore.planning.model.task.ceocstn.OCMethod;

public class StandardProblemFactory {

	public static STNPlanningProblem<Operation, Method, Action> getDockworkerProblem() {

		/* retrieve STRIPS operations of the planning problem */
		StripsPlanningDomain dwrStripsDomain = (StripsPlanningDomain) jaicore.planning.model.strips.StandardProblemFactory.getDockworkerProblem().getDomain();

		/* define non-primitive STN task literals for the domain */
		Literal taskMoveTopmostContainer = new Literal("move-topmost-container(p1,p2)");
		Literal taskMoveStack = new Literal("move-stack(p,q)");
//		Literal taskMoveAllStacks = new Literal("move-all-stacks()");

		/* define STN methods for the domain */
		List<Method> methods = new ArrayList<>();
		Monom p1 = new Monom("top(c,p1) & on(c,x1) & attached(p1,l1) & belong(k,l1) & attached(p2,l2) & top(x2,p2)");
		methods.add(new Method("take-and-put", Arrays.asList(new VariableParam[] { new VariableParam("k"), new VariableParam("c"), new VariableParam("p1"), new VariableParam("p2"), new VariableParam("l1"), new VariableParam("l2"), new VariableParam("x1"), new VariableParam("x2") }), taskMoveTopmostContainer, p1,
				new TaskNetwork("take(k,l1,c,x1,p1) -> put(k,l2,c,x2,p2)"), false));
		methods.add(new Method("recursive-move", Arrays.asList(new VariableParam[] { new VariableParam("c"), new VariableParam("p"), new VariableParam("q"), new VariableParam("x") }), taskMoveStack, new Monom("top(c,p) & on(c,x)"),
				new TaskNetwork("move-topmost-container(p,q) -> move-stack(p,q)"), false));
		methods.add(new Method("do-nothing", Arrays.asList(new VariableParam[] { new VariableParam("p") }), taskMoveStack, new Monom("top('pallet',p)"),
				new TaskNetwork(), false));

		/* create STN domain */
		STNPlanningDomain<Operation, Method> domain = new STNPlanningDomain<>(dwrStripsDomain.getOperations(), methods);

		/* define init situation, w.r.t. example in Ghallab, Nau, Traverso; p. 230 */
		Monom init = new Monom(""
				+ "attached('p1a','l1') & attached('p1b','l1') & attached('p1c','l1') & belong('crane1','l1') & empty('crane1') & in('c11','p1a') & in('c12','p1a') & top('c11','p1a') & top('pallet','p1b') & top('pallet','p1c') & on('c11','c12') & on('c12','pallet') &"
				+ "attached('p2a','l2') & attached('p2b','l2') & attached('p2c','l2') & belong('crane2','l2') & empty('crane2') & in('c21','p2a') & in('c22','p2a') & in('c23','p2a')  & top('c21','p2a') & top('pallet','p2b') & top('pallet','p2c') & on('c21','c22') & on('c22','c23') & on('c23','pallet') &"
				+ "attached('p3a','l3') & attached('p3b','l3') & attached('p3c','l3') & belong('crane3','l3') & empty('crane3') & in('c31','p3a') & in('c32','p3a') & in('c33','p3a') & in('c34','p3a') & top('c31','p3a') & top('pallet','p3b') & top('pallet','p3c') & on('c31','c32') & on('c32','c33') & on('c33', 'c34') & on('c34','pallet')"
				);
		TaskNetwork network = new TaskNetwork("move-stack('p1a', 'p1c') -> move-stack('p1c','p1b') -> move-stack('p2a','p2c') -> move-stack('p2c','p2b') -> move-stack('p3a','p3c') -> move-stack('p3c','p3b')");
		return new STNPlanningProblem<>(domain, null, init, network);
	}

	public static STNPlanningProblem<CEOCOperation, OCMethod, CEOCAction> getNestedDichotomyCreationProblem(String rootClusterName, List<String> classes) {
		CEOCSTNPlanningProblem<CEOCOperation, OCMethod, CEOCAction> problem = jaicore.planning.model.task.ceocstn.StandardProblemFactory.getNestedDichotomyCreationProblem(rootClusterName, classes, true, 1, 1);
		
		List<CEOperation> operations = new ArrayList<>();
		for (CEOCOperation op : problem.getDomain().getOperations()) {
			Monom precondition = op.getPrecondition();
			Map<CNFFormula,Monom> addLists = op.getAddLists();
			Map<CNFFormula,Monom> deleteLists = op.getDeleteLists();
			List<VariableParam> parameters = new ArrayList<>(op.getParams());
			if (!op.getOutputs().isEmpty()) {
				for (VariableParam out : op.getOutputs()) {
					precondition.addAll(new Monom("cluster(" + out.getName() + ") & !inuse(" + out.getName() + ")"));
					addLists.get(new CNFFormula()).add(new Literal("inuse(" + out.getName() + ")"));
				}
				parameters.add(new VariableParam("next"));
				addLists.get(new CNFFormula()).add(new Literal("active(next)"));
				deleteLists.put(new CNFFormula(), new Monom("active(" + op.getOutputs().get(0).getName() + ")"));
				precondition.addAll(new Monom("active(" + op.getOutputs().get(0).getName() + ") & succ(" + op.getOutputs().get(0).getName() + ", " + op.getOutputs().get(1).getName() + ") & succ(" + op.getOutputs().get(1).getName() + ", next)"));
			}
			operations.add(new CEOperation(op.getName(), parameters, precondition, addLists, deleteLists));
		}
		List<Method> methods = new ArrayList<>();
		for (OCMethod method : problem.getDomain().getMethods()) {
			List<VariableParam> parameters = new ArrayList<>(method.getParameters());
			Monom precondition = method.getPrecondition();
			TaskNetwork nw = method.getNetwork();
			if (!method.getOutputs().isEmpty()) {
				for (VariableParam out : method.getOutputs()) {
					precondition.addAll(new Monom("cluster(" + out.getName() + ") & !inuse(" + out.getName() + ")"));
				}
				parameters.add(new VariableParam("next"));
				List<Literal> tasks = new ArrayList<>(nw.getItems()); 
				for (Literal task : tasks) {
					if (task.getPropertyName().endsWith("initChildClusters")) {
						List<LiteralParam> taskParams = new ArrayList<>(task.getParameters());
						taskParams.add(new VariableParam("next"));
						Collection<Literal> succ = nw.getSuccessors(task);
						Collection<Literal> pred = nw.getPredecessors(task);
						nw.removeItem(task);
						Literal newTask = new Literal(task.getPropertyName(), taskParams);
						nw.addItem(newTask);
						for (Literal l : succ)
							nw.addEdge(newTask, l);
						for (Literal l : pred)
							nw.addEdge(l, newTask);
					}
				}
				precondition.addAll(new Monom("active(" + method.getOutputs().get(0).getName() + ") & succ(" + method.getOutputs().get(0).getName() + ", " + method.getOutputs().get(1).getName() + ") & succ(" + method.getOutputs().get(1).getName() + ", next)"));
			}
			System.out.println(precondition);
			methods.add(new Method(method.getName(), parameters, method.getTask(), precondition, nw, method.isLonely()));
		}
		
		STNPlanningDomain domain = new STNPlanningDomain(operations, methods);
		Monom init = problem.getInit();
		init.add(new Literal("inuse('root')"));
		for (int i = 0; i < 2 * classes.size(); i++) {
			init.addAll(i == 0 ? new Monom("cluster('c0') & active('c1')") : new Monom("cluster('c" + i + "') & succ('c" + (i-1) + "','c" + i + "')"));
		}
		System.out.println(init);
		STNPlanningProblem newProblem = new STNPlanningProblem(domain, null, init, problem.getNetwork());
		return newProblem;
	}
}
