package de.uni_koblenz.west.cidre.common.query.execution.operators;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

import de.uni_koblenz.west.cidre.common.executor.messagePassing.MessageSenderBuffer;
import de.uni_koblenz.west.cidre.common.mapDB.MapDBCacheOptions;
import de.uni_koblenz.west.cidre.common.mapDB.MapDBStorageOptions;
import de.uni_koblenz.west.cidre.common.query.Mapping;
import de.uni_koblenz.west.cidre.common.query.MappingRecycleCache;
import de.uni_koblenz.west.cidre.common.query.execution.QueryOperatorBase;
import de.uni_koblenz.west.cidre.common.query.execution.QueryOperatorTask;
import de.uni_koblenz.west.cidre.common.query.execution.QueryOperatorType;
import de.uni_koblenz.west.cidre.common.utils.JoinMappingCache;
import de.uni_koblenz.west.cidre.common.utils.NumberConversion;
import de.uni_koblenz.west.cidre.master.statisticsDB.GraphStatistics;

/**
 * Performs the join operation of mappings as a hash join.
 * 
 * @author Daniel Janke &lt;danijankATuni-koblenz.de&gt;
 *
 */
public class TriplePatternJoinOperator extends QueryOperatorBase {

	private long[] resultVars;

	private long[] joinVars;

	private final JoinType joinType;

	private final MapDBStorageOptions storageType;

	private final boolean useTransactions;

	private final boolean writeAsynchronously;

	private final MapDBCacheOptions cacheType;

	private JoinMappingCache leftMappingCache;

	private JoinMappingCache rightMappingCache;

	private JoinIterator iterator;

	public TriplePatternJoinOperator(long id, long coordinatorId,
			int numberOfSlaves, int cacheSize, File cacheDirectory,
			int emittedMappingsPerRound, QueryOperatorTask leftChild,
			QueryOperatorTask rightChild, MapDBStorageOptions storageType,
			boolean useTransactions, boolean writeAsynchronously,
			MapDBCacheOptions cacheType) {
		super(id, coordinatorId, numberOfSlaves, cacheSize, cacheDirectory,
				emittedMappingsPerRound);
		addChildTask(leftChild);
		addChildTask(rightChild);
		computeVars(leftChild.getResultVariables(),
				rightChild.getResultVariables());

		if (joinVars.length > 0) {
			joinType = JoinType.JOIN;
		} else {
			if (leftChild.getResultVariables().length == 0) {
				joinType = JoinType.RIGHT_FORWARD;
			} else if (rightChild.getResultVariables().length == 0) {
				joinType = JoinType.LEFT_FORWARD;
			} else {
				joinType = JoinType.CARTESIAN_PRODUCT;
			}
		}

		this.cacheType = cacheType;
		this.storageType = storageType;
		this.useTransactions = useTransactions;
		this.writeAsynchronously = writeAsynchronously;
	}

	public TriplePatternJoinOperator(short slaveId, int queryId, short taskId,
			long coordinatorId, int numberOfSlaves, int cacheSize,
			File cacheDirectory, int emittedMappingsPerRound,
			QueryOperatorTask leftChild, QueryOperatorTask rightChild,
			MapDBStorageOptions storageType, boolean useTransactions,
			boolean writeAsynchronously, MapDBCacheOptions cacheType) {
		super(slaveId, queryId, taskId, coordinatorId, numberOfSlaves,
				cacheSize, cacheDirectory, emittedMappingsPerRound);
		addChildTask(leftChild);
		addChildTask(rightChild);
		computeVars(leftChild.getResultVariables(),
				rightChild.getResultVariables());

		if (joinVars.length > 0) {
			joinType = JoinType.JOIN;
		} else {
			if (leftChild.getResultVariables().length == 0) {
				joinType = JoinType.RIGHT_FORWARD;
			} else if (rightChild.getResultVariables().length == 0) {
				joinType = JoinType.LEFT_FORWARD;
			} else {
				joinType = JoinType.CARTESIAN_PRODUCT;
			}
		}

		this.cacheType = cacheType;
		this.storageType = storageType;
		this.useTransactions = useTransactions;
		this.writeAsynchronously = writeAsynchronously;
	}

	@Override
	public void setUp(MessageSenderBuffer messageSender,
			MappingRecycleCache recycleCache, Logger logger) {
		super.setUp(messageSender, recycleCache, logger);
		long[] leftVars = ((QueryOperatorTask) getChildTask(0))
				.getResultVariables();
		long[] rightVars = ((QueryOperatorTask) getChildTask(1))
				.getResultVariables();
		leftMappingCache = new JoinMappingCache(storageType, useTransactions,
				writeAsynchronously, cacheType, getCacheDirectory(),
				recycleCache,
				getClass().getSimpleName() + getID() + "_leftChild_", leftVars,
				createComparisonOrder(leftVars), joinVars.length);
		rightMappingCache = new JoinMappingCache(storageType, useTransactions,
				writeAsynchronously, cacheType, getCacheDirectory(),
				recycleCache,
				getClass().getSimpleName() + getID() + "_rightChild_",
				rightVars, createComparisonOrder(rightVars), joinVars.length);
	}

	private int[] createComparisonOrder(long[] vars) {
		int[] ordering = new int[vars.length];
		int nextIndex = 0;
		for (long var : joinVars) {
			ordering[nextIndex] = getIndexOfVar(var, vars);
			nextIndex++;
		}
		for (int i = 0; i < vars.length; i++) {
			if (getIndexOfVar(vars[i], joinVars) == -1) {
				ordering[nextIndex] = i;
				nextIndex++;
			}
		}
		return ordering;
	}

	private int getIndexOfVar(long var, long[] vars) {
		for (int i = 0; i < vars.length; i++) {
			if (vars[i] == var) {
				return i;
			}
		}
		return -1;
	}

	private void computeVars(long[] leftVars, long[] rightVars) {
		long[] leftResultVars = ((QueryOperatorBase) getChildTask(0))
				.getResultVariables();
		long[] rightResultVars = ((QueryOperatorBase) getChildTask(1))
				.getResultVariables();
		if (leftResultVars.length == 0) {
			joinVars = new long[0];
			resultVars = rightResultVars;
		} else if (rightResultVars.length == 0) {
			joinVars = new long[0];
			resultVars = leftResultVars;
		} else {
			long[] allVars = new long[leftVars.length + rightVars.length];
			System.arraycopy(leftVars, 0, allVars, 0, leftVars.length);
			System.arraycopy(rightVars, 0, allVars, leftVars.length,
					rightVars.length);
			Arrays.sort(allVars);
			// count occurrences of different variable types
			int numberOfJoinVars = 0;
			int numberOfResultVars = 0;
			for (int i = 0; i < allVars.length; i++) {
				if (i > 0 && allVars[i - 1] == allVars[i]) {
					// each variable occurs at most two times
					numberOfJoinVars++;
				} else {
					numberOfResultVars++;
				}
			}
			// assign variables to arrays
			resultVars = new long[numberOfResultVars];
			joinVars = new long[numberOfJoinVars];
			int nextJoinVarIndex = 0;
			for (int i = 0; i < allVars.length; i++) {
				if (i > 0 && allVars[i - 1] == allVars[i]) {
					// each variable occurs at most two times
					joinVars[nextJoinVarIndex] = allVars[i];
					nextJoinVarIndex++;
				} else {
					resultVars[i - nextJoinVarIndex] = allVars[i];
				}
			}
		}
	}

	@Override
	public long computeEstimatedLoad(GraphStatistics statistics, int slave,
			boolean setLoads) {
		long load = 0;
		long totalLoad = statistics.getTotalOwnerLoad();
		if (totalLoad != 0) {
			double loadFactor = ((double) statistics.getOwnerLoad(slave))
					/ totalLoad;
			if (loadFactor != 0) {
				long joinSize = computeTotalEstimatedLoad(statistics);
				if (joinSize != 0) {
					load = (long) (joinSize * loadFactor);
				}
			}
		}
		if (setLoads) {
			((QueryOperatorBase) getChildTask(0))
					.computeEstimatedLoad(statistics, slave, setLoads);
			((QueryOperatorBase) getChildTask(1))
					.computeEstimatedLoad(statistics, slave, setLoads);
			setEstimatedWorkLoad(load);
		}
		return load;
	}

	@Override
	public long computeTotalEstimatedLoad(GraphStatistics statistics) {
		QueryOperatorBase leftChild = (QueryOperatorBase) getChildTask(0);
		long leftLoad = leftChild.computeTotalEstimatedLoad(statistics);
		if (leftLoad == 0) {
			return 0;
		}
		QueryOperatorBase rightChild = (QueryOperatorBase) getChildTask(1);
		long rightLoad = rightChild.computeTotalEstimatedLoad(statistics);
		if (rightLoad == 0) {
			return 0;
		}
		return leftLoad * rightLoad;
	}

	@Override
	public long[] getResultVariables() {
		return resultVars;
	}

	@Override
	public long getFirstJoinVar() {
		return joinVars.length == 0 ? -1 : joinVars[0];
	}

	@Override
	public long getCurrentTaskLoad() {
		long leftSize = getSizeOfInputQueue(0) + leftMappingCache.size();
		long rightSize = getSizeOfInputQueue(1) + rightMappingCache.size();
		if (leftSize == 0) {
			return rightSize;
		} else if (rightSize == 0) {
			return leftSize;
		} else {
			return leftSize * rightSize;
		}
	}

	@Override
	protected void executeOperationStep() {
		switch (joinType) {
		case JOIN:
		case CARTESIAN_PRODUCT:
			executeJoinStep();
			break;
		case LEFT_FORWARD:
			executeLeftForwardStep();
			break;
		case RIGHT_FORWARD:
			executeRightForwardStep();
			break;
		}
	}

	// TODO remove

	private int emittedMappings = 0;

	private int receivedMappingsFromLeft = 0;

	private int receivedMappingsFromRight = 0;

	private void executeJoinStep() {
		for (int i = 0; i < getEmittedMappingsPerRound(); i++) {
			if (iterator == null || !iterator.hasNext()) {
				if (iterator != null && !iterator.hasNext()) {
					recycleCache.releaseMapping(iterator.getJoiningMapping());
				}
				if (shouldConsumefromLeftChild()) {
					if (isInputQueueEmpty(0)) {
						if (hasChildFinished(0)) {
							// left child is finished
							rightMappingCache.close();
						}
						if (isInputQueueEmpty(1)) {
							// there are no mappings to consume
							return;
						}
					} else {
						// TODO remove
						receivedMappingsFromLeft++;
						Mapping mapping = consumeMapping(0);
						long[] mappingVars = ((QueryOperatorBase) getChildTask(
								0)).getResultVariables();
						long[] rightVars = ((QueryOperatorBase) getChildTask(1))
								.getResultVariables();
						leftMappingCache.add(mapping);
						iterator = new JoinIterator(recycleCache,
								getResultVariables(), joinVars, mapping,
								mappingVars,
								joinType == JoinType.CARTESIAN_PRODUCT
										? rightMappingCache.iterator()
										: rightMappingCache.getMatchCandidates(
												mapping, mappingVars),
								rightVars);
					}
				} else {
					if (isInputQueueEmpty(1)) {
						if (hasChildFinished(1)) {
							// right child is finished
							leftMappingCache.close();
						}
						if (isInputQueueEmpty(0)) {
							// there are no mappings to consume
							return;
						}
					} else {
						// TODO remove
						receivedMappingsFromRight++;
						Mapping mapping = consumeMapping(1);
						long[] mappingVars = ((QueryOperatorBase) getChildTask(
								1)).getResultVariables();
						long[] leftVars = ((QueryOperatorBase) getChildTask(0))
								.getResultVariables();
						rightMappingCache.add(mapping);
						iterator = new JoinIterator(recycleCache,
								getResultVariables(), joinVars, mapping,
								mappingVars,
								joinType == JoinType.CARTESIAN_PRODUCT
										? leftMappingCache.iterator()
										: leftMappingCache.getMatchCandidates(
												mapping, mappingVars),
								leftVars);
					}
				}
				i--;
			} else {
				Mapping resultMapping = iterator.next();
				emitMapping(resultMapping);
				// TODO remove
				emittedMappings++;
			}
		}
	}

	@Override
	protected void tidyUp() {
		super.tidyUp();
		// TODO remove
		if (logger != null) {
			logger.info(NumberConversion.id2description(getID()) + ":\n"
					+ toString() + "\nreceived left child: "
					+ receivedMappingsFromLeft + " received from right: "
					+ receivedMappingsFromRight + " emitted: "
					+ emittedMappings);
		}
	}

	private boolean shouldConsumefromLeftChild() {
		if (isInputQueueEmpty(1)) {
			return true;
		} else if (isInputQueueEmpty(0)) {
			return false;
		} else {
			return leftMappingCache.size() < rightMappingCache.size();
		}
	}

	private void executeLeftForwardStep() {
		if (hasChildFinished(1)) {
			// the right child has finished
			if (isInputQueueEmpty(1)) {
				// no match for the right expression could be found
				// discard all mappings received from left child
				while (!isInputQueueEmpty(0)) {
					Mapping mapping = consumeMapping(0);
					recycleCache.releaseMapping(mapping);
				}
			} else {
				// the right child has matched
				for (int i = 0; i < getEmittedMappingsPerRound()
						&& !isInputQueueEmpty(0); i++) {
					emitMapping(consumeMapping(0));
				}
				if (hasChildFinished(0) && isInputQueueEmpty(0)) {
					// as a final step, discard the empty mapping from the right
					// child
					Mapping mapping = consumeMapping(1);
					recycleCache.releaseMapping(mapping);
				}
			}
		}
	}

	private void executeRightForwardStep() {
		if (hasChildFinished(0)) {
			// the left child has finished
			if (isInputQueueEmpty(0)) {
				// no match for the left expression could be found
				// discard all mappings received from right child
				while (!isInputQueueEmpty(1)) {
					Mapping mapping = consumeMapping(1);
					recycleCache.releaseMapping(mapping);
				}
			} else {
				// the left child has matched
				for (int i = 0; i < getEmittedMappingsPerRound()
						&& !isInputQueueEmpty(1); i++) {
					Mapping mapping = consumeMapping(1);
					emitMapping(mapping);
				}
				if (hasChildFinished(1) && isInputQueueEmpty(1)) {
					// as a final step, discard the empty mapping from the left
					// child
					Mapping mapping = consumeMapping(0);
					recycleCache.releaseMapping(mapping);
				}
			}
		}
	}

	@Override
	protected void closeInternal() {
		leftMappingCache.close();
		rightMappingCache.close();
	}

	@Override
	public void serialize(DataOutputStream output,
			boolean useBaseImplementation, int slaveId) throws IOException {
		if (getParentTask() == null) {
			output.writeBoolean(useBaseImplementation);
			output.writeLong(getCoordinatorID());
		}
		output.writeInt(QueryOperatorType.TRIPLE_PATTERN_JOIN.ordinal());
		((QueryOperatorTask) getChildTask(0)).serialize(output,
				useBaseImplementation, slaveId);
		((QueryOperatorTask) getChildTask(1)).serialize(output,
				useBaseImplementation, slaveId);
		output.writeLong(getIdOnSlave(slaveId));
		output.writeInt(getEmittedMappingsPerRound());
		output.writeLong(getEstimatedTaskLoad());
	}

	@Override
	public void toString(StringBuilder sb, int indention) {
		indent(sb, indention);
		sb.append(getClass().getSimpleName());
		sb.append(" ").append(joinType.name());
		sb.append(" joinVars: [");
		String delim = "";
		for (long var : joinVars) {
			sb.append(delim).append(var);
			delim = ",";
		}
		sb.append("]");
		sb.append(" resultVars: [");
		delim = "";
		for (long var : resultVars) {
			sb.append(delim).append(var);
			delim = ",";
		}
		sb.append("]");
		sb.append(" estimatedWorkLoad: ").append(getEstimatedTaskLoad());
		sb.append("\n");
		((QueryOperatorBase) getChildTask(0)).toString(sb, indention + 1);
		((QueryOperatorBase) getChildTask(1)).toString(sb, indention + 1);
	}

	private static enum JoinType {
		JOIN, CARTESIAN_PRODUCT, LEFT_FORWARD, RIGHT_FORWARD;
	}
}
